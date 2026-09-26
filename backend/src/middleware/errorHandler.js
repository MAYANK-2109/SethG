/**
 * Global Express error handler.
 * Returns consistent JSON error structure.
 */
// eslint-disable-next-line no-unused-vars
exports.errorHandler = (err, _req, res, _next) => {
  console.error('[ERROR]', err);

  // Postgres unique-violation
  if (err.code === '23505') {
    return res.status(409).json({ error: 'Duplicate value — resource already exists' });
  }

  // Postgres check-constraint violation
  if (err.code === '23514') {
    return res.status(422).json({ error: 'Database constraint violated' });
  }

  const status = err.status || err.statusCode || 500;
  const message = err.expose ? err.message : 'Internal server error';

  return res.status(status).json({ error: message });
};
