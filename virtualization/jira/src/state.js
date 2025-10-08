const { faker } = require('@faker-js/faker');
const { v4: uuidv4 } = require('uuid');

const STATUS_CATEGORIES = {
  TODO: {
    id: 2,
    key: 'new',
    colorName: 'blue-gray',
    name: 'To Do',
  },
  IN_PROGRESS: {
    id: 4,
    key: 'indeterminate',
    colorName: 'yellow',
    name: 'In Progress',
  },
  DONE: {
    id: 3,
    key: 'done',
    colorName: 'green',
    name: 'Done',
  },
};

const STATUS_DEFS = [
  { id: '10000', name: 'Backlog', category: 'TODO' },
  { id: '10001', name: 'Ready for Dev', category: 'TODO' },
  { id: '10002', name: 'In Progress', category: 'IN_PROGRESS' },
  { id: '10003', name: 'In Review', category: 'IN_PROGRESS' },
  { id: '10004', name: 'Blocked', category: 'IN_PROGRESS' },
  { id: '10005', name: 'Done', category: 'DONE' },
];

const STATUS_TRANSITIONS = {
  Backlog: [
    { id: '11', to: 'Ready for Dev' },
  ],
  'Ready for Dev': [
    { id: '12', to: 'In Progress' },
    { id: '15', to: 'Blocked' },
  ],
  'In Progress': [
    { id: '13', to: 'In Review' },
    { id: '16', to: 'Blocked' },
    { id: '17', to: 'Done' },
  ],
  'In Review': [
    { id: '14', to: 'Done' },
    { id: '18', to: 'In Progress' },
  ],
  Blocked: [
    { id: '19', to: 'In Progress' },
    { id: '20', to: 'Done' },
  ],
  Done: [
    { id: '21', to: 'In Progress' },
  ],
};

const ISSUE_TYPES = [
  { id: '10001', name: 'Story', hierarchyLevel: 0, subtask: false },
  { id: '10002', name: 'Task', hierarchyLevel: 0, subtask: false },
  { id: '10003', name: 'Bug', hierarchyLevel: 0, subtask: false },
  { id: '10004', name: 'Sub-task', hierarchyLevel: -1, subtask: true },
  { id: '10005', name: 'Epic', hierarchyLevel: 3, subtask: false },
];

const PRIORITIES = [
  { id: '1', name: 'Highest', iconUrl: 'https://example.com/priority/highest.png' },
  { id: '2', name: 'High', iconUrl: 'https://example.com/priority/high.png' },
  { id: '3', name: 'Medium', iconUrl: 'https://example.com/priority/medium.png' },
  { id: '4', name: 'Low', iconUrl: 'https://example.com/priority/low.png' },
  { id: '5', name: 'Lowest', iconUrl: 'https://example.com/priority/lowest.png' },
];

function createAvatarUrls() {
  return {
    '16x16': faker.image.avatarGitHub(),
    '24x24': faker.image.avatarGitHub(),
    '32x32': faker.image.avatarGitHub(),
    '48x48': faker.image.avatarGitHub(),
  };
}

function buildStatus(name, baseUrl) {
  const statusDef = STATUS_DEFS.find((status) => status.name === name) ?? STATUS_DEFS[0];
  const category = STATUS_CATEGORIES[statusDef.category];
  return {
    self: `${baseUrl}/rest/api/3/status/${statusDef.id}`,
    description: `${statusDef.name} status`,
    iconUrl: 'https://example.com/status/icon.png',
    name: statusDef.name,
    id: statusDef.id,
    statusCategory: {
      self: `${baseUrl}/rest/api/3/statuscategory/${category.id}`,
      id: category.id,
      key: category.key,
      colorName: category.colorName,
      name: category.name,
    },
  };
}

function buildIssueType(name, baseUrl) {
  const issueType = ISSUE_TYPES.find((type) => type.name === name) ?? ISSUE_TYPES[0];
  return {
    self: `${baseUrl}/rest/api/3/issuetype/${issueType.id}`,
    id: issueType.id,
    description: `${issueType.name} issue type`,
    iconUrl: 'https://example.com/issuetype/icon.png',
    name: issueType.name,
    subtask: issueType.subtask,
    avatarId: faker.number.int({ min: 10, max: 200 }),
    hierarchyLevel: issueType.hierarchyLevel,
  };
}

function buildPriority(name, baseUrl) {
  const priority = PRIORITIES.find((candidate) => candidate.name === name) ?? PRIORITIES[2];
  return {
    self: `${baseUrl}/rest/api/3/priority/${priority.id}`,
    id: priority.id,
    name: priority.name,
    iconUrl: priority.iconUrl,
  };
}

function createUser(baseUrl) {
  const accountId = uuidv4();
  return {
    accountId,
    self: `${baseUrl}/rest/api/3/user?accountId=${accountId}`,
    emailAddress: faker.internet.email().toLowerCase(),
    displayName: faker.person.fullName(),
    active: true,
    timeZone: faker.location.timeZone(),
    avatarUrls: createAvatarUrls(),
  };
}

function projectField(project, baseUrl) {
  return {
    self: `${baseUrl}/rest/api/3/project/${project.id}`,
    id: project.id,
    key: project.key,
    name: project.name,
    projectTypeKey: 'software',
    simplified: false,
    avatarUrls: project.avatarUrls,
  };
}

function minifiedIssue(issue) {
  return {
    id: issue.id,
    key: issue.key,
    self: issue.self,
    fields: {
      summary: issue.fields.summary,
      status: issue.fields.status,
      issuetype: issue.fields.issuetype,
    },
  };
}

function randomLabels() {
  return faker.helpers.arrayElements(
    ['frontend', 'backend', 'infra', 'ux', 'devops', 'qa', 'urgent', 'low-priority'],
    { min: 0, max: 3 }
  );
}

function randomComponents(baseUrl) {
  const count = faker.number.int({ min: 0, max: 3 });
  return Array.from({ length: count }, () => {
    const id = faker.number.int({ min: 2000, max: 9999 }).toString();
    return {
      self: `${baseUrl}/rest/api/3/component/${id}`,
      id,
      name: faker.commerce.department(),
    };
  });
}

function randomVersions(baseUrl) {
  const count = faker.number.int({ min: 0, max: 2 });
  return Array.from({ length: count }, () => {
    const id = faker.number.int({ min: 3000, max: 9999 }).toString();
    return {
      self: `${baseUrl}/rest/api/3/version/${id}`,
      id,
      name: `${faker.commerce.productAdjective()} ${faker.number.int({ min: 1, max: 20 })}.0`,
      released: faker.datatype.boolean(),
    };
  });
}

function createIssueInstance({
  project,
  issueType,
  status,
  baseUrl,
  assignee,
  reporter,
  parent,
}) {
  const id = faker.number.int({ min: 10000, max: 99999 }).toString();
  const key = `${project.key}-${project.sequence.next().value}`;
  const summary = faker.hacker.phrase();
  const created = faker.date.recent({ days: 30 });
  const updated = faker.date.between({ from: created, to: new Date() });
  const due = faker.date.soon({ days: 30, refDate: updated });
  const priority = faker.helpers.arrayElement(PRIORITIES);

  const issue = {
    id,
    key,
    self: `${baseUrl}/rest/api/3/issue/${key}`,
    fields: {
      summary,
      description: faker.lorem.paragraphs({ min: 1, max: 3 }, '\n\n'),
      issuetype: buildIssueType(issueType.name, baseUrl),
      project: projectField(project, baseUrl),
      status: buildStatus(status.name, baseUrl),
      statuscategorychangedate: updated.toISOString(),
      priority: buildPriority(priority.name, baseUrl),
      assignee,
      reporter,
      creator: reporter,
      labels: randomLabels(),
      components: randomComponents(baseUrl),
      fixVersions: randomVersions(baseUrl),
      versions: randomVersions(baseUrl),
      environment: faker.lorem.sentence(),
      created: created.toISOString(),
      updated: updated.toISOString(),
      duedate: due.toISOString().slice(0, 10),
      watches: {
        watchCount: faker.number.int({ min: 0, max: 6 }),
        isWatching: faker.datatype.boolean(),
        self: `${baseUrl}/rest/api/3/issue/${key}/watchers`,
      },
      subtasks: [],
      parent: parent ? minifiedIssue(parent) : undefined,
      progress: {
        progress: faker.number.int({ min: 1000, max: 36000 }),
        total: faker.number.int({ min: 36000, max: 72000 }),
      },
      aggregateprogress: {
        progress: faker.number.int({ min: 1000, max: 36000 }),
        total: faker.number.int({ min: 36000, max: 72000 }),
      },
      timetracking: {
        originalEstimate: `${faker.number.int({ min: 4, max: 24 })}h`,
        remainingEstimate: `${faker.number.int({ min: 1, max: 12 })}h`,
        timeSpent: `${faker.number.int({ min: 1, max: 20 })}h`,
      },
      aggregateTimeOriginalEstimate: faker.number.int({ min: 3600, max: 144000 }),
      aggregateTimeSpent: faker.number.int({ min: 600, max: 36000 }),
      aggregateTimeEstimate: faker.number.int({ min: 600, max: 36000 }),
      timeoriginalestimate: faker.number.int({ min: 600, max: 36000 }),
      timespent: faker.number.int({ min: 600, max: 36000 }),
      timeestimate: faker.number.int({ min: 600, max: 36000 }),
      worklog: {
        startAt: 0,
        maxResults: 20,
        total: 0,
        worklogs: [],
      },
      comment: {
        comments: [],
        self: `${baseUrl}/rest/api/3/issue/${key}/comment`,
        maxResults: 0,
        total: 0,
      },
      issuelinks: [],
      votes: {
        votes: faker.number.int({ min: 0, max: 20 }),
        hasVoted: faker.datatype.boolean(),
        self: `${baseUrl}/rest/api/3/issue/${key}/votes`,
      },
      security: null,
    },
    statusHistory: [status.name],
    transitionsApplied: [],
    parentKey: parent ? parent.key : undefined,
    issueType: issueType.name,
  };

  return issue;
}

function attachSubtask(parent, subtask) {
  parent.fields.subtasks.push(minifiedIssue(subtask));
  subtask.fields.parent = minifiedIssue(parent);
}

function detachSubtask(parent, subtaskKey) {
  parent.fields.subtasks = parent.fields.subtasks.filter((entry) => entry.key !== subtaskKey);
}

function createProjects({ count, baseUrl }) {
  const projects = [];
  const usedKeys = new Set();
  while (projects.length < count) {
    const key = faker.string.alpha({ length: 3, casing: 'upper' });
    if (usedKeys.has(key)) {
      // retry until we find a unique key
      // eslint-disable-next-line no-continue
      continue;
    }
    usedKeys.add(key);
    const project = {
      id: faker.number.int({ min: 10000, max: 99999 }).toString(),
      key,
      name: `${faker.word.adjective({ length: 5 }).toUpperCase()} ${faker.word.noun({ length: 6 }).toUpperCase()}`,
      lead: null,
      avatarUrls: createAvatarUrls(),
      sequence: (function* sequenceGenerator() {
        let counter = faker.number.int({ min: 1, max: 40 });
        // eslint-disable-next-line no-constant-condition
        while (true) {
          yield counter;
          counter += 1;
        }
      })(),
    };
    projects.push(project);
  }
  return projects;
}

function pickStatus() {
  return faker.helpers.arrayElement(STATUS_DEFS.filter((status) => status.name !== 'Backlog'));
}

function generateIssuesForProjects({ projects, baseUrl, users, issuesPerProject }) {
  const issues = [];
  const userEntries = Array.from(users.values());

  for (const project of projects) {
    const topLevelCount = issuesPerProject;
    for (let i = 0; i < topLevelCount; i += 1) {
      const issueType = faker.helpers.arrayElement(ISSUE_TYPES.filter((type) => !type.subtask));
      const status = pickStatus();
      const assignee = faker.helpers.arrayElement(userEntries);
      const reporter = faker.helpers.arrayElement(userEntries);

      const issue = createIssueInstance({
        project,
        issueType,
        status,
        baseUrl,
        assignee,
        reporter,
        parent: null,
      });

      issues.push(issue);

      const subtaskCount = faker.number.int({ min: 0, max: 3 });
      for (let s = 0; s < subtaskCount; s += 1) {
        const subtask = createIssueInstance({
          project,
          issueType: ISSUE_TYPES.find((type) => type.subtask),
          status: faker.helpers.arrayElement(STATUS_DEFS.filter((candidate) => candidate.name !== 'Backlog')),
          baseUrl,
          assignee: faker.helpers.arrayElement(userEntries),
          reporter: faker.helpers.arrayElement(userEntries),
          parent: issue,
        });
        attachSubtask(issue, subtask);
        issues.push(subtask);
      }
    }
  }

  return issues;
}

function buildState(config) {
  const { seed, projectCount, issuesPerProject, baseUrl } = config;
  if (seed !== undefined && seed !== null && !Number.isNaN(Number(seed))) {
    faker.seed(Number(seed));
  }

  const projects = createProjects({ count: projectCount, baseUrl });
  const users = new Map();
  const userCount = Math.max(6, projectCount * 3);
  for (let i = 0; i < userCount; i += 1) {
    const user = createUser(baseUrl);
    users.set(user.accountId, user);
  }

  const issues = generateIssuesForProjects({ projects, baseUrl, users, issuesPerProject });
  const issuesByKey = new Map();
  const issuesById = new Map();
  const projectIndex = new Map(projects.map((project) => [project.key.toUpperCase(), project]));

  for (const issue of issues) {
    issuesByKey.set(issue.key.toUpperCase(), issue);
    issuesById.set(issue.id, issue);
  }

  return {
    config,
    projects,
    projectIndex,
    users,
    issuesByKey,
    issuesById,
  };
}

function listProjects(state) {
  return state.projects;
}

function getProject(state, projectKeyOrId) {
  if (!projectKeyOrId) return null;
  const normalized = projectKeyOrId.toUpperCase();
  return state.projectIndex.get(normalized) ?? state.projects.find((project) => project.id === projectKeyOrId) ?? null;
}

function getIssue(state, idOrKey) {
  if (!idOrKey) return null;
  const normalizedKey = idOrKey.toUpperCase();
  return state.issuesByKey.get(normalizedKey) ?? state.issuesById.get(idOrKey) ?? null;
}

function upsertIssue(state, issue) {
  state.issuesByKey.set(issue.key.toUpperCase(), issue);
  state.issuesById.set(issue.id, issue);
}

function deleteIssue(state, issue) {
  state.issuesByKey.delete(issue.key.toUpperCase());
  state.issuesById.delete(issue.id);
}

function getTransitionsForIssue(issue, baseUrl) {
  const available = STATUS_TRANSITIONS[issue.fields.status.name] ?? [];
  return available.map((transition) => {
    const status = buildStatus(transition.to, baseUrl);
    return {
      id: transition.id,
      name: transition.to,
      to: status,
      hasScreen: false,
      isGlobal: true,
      isConditional: false,
      isInitial: false,
    };
  });
}

function applyTransition(state, issue, transitionIdOrName, baseUrl) {
  const transitions = getTransitionsForIssue(issue, baseUrl);
  const transition = transitions.find(
    (candidate) => candidate.id === transitionIdOrName || candidate.name.toLowerCase() === transitionIdOrName.toLowerCase()
  );

  if (!transition) {
    return null;
  }

  issue.fields.status = transition.to;
  issue.fields.statuscategorychangedate = new Date().toISOString();
  issue.fields.updated = new Date().toISOString();
  issue.statusHistory.push(transition.to.name);
  issue.transitionsApplied.push({ id: transition.id, name: transition.name, at: new Date().toISOString() });

  if (issue.fields.parent?.key) {
    const parentIssue = getIssue(state, issue.fields.parent.key);
    if (parentIssue) {
      parentIssue.fields.subtasks = parentIssue.fields.subtasks.map((entry) => {
        if (entry.key === issue.key) {
          return minifiedIssue(issue);
        }
        return entry;
      });
      issue.fields.parent = minifiedIssue(parentIssue);
    }
  }

  return transition;
}

function createIssueFromPayload(state, payload) {
  const { fields = {} } = payload;
  const projectKey = fields.project?.key ?? fields.project?.id ?? fields.project?.key;
  const project = getProject(state, projectKey);
  if (!project) {
    throw new Error('Project not found');
  }

  const issueTypeName = fields.issuetype?.name ?? 'Story';
  const issueType = ISSUE_TYPES.find((type) => type.name.toLowerCase() === issueTypeName.toLowerCase()) ?? ISSUE_TYPES[0];

  const statusName = fields.status?.name ?? 'Ready for Dev';
  const status = STATUS_DEFS.find((candidate) => candidate.name === statusName) ?? STATUS_DEFS[1];
  const assignee = fields.assignee?.accountId ? state.users.get(fields.assignee.accountId) : faker.helpers.arrayElement(Array.from(state.users.values()));
  const reporter = fields.reporter?.accountId ? state.users.get(fields.reporter.accountId) : faker.helpers.arrayElement(Array.from(state.users.values()));

  const parentKey = fields.parent?.key;
  const parentIssue = parentKey ? getIssue(state, parentKey) : null;
  if (parentKey && !parentIssue) {
    throw new Error(`Parent issue ${parentKey} not found`);
  }

  const issue = createIssueInstance({
    project,
    issueType,
    status,
    baseUrl: state.config.baseUrl,
    assignee,
    reporter,
    parent: parentIssue,
  });

  if (fields.summary) {
    issue.fields.summary = fields.summary;
  }

  if (fields.description) {
    issue.fields.description = typeof fields.description === 'string' ? fields.description : JSON.stringify(fields.description);
  }

  if (Array.isArray(fields.labels) && fields.labels.length > 0) {
    issue.fields.labels = fields.labels;
  }

  if (parentIssue) {
    attachSubtask(parentIssue, issue);
  }

  upsertIssue(state, issue);

  return issue;
}

function updateParentRelationships(state) {
  for (const issue of state.issuesByKey.values()) {
    if (!issue.parentKey) continue;
    const parent = getIssue(state, issue.parentKey);
    if (parent) {
      const hasSubtask = parent.fields.subtasks.some((entry) => entry.key === issue.key);
      if (!hasSubtask) {
        attachSubtask(parent, issue);
      }
    }
  }
}

function searchIssues(state, criteria) {
  const { jql } = criteria;
  const iterable = Array.from(state.issuesByKey.values());
  if (!jql) {
    return iterable;
  }

  const tokens = jql
    .split(/AND/i)
    .map((token) => token.trim())
    .filter(Boolean);

  return iterable.filter((issue) => {
    for (const token of tokens) {
      if (/^project\s*=/.test(token)) {
        const match = token.split('=').map((part) => part.trim());
        const value = match[1]?.replace(/"/g, '').toUpperCase();
        if (value && issue.fields.project.key.toUpperCase() !== value) {
          return false;
        }
      } else if (/^key\s*=/.test(token)) {
        const value = token.split('=')[1]?.trim().replace(/"/g, '').toUpperCase();
        if (value && issue.key.toUpperCase() !== value) {
          return false;
        }
      } else if (/^parent\s*=/.test(token)) {
        const value = token.split('=')[1]?.trim().replace(/"/g, '').toUpperCase();
        const parentKey = issue.fields.parent?.key?.toUpperCase();
        if (value && parentKey !== value) {
          return false;
        }
      } else if (/^issuetype\s*=/.test(token)) {
        const value = token.split('=')[1]?.trim().replace(/"/g, '').toLowerCase();
        if (value && issue.fields.issuetype.name.toLowerCase() !== value) {
          return false;
        }
      } else if (/^status\s*=/.test(token)) {
        const value = token.split('=')[1]?.trim().replace(/"/g, '').toLowerCase();
        if (value && issue.fields.status.name.toLowerCase() !== value) {
          return false;
        }
      }
    }
    return true;
  });
}

module.exports = {
  buildState,
  listProjects,
  getProject,
  getIssue,
  upsertIssue,
  deleteIssue,
  getTransitionsForIssue,
  applyTransition,
  createIssueFromPayload,
  updateParentRelationships,
  searchIssues,
};
