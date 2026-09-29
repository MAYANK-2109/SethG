require('dotenv').config();
const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const { setupSocketIO } = require('./socket');
const cors = require('cors');

const authRoutes = require('./routes/auth');
const userRoutes = require('./routes/user');
const earningsRoutes = require('./routes/earnings');
const lotRoutes = require('./routes/lots');
const recyclerRoutes = require('./routes/recycler');
const hubRoutes = require('./routes/hubs');
const poolRoutes = require('./routes/pools');
const notificationRoutes = require('./routes/notifications');
const { errorHandler } = require('./middleware/errorHandler');

const app = express();
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: '*' } });
setupSocketIO(io);
app.set('io', io);

// ── Middleware ─────────────────────────────────────────────────────────────
app.use(cors());
app.use(express.json({ limit: '15mb' }));
app.use(express.urlencoded({ extended: true, limit: '15mb' }));

// ── Routes ─────────────────────────────────────────────────────────────────
app.use('/auth', authRoutes);
app.use('/user', userRoutes);
app.use('/earnings', earningsRoutes);
app.use('/lots', lotRoutes);
app.use('/recycler', recyclerRoutes);
app.use('/hubs', hubRoutes);
app.use('/pools', poolRoutes);
app.use('/notifications', notificationRoutes);
app.use('/user/notifications', notificationRoutes);

// ── Health check ────────────────────────────────────────────────────────────
app.get('/health', (_req, res) => res.json({ status: 'ok', service: 'SethG API' }));

// ── Global error handler ────────────────────────────────────────────────────
app.use(errorHandler);

// ── Start server ────────────────────────────────────────────────────────────
const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`SethG API running on port ${PORT}`);
});

module.exports = app;
