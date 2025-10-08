const fs = require('fs');
const path = require('path');
const express = require('express');

const PORT = Number(process.env.PORT || process.env.JIRA_MOCK_PORT || 8080);
const BASE_URL = process.env.JIRA_MOCK_BASE_URL || `http://localhost:${PORT}`;
const DATA_PATH = process.env.JIRA_MOCK_DB || path.join(__dirname, '../data/state.json');
const RESOURCES_DIR = path.join(__dirname, '../resources');

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function loadResource(name) {
  return readJson(path.join(RESOURCES_DIR, name));
}

function ensureDir(filePath) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
}

function normalizeIssue(issue, overrides = {}) {
  return {
    id: issue.id || String(Date.now()),
    key: issue.key,
    self: `${BASE_URL}/rest/api/3/issue/${issue.key}`,
    fields: issue.fields || {},
    ...overrides,
  };
}

function unpackIssues() {
  const summaryFile = loadResource('search.json');
  const issues = {};
  const counters = {};

  const issueFiles = fs
    .readdirSync(RESOURCES_DIR)
    .filter((file) => /^issue-/i.test(file))
    .map((file) => path.join(RESOURCES_DIR, file));

  issueFiles.forEach((filePath) => {
    const issue = readJson(filePath);
    if (!issue || !issue.key) return;
    issues[issue.key.toUpperCase()] = normalizeIssue(issue);
  });

  summaryFile.issues.forEach((issue) => {
    const key = issue.key;
    issues[key.toUpperCase()] = normalizeIssue({ key, fields: issue.fields });
  });

  Object.keys(issues).forEach((key) => {
    const match = key.match(/^[A-Z]+-(\d+)$/);
    if (!match) return;
    const projectKey = key.split('-')[0];
    const value = Number(match[1]);
    counters[projectKey] = Math.max(counters[projectKey] || 0, value);
  });

  return { issues, summary: summaryFile, counters };
}

function loadInitialState() {
  const { issues, summary, counters } = unpackIssues();
  const projects = loadResource('projects.json');
  const boards = loadResource('boards.json');
  const releases = loadResource('releases.json');
  const users = loadResource('users.json');

  return {
    serverInfo: loadResource('serverinfo.json'),
    myself: loadResource('myself.json'),
    fields: loadResource('fields.json'),
    issueLinkTypes: loadResource('issue-link-types.json'),
    createMeta: loadResource('createmeta.json'),
    createMetaV9: loadResource('createmetav9.json'),
    transitions: loadResource('transitions.json'),
    boards,
    releases,
    users,
    projects,
    issues,
    issueSummary: summary,
    counters,
    sprints: {
      default: loadResource('sprints.json'),
      'board-0': loadResource('sprints-0.json'),
      'board-2': loadResource('sprints-2.json'),
      'board-3': loadResource('sprints-3.json'),
    },
    sprintDetails: loadResource('sprint-get.json'),
    epicIssues: loadResource('epic.json'),
  };
}

function loadState() {
  try {
    const loaded = readJson(DATA_PATH);
    if (!loaded || typeof loaded !== 'object' || !loaded.issues) {
      throw new Error('invalid state file');
    }
    return loaded;
  } catch (error) {
    const initial = loadInitialState();
    ensureDir(DATA_PATH);
    fs.writeFileSync(DATA_PATH, JSON.stringify(initial, null, 2));
    return initial;
  }
}

function persist(state) {
  ensureDir(DATA_PATH);
  fs.writeFileSync(DATA_PATH, JSON.stringify(state, null, 2));
}

const state = loadState();

function nextIssueKey(projectKey) {
  const current = state.counters[projectKey] || 0;
  const next = current + 1;
  state.counters[projectKey] = next;
  return `${projectKey}-${next}`;
}

const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '1mb' }));
app.use(express.urlencoded({ extended: true }));

affirm();

function affirm() {
  app.get('/', (req, res) => {
    res.json({ message: 'Kapture Jira Mock', baseUrl: BASE_URL });
  });

  app.get('/rest/api/3/serverInfo', (req, res) => {
    res.json(state.serverInfo);
  });

  app.get('/rest/api/3/myself', (req, res) => {
    res.json(state.myself);
  });

  app.get('/rest/api/3/field', (req, res) => {
    res.json(state.fields);
  });

  app.get('/rest/api/3/issue/createmeta', (req, res) => {
    res.json(state.createMeta);
  });

  app.get('/rest/api/3/issue/createmeta/latest', (req, res) => {
    res.json(state.createMetaV9);
  });

  app.get('/rest/api/3/project/search', (req, res) => {
    res.json({
      self: `${BASE_URL}/rest/api/3/project/search`,
      maxResults: state.projects.length,
      startAt: 0,
      total: state.projects.length,
      isLast: true,
      values: state.projects,
    });
  });

  app.get('/rest/api/3/project/:projectIdOrKey', (req, res) => {
    const { projectIdOrKey } = req.params;
    const project = state.projects.find((p) => p.id === projectIdOrKey || p.key.toUpperCase() === projectIdOrKey.toUpperCase());
    if (!project) return res.status(404).json({ errorMessages: ['Project not found'], errors: {} });
    res.json(project);
  });

  app.get('/rest/api/3/issueLinkType', (req, res) => {
    res.json(state.issueLinkTypes);
  });

  app.get(['/rest/api/3/user/search', '/rest/api/3/users/search'], (req, res) => {
    res.json(state.users);
  });

  app.get('/rest/api/3/project/:projectIdOrKey/version', (req, res) => {
    res.json(state.releases);
  });

  app.get('/rest/agile/1.0/board', (req, res) => {
    res.json(state.boards);
  });

  app.get('/rest/agile/1.0/board/:boardId/sprint', (req, res) => {
    const { boardId } = req.params;
    const key = `board-${boardId}`;
    res.json(state.sprints[key] || state.sprints.default);
  });

  app.get('/rest/agile/1.0/sprint/:sprintId', (req, res) => {
    res.json(state.sprintDetails);
  });

  app.get('/rest/api/3/issue/:issueIdOrKey', (req, res) => {
    const issue = state.issues[req.params.issueIdOrKey.toUpperCase()];
    if (!issue) return res.status(404).json({ errorMessages: ['Issue not found'], errors: {} });
    res.json(issue);
  });

  app.get('/rest/api/3/issue/:issueIdOrKey/transitions', (req, res) => {
    const issue = state.issues[req.params.issueIdOrKey.toUpperCase()];
    if (!issue) return res.status(404).json({ errorMessages: ['Issue not found'], errors: {} });
    res.json(state.transitions);
  });

  app.post('/rest/api/3/issue/:issueIdOrKey/transitions', (req, res) => {
    const issueKey = req.params.issueIdOrKey.toUpperCase();
    const issue = state.issues[issueKey];
    if (!issue) return res.status(404).json({ errorMessages: ['Issue not found'], errors: {} });
    const transition = req.body?.transition;
    if (!transition) return res.status(400).json({ errorMessages: ['transition required'], errors: {} });

    let statusName = transition.name;
    if (!statusName && transition.id) {
      const found = state.transitions.transitions.find((t) => t.id === transition.id);
      statusName = found?.name;
    }
    if (!statusName) return res.status(400).json({ errorMessages: ['Unknown transition'], errors: {} });

    if (!issue.fields.status) issue.fields.status = {};
    issue.fields.status.name = statusName;

    const summaryIssue = state.issueSummary.issues.find((i) => i.key.toUpperCase() === issueKey);
    if (summaryIssue) summaryIssue.fields.status.name = statusName;

    persist(state);
    return res.status(204).send();
  });

  app.get('/rest/api/3/search', (req, res) => {
    const jql = (req.query.jql || '').toString().toLowerCase();
    if (!jql || jql.trim() === '') {
      return res.json(state.issueSummary);
    }

    const issues = Object.values(state.issues).map((issue) => ({ key: issue.key, fields: issue.fields }));

    const filtered = issues.filter((issue) => {
      const conditions = jql.split(' and ').map((token) => token.trim());
      return conditions.every((token) => {
        if (token.startsWith('project')) {
          const value = token.split('=')[1]?.replace(/"/g, '').trim();
          if (!value) return true;
          return issue.key.toLowerCase().startsWith(value.toLowerCase() + '-');
        }
        if (token.startsWith('key')) {
          const value = token.split('=')[1]?.replace(/"/g, '').trim();
          if (!value) return true;
          return issue.key.toLowerCase() === value.toLowerCase();
        }
        if (token.includes('issuetype')) {
          const value = token.split('=')[1]?.replace(/"/g, '').trim();
          if (!value) return true;
          return issue.fields.issuetype?.name?.toLowerCase() === value.toLowerCase();
        }
        if (token.includes('status')) {
          const value = token.split('=')[1]?.replace(/"/g, '').trim();
          if (!value) return true;
          return issue.fields.status?.name?.toLowerCase() === value.toLowerCase();
        }
        return true;
      });
    });

    const response = {
      expand: 'schema,names',
      startAt: 0,
      maxResults: filtered.length,
      total: filtered.length,
      isLast: true,
      issues: filtered,
    };
    res.json(response);
  });

  app.get('/rest/api/3/search/epic', (req, res) => {
    res.json(state.epicIssues);
  });

  app.post('/rest/api/3/issue', (req, res) => {
    const payload = req.body || {};
    const fields = payload.fields || {};
    const projectKey = fields.project?.key || fields.project?.id && state.projects.find((p) => p.id === fields.project.id)?.key;
    if (!projectKey) {
      return res.status(400).json({ errorMessages: ['project key required'], errors: {} });
    }

    const nextKey = nextIssueKey(projectKey.toUpperCase());
    const now = new Date().toISOString();
    const issue = normalizeIssue({
      key: nextKey,
      fields: {
        summary: fields.summary || '(no summary)',
        description: fields.description || null,
        issuetype: fields.issuetype || { name: 'Task' },
        status: { name: 'To Do' },
        priority: fields.priority || { name: 'Medium' },
        assignee: fields.assignee || null,
        reporter: fields.reporter || state.myself,
        labels: fields.labels || [],
        watches: { watchCount: 0, isWatching: false },
        created: now,
        updated: now,
      },
    }, { id: String(Date.now()) });

    state.issues[nextKey.toUpperCase()] = issue;
    state.issueSummary.issues.push({ key: issue.key, fields: issue.fields });
    persist(state);

    res.status(201).json({ id: issue.id, key: issue.key, self: issue.self });
  });

  app.use((req, res) => {
    res.status(404).json({ errorMessages: ['No mock implemented for this path'], errors: {} });
  });

  app.listen(PORT, '0.0.0.0', () => {
    /* eslint-disable no-console */
    console.log(`Jira mock server listening on port ${PORT}`);
    /* eslint-enable no-console */
  });
}
