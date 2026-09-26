require('dotenv').config();
const express = require('express');
const cors = require('cors');

const authRoutes = require('./routes/auth');
const userRoutes = require('./routes/user');
const earningsRoutes = require('./routes/earnings');
const lotRoutes = require('./routes/lots');
const recyclerRoutes = require('./routes/recycler');
const hubRoutes = require('./routes/hubs');
const { errorHandler } = require('./middleware/errorHandler');

const app = express();

// ── Middleware ─────────────────────────────────────────────────────────────
app.use(cors());
app.use(express.json());

// ── Routes ─────────────────────────────────────────────────────────────────
app.use('/auth', authRoutes);
app.use('/user', userRoutes);
app.use('/earnings', earningsRoutes);
app.use('/lots', lotRoutes);
app.use('/recycler', recyclerRoutes);
app.use('/hubs', hubRoutes);

// ── Health check ────────────────────────────────────────────────────────────
app.get('/health', (_req, res) => res.json({ status: 'ok', service: 'SethG API' }));

// ── Global error handler ────────────────────────────────────────────────────
app.use(errorHandler);

// ── Start server ────────────────────────────────────────────────────────────
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`SethG API running on port ${PORT}`);
});

module.exports = app;
