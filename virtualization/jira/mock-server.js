#!/usr/bin/env node

const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const fs = require('fs');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 8080;

// Middleware
app.use(cors());
app.use(bodyParser.json());
app.use(express.static('public'));

// In-memory storage
let issues = {};
let issueCounter = 100;
let projectCounter = 1;

// Load initial data from resources
function loadResources() {
  const resourcesPath = path.join(__dirname, 'resources', 'search.json');
  const searchData = JSON.parse(fs.readFileSync(resourcesPath, 'utf8'));
  searchData.issues.forEach(issue => {
    issues[issue.key] = issue;
  });
  console.log(`Loaded ${Object.keys(issues).length} issues from resources`);
}

// Helper: Generate new issue key
function generateIssueKey(projectKey = 'TEST') {
  return `${projectKey}-${issueCounter++}`;
}

// Helper: Get current timestamp
function now() {
  return new Date().toISOString();
}

// Status transitions map
const TRANSITIONS = {
  'To Do': [
    { id: '21', name: 'In Progress', to: { id: '21', name: 'In Progress' } },
    { id: '31', name: 'Done', to: { id: '31', name: 'Done' } }
  ],
  'In Progress': [
    { id: '11', name: 'To Do', to: { id: '11', name: 'To Do' } },
    { id: '41', name: 'Code Review', to: { id: '41', name: 'Code Review' } },
    { id: '31', name: 'Done', to: { id: '31', name: 'Done' } }
  ],
  'Code Review': [
    { id: '21', name: 'In Progress', to: { id: '21', name: 'In Progress' } },
    { id: '31', name: 'Done', to: { id: '31', name: 'Done' } }
  ],
  'Done': [
    { id: '21', name: 'In Progress', to: { id: '21', name: 'In Progress' } }
  ],
  'Blocked': [
    { id: '21', name: 'In Progress', to: { id: '21', name: 'In Progress' } },
    { id: '11', name: 'To Do', to: { id: '11', name: 'To Do' } }
  ]
};

// ===== JIRA REST API Endpoints =====

// GET /rest/api/3/issue/{issueKey}
app.get('/rest/api/3/issue/:issueKey', (req, res) => {
  const { issueKey } = req.params;
  console.log(`[GET] /rest/api/3/issue/${issueKey}`);

  const issue = issues[issueKey];
  if (!issue) {
    return res.status(404).json({
      errorMessages: [`Issue does not exist: ${issueKey}`],
      errors: {}
    });
  }

  res.json(issue);
});

// POST /rest/api/3/issue - Create issue
app.post('/rest/api/3/issue', (req, res) => {
  console.log('[POST] /rest/api/3/issue', JSON.stringify(req.body, null, 2));

  const { fields } = req.body;
  const key = generateIssueKey(fields.project?.key || 'TEST');

  const newIssue = {
    id: String(issueCounter),
    key: key,
    self: `http://localhost:${PORT}/rest/api/3/issue/${key}`,
    fields: {
      issuetype: fields.issuetype || { name: 'Task' },
      project: fields.project || { key: 'TEST', name: 'Test Project' },
      summary: fields.summary || 'New Issue',
      description: fields.description || null,
      status: { name: 'To Do', id: '11' },
      priority: fields.priority || { name: 'Medium' },
      reporter: { displayName: 'Mock User' },
      assignee: fields.assignee || null,
      created: now(),
      updated: now(),
      labels: fields.labels || [],
      issuelinks: fields.issuelinks || [],
      parent: fields.parent || null,
      subtasks: []
    }
  };

  // Handle parent link for subtasks
  if (fields.parent) {
    const parentKey = fields.parent.key;
    if (issues[parentKey]) {
      if (!issues[parentKey].fields.subtasks) {
        issues[parentKey].fields.subtasks = [];
      }
      issues[parentKey].fields.subtasks.push({
        key: key,
        fields: {
          summary: newIssue.fields.summary,
          status: newIssue.fields.status,
          issuetype: newIssue.fields.issuetype
        }
      });
    }
  }

  issues[key] = newIssue;

  res.status(201).json({
    id: newIssue.id,
    key: key,
    self: newIssue.self
  });
});

// GET /rest/api/3/issue/{issueKey}/transitions
app.get('/rest/api/3/issue/:issueKey/transitions', (req, res) => {
  const { issueKey } = req.params;
  console.log(`[GET] /rest/api/3/issue/${issueKey}/transitions`);

  const issue = issues[issueKey];
  if (!issue) {
    return res.status(404).json({
      errorMessages: [`Issue does not exist: ${issueKey}`]
    });
  }

  const currentStatus = issue.fields.status.name;
  const transitions = TRANSITIONS[currentStatus] || [];

  res.json({
    expand: 'transitions',
    transitions: transitions.map(t => ({
      id: t.id,
      name: t.name,
      to: t.to,
      hasScreen: false,
      isGlobal: false,
      isInitial: false,
      isAvailable: true,
      isConditional: false,
      isLoading: false
    }))
  });
});

// POST /rest/api/3/issue/{issueKey}/transitions
app.post('/rest/api/3/issue/:issueKey/transitions', (req, res) => {
  const { issueKey } = req.params;
  const { transition } = req.body;
  console.log(`[POST] /rest/api/3/issue/${issueKey}/transitions`, JSON.stringify(req.body, null, 2));

  const issue = issues[issueKey];
  if (!issue) {
    return res.status(404).json({
      errorMessages: [`Issue does not exist: ${issueKey}`]
    });
  }

  // Find the transition
  const currentStatus = issue.fields.status.name;
  const availableTransitions = TRANSITIONS[currentStatus] || [];
  const targetTransition = availableTransitions.find(t =>
    t.id === transition.id || t.name === transition.id
  );

  if (!targetTransition) {
    return res.status(400).json({
      errorMessages: [`Transition ${transition.id} is not valid for current status ${currentStatus}`]
    });
  }

  // Update status
  issue.fields.status = {
    id: targetTransition.to.id,
    name: targetTransition.to.name
  };
  issue.fields.updated = now();

  res.status(204).send();
});

// GET /rest/api/3/search
app.get('/rest/api/3/search', (req, res) => {
  const { jql, maxResults = 50, startAt = 0 } = req.query;
  console.log(`[GET] /rest/api/3/search?jql=${jql}`);

  let filteredIssues = Object.values(issues);

  // Simple JQL parsing (very basic)
  if (jql) {
    const lowerJql = jql.toLowerCase();

    // project = X
    const projectMatch = jql.match(/project\s*=\s*(\w+)/i);
    if (projectMatch) {
      const projectKey = projectMatch[1];
      filteredIssues = filteredIssues.filter(i =>
        i.fields.project?.key === projectKey
      );
    }

    // status = X
    const statusMatch = jql.match(/status\s*=\s*"([^"]+)"/i);
    if (statusMatch) {
      const statusName = statusMatch[1];
      filteredIssues = filteredIssues.filter(i =>
        i.fields.status.name === statusName
      );
    }

    // key = X
    const keyMatch = jql.match(/key\s*=\s*(\S+)/i);
    if (keyMatch) {
      const key = keyMatch[1];
      filteredIssues = filteredIssues.filter(i => i.key === key);
    }
  }

  const total = filteredIssues.length;
  const paginatedIssues = filteredIssues.slice(startAt, startAt + maxResults);

  res.json({
    expand: 'schema,names',
    startAt: parseInt(startAt),
    maxResults: parseInt(maxResults),
    total: total,
    issues: paginatedIssues
  });
});

// GET /rest/api/3/myself
app.get('/rest/api/3/myself', (req, res) => {
  console.log('[GET] /rest/api/3/myself');
  res.json({
    accountId: 'mock-user-123',
    displayName: 'Mock User',
    emailAddress: 'mock@example.com',
    active: true
  });
});

// ===== Admin UI Endpoints =====

// GET /api/admin/issues - Get all issues for admin UI
app.get('/api/admin/issues', (req, res) => {
  res.json(Object.values(issues));
});

// PUT /api/admin/issues/:key - Update issue
app.put('/api/admin/issues/:key', (req, res) => {
  const { key } = req.params;
  const updatedIssue = req.body;

  if (!issues[key]) {
    return res.status(404).json({ error: 'Issue not found' });
  }

  updatedIssue.fields.updated = now();
  issues[key] = updatedIssue;

  res.json(updatedIssue);
});

// DELETE /api/admin/issues/:key
app.delete('/api/admin/issues/:key', (req, res) => {
  const { key } = req.params;

  if (!issues[key]) {
    return res.status(404).json({ error: 'Issue not found' });
  }

  delete issues[key];
  res.status(204).send();
});

// POST /api/admin/reset - Reset to initial state
app.post('/api/admin/reset', (req, res) => {
  issues = {};
  issueCounter = 100;
  loadResources();
  res.json({ message: 'Reset to initial state', issueCount: Object.keys(issues).length });
});

// ===== Server Startup =====

loadResources();

app.listen(PORT, () => {
  console.log(`\n🚀 Jira Mock Server running on http://localhost:${PORT}`);
  console.log(`📊 Admin UI available at http://localhost:${PORT}`);
  console.log(`📝 Loaded ${Object.keys(issues).length} issues`);
  console.log(`\nAPI Base URL: http://localhost:${PORT}/rest/api/3`);
  console.log('\nExample Kapture config:');
  console.log(`{
  "externalBaseUrl": "http://localhost:${PORT}",
  "external": {
    "type": "rest",
    "baseUrl": "http://localhost:${PORT}",
    "auth": {
      "type": "bearer",
      "token": "mock-token"
    }
  }
}\n`);
});
