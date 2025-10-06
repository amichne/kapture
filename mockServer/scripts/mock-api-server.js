#!/usr/bin/env node

/**
 * Mock API server for kapture testing
 * Simulates task and tracking endpoints
 */

const http = require('http');
const url = require('url');

const PORT = process.env.PORT || 8080;

// Mock task database
const tasks = {
  'PROJ-123': {
    taskId: 'PROJ-123',
    status: 'IN_PROGRESS',
    title: 'Implement user authentication',
    assignee: 'developer@example.com',
    createdAt: '2025-01-15T10:00:00Z'
  },
  'PROJ-456': {
    taskId: 'PROJ-456',
    status: 'READY',
    title: 'Fix login bug',
    assignee: 'developer@example.com',
    createdAt: '2025-01-16T14:30:00Z'
  },
  'PROJ-789': {
    taskId: 'PROJ-789',
    status: 'IN_REVIEW',
    title: 'Add API documentation',
    assignee: 'writer@example.com',
    createdAt: '2025-01-17T09:15:00Z'
  },
  'ABC-100': {
    taskId: 'ABC-100',
    status: 'DONE',
    title: 'Refactor database layer',
    assignee: 'senior@example.com',
    createdAt: '2025-01-10T11:00:00Z'
  },
  'ABC-200': {
    taskId: 'ABC-200',
    status: 'BLOCKED',
    title: 'Deploy to production',
    assignee: 'devops@example.com',
    createdAt: '2025-01-18T16:45:00Z'
  }
};

// Track events in memory
const events = [];

function handleRequest(req, res) {
  const parsedUrl = url.parse(req.url, true);
  const pathname = parsedUrl.pathname;
  const method = req.method;

  console.log(`${new Date().toISOString()} ${method} ${pathname}`);

  // CORS headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  res.setHeader('Content-Type', 'application/json');

  // Handle preflight
  if (method === 'OPTIONS') {
    res.writeHead(200);
    res.end();
    return;
  }

  // GET /tasks/:taskId/status
  const taskMatch = pathname.match(/^\/tasks\/([A-Z]+-\d+)\/status$/);
  if (method === 'GET' && taskMatch) {
    const taskId = taskMatch[1];
    const task = tasks[taskId];

    if (task) {
      console.log(`  → Found task: ${task.status}`);
      res.writeHead(200);
      res.end(JSON.stringify(task, null, 2));
    } else {
      console.log(`  → Task not found`);
      res.writeHead(404);
      res.end(JSON.stringify({
        error: 'Task not found',
        taskId: taskId
      }, null, 2));
    }
    return;
  }

  // POST /events/track
  if (method === 'POST' && pathname === '/events/track') {
    let body = '';

    req.on('data', chunk => {
      body += chunk.toString();
    });

    req.on('end', () => {
      try {
        const event = JSON.parse(body);
        event.receivedAt = new Date().toISOString();
        events.push(event);

        console.log(`  → Tracked event:`, {
          command: event.command,
          task: event.task,
          duration: event.durationMs + 'ms',
          exitCode: event.exitCode
        });

        res.writeHead(200);
        res.end(JSON.stringify({
          success: true,
          eventId: events.length
        }, null, 2));
      } catch (err) {
        console.error(`  → Error parsing event:`, err.message);
        res.writeHead(400);
        res.end(JSON.stringify({
          error: 'Invalid JSON',
          message: err.message
        }, null, 2));
      }
    });
    return;
  }

  // GET /events (debug endpoint)
  if (method === 'GET' && pathname === '/events') {
    const limit = parseInt(parsedUrl.query.limit) || 10;
    const recent = events.slice(-limit);

    res.writeHead(200);
    res.end(JSON.stringify({
      total: events.length,
      events: recent
    }, null, 2));
    return;
  }

  // GET /tasks (list all)
  if (method === 'GET' && pathname === '/tasks') {
    res.writeHead(200);
    res.end(JSON.stringify({
      tasks: Object.values(tasks)
    }, null, 2));
    return;
  }

  // PUT /tasks/:taskId/status (update status for testing)
  const updateMatch = pathname.match(/^\/tasks\/([A-Z]+-\d+)\/status$/);
  if (method === 'PUT' && updateMatch) {
    const taskId = updateMatch[1];
    let body = '';

    req.on('data', chunk => {
      body += chunk.toString();
    });

    req.on('end', () => {
      try {
        const update = JSON.parse(body);
        if (tasks[taskId]) {
          tasks[taskId].status = update.status;
          console.log(`  → Updated ${taskId} to ${update.status}`);
          res.writeHead(200);
          res.end(JSON.stringify(tasks[taskId], null, 2));
        } else {
          res.writeHead(404);
          res.end(JSON.stringify({ error: 'Task not found' }));
        }
      } catch (err) {
        res.writeHead(400);
        res.end(JSON.stringify({ error: 'Invalid JSON' }));
      }
    });
    return;
  }

  // GET /health
  if (method === 'GET' && pathname === '/health') {
    res.writeHead(200);
    res.end(JSON.stringify({
      status: 'healthy',
      uptime: process.uptime(),
      tasks: Object.keys(tasks).length,
      events: events.length
    }, null, 2));
    return;
  }

  // 404 for everything else
  console.log(`  → Not found`);
  res.writeHead(404);
  res.end(JSON.stringify({
    error: 'Not found',
    path: pathname,
    availableEndpoints: [
      'GET /tasks',
      'GET /tasks/:taskId/status',
      'PUT /tasks/:taskId/status',
      'POST /events/track',
      'GET /events',
      'GET /health'
    ]
  }, null, 2));
}

const server = http.createServer(handleRequest);

server.listen(PORT, () => {
  console.log('');
  console.log('=====================================');
  console.log('  Kapture Mock API Server');
  console.log('=====================================');
  console.log(`  URL: http://localhost:${PORT}`);
  console.log('');
  console.log('Available endpoints:');
  console.log(`  GET  /health`);
  console.log(`  GET  /tasks`);
  console.log(`  GET  /tasks/:taskId/status`);
  console.log(`  PUT  /tasks/:taskId/status`);
  console.log(`  POST /events/track`);
  console.log(`  GET  /events`);
  console.log('');
  console.log('Mock tasks:');
  Object.values(tasks).forEach(t => {
    console.log(`  ${t.taskId.padEnd(10)} ${t.status.padEnd(15)} ${t.title}`);
  });
  console.log('');
  console.log('Press Ctrl+C to stop');
  console.log('=====================================');
  console.log('');
});

// Graceful shutdown
process.on('SIGTERM', () => {
  console.log('\nShutting down gracefully...');
  server.close(() => {
    console.log('Server closed');
    process.exit(0);
  });
});

process.on('SIGINT', () => {
  console.log('\nShutting down gracefully...');
  server.close(() => {
    console.log('Server closed');
    process.exit(0);
  });
});
