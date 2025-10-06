#!/usr/bin/env node

/**
 * Mock API server for kapture testing
 * Simulates ticket and tracking endpoints
 */

const http = require('http');
const url = require('url');

const PORT = process.env.PORT || 8080;

// Mock ticket database
const tickets = {
  'PROJ-123': {
    ticketId: 'PROJ-123',
    status: 'IN_PROGRESS',
    title: 'Implement user authentication',
    assignee: 'developer@example.com',
    createdAt: '2025-01-15T10:00:00Z'
  },
  'PROJ-456': {
    ticketId: 'PROJ-456',
    status: 'READY',
    title: 'Fix login bug',
    assignee: 'developer@example.com',
    createdAt: '2025-01-16T14:30:00Z'
  },
  'PROJ-789': {
    ticketId: 'PROJ-789',
    status: 'IN_REVIEW',
    title: 'Add API documentation',
    assignee: 'writer@example.com',
    createdAt: '2025-01-17T09:15:00Z'
  },
  'ABC-100': {
    ticketId: 'ABC-100',
    status: 'DONE',
    title: 'Refactor database layer',
    assignee: 'senior@example.com',
    createdAt: '2025-01-10T11:00:00Z'
  },
  'ABC-200': {
    ticketId: 'ABC-200',
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

  // GET /tickets/:ticketId/status
  const ticketMatch = pathname.match(/^\/tickets\/([A-Z]+-\d+)\/status$/);
  if (method === 'GET' && ticketMatch) {
    const ticketId = ticketMatch[1];
    const ticket = tickets[ticketId];

    if (ticket) {
      console.log(`  → Found ticket: ${ticket.status}`);
      res.writeHead(200);
      res.end(JSON.stringify(ticket, null, 2));
    } else {
      console.log(`  → Ticket not found`);
      res.writeHead(404);
      res.end(JSON.stringify({
        error: 'Ticket not found',
        ticketId: ticketId
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
          ticket: event.ticket,
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

  // GET /tickets (list all)
  if (method === 'GET' && pathname === '/tickets') {
    res.writeHead(200);
    res.end(JSON.stringify({
      tickets: Object.values(tickets)
    }, null, 2));
    return;
  }

  // PUT /tickets/:ticketId/status (update status for testing)
  const updateMatch = pathname.match(/^\/tickets\/([A-Z]+-\d+)\/status$/);
  if (method === 'PUT' && updateMatch) {
    const ticketId = updateMatch[1];
    let body = '';

    req.on('data', chunk => {
      body += chunk.toString();
    });

    req.on('end', () => {
      try {
        const update = JSON.parse(body);
        if (tickets[ticketId]) {
          tickets[ticketId].status = update.status;
          console.log(`  → Updated ${ticketId} to ${update.status}`);
          res.writeHead(200);
          res.end(JSON.stringify(tickets[ticketId], null, 2));
        } else {
          res.writeHead(404);
          res.end(JSON.stringify({ error: 'Ticket not found' }));
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
      tickets: Object.keys(tickets).length,
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
      'GET /tickets',
      'GET /tickets/:ticketId/status',
      'PUT /tickets/:ticketId/status',
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
  console.log(`  GET  /tickets`);
  console.log(`  GET  /tickets/:ticketId/status`);
  console.log(`  PUT  /tickets/:ticketId/status`);
  console.log(`  POST /events/track`);
  console.log(`  GET  /events`);
  console.log('');
  console.log('Mock tickets:');
  Object.values(tickets).forEach(t => {
    console.log(`  ${t.ticketId.padEnd(10)} ${t.status.padEnd(15)} ${t.title}`);
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
