const pool = require('../db/pool');

/**
 * Allows the request only for users with one of the given roles.
 * Must run after authenticate. Attaches req.user (id, role, is_verified, lat, lon).
 */
module.exports = function requireRole(...roles) {
  return async (req, res, next) => {
    try {
      const { rows } = await pool.query(
        `SELECT id, name, role, is_verified, lat, lon, vehicle_min_kg FROM users WHERE id = $1`,
        [req.userId]
      );
      if (!rows.length) return res.status(404).json({ error: 'User not found' });
      if (!roles.includes(rows[0].role)) {
        return res.status(403).json({ error: `Only ${roles.join('/')} accounts can do this` });
      }
      req.user = rows[0];
      next();
    } catch (err) {
      next(err);
    }
  };
};
