const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const { v4: uuidv4 } = require('uuid');
const { body, validationResult } = require('express-validator');
const pool = require('../db/pool');

const SALT_ROUNDS = 12;
const ACCESS_EXPIRES_IN  = process.env.JWT_ACCESS_EXPIRES_IN  || '15m';
const REFRESH_EXPIRES_IN = process.env.JWT_REFRESH_EXPIRES_IN || '30d';

// ── Helper: sign tokens ─────────────────────────────────────────────────────
function signAccessToken(userId) {
  return jwt.sign(
    { sub: userId },
    process.env.JWT_ACCESS_SECRET,
    { expiresIn: ACCESS_EXPIRES_IN }
  );
}

function signRefreshToken(userId) {
  return jwt.sign(
    { sub: userId, jti: uuidv4() },
    process.env.JWT_REFRESH_SECRET,
    { expiresIn: REFRESH_EXPIRES_IN }
  );
}

function hashToken(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

async function storeRefreshToken(client, userId, token) {
  const tokenHash = hashToken(token);
  // Decode to get expiry
  const decoded = jwt.decode(token);
  const expiresAt = new Date(decoded.exp * 1000);
  await client.query(
    `INSERT INTO refresh_tokens (user_id, token_hash, expires_at)
     VALUES ($1, $2, $3)`,
    [userId, tokenHash, expiresAt]
  );
}

// ── Validation rules ────────────────────────────────────────────────────────
exports.registerValidation = [
  body('name').trim().notEmpty().withMessage('Name is required'),
  body('phone')
    .optional({ nullable: true, checkFalsy: true })
    .isMobilePhone()
    .withMessage('Invalid phone number'),
  body('email')
    .optional({ nullable: true, checkFalsy: true })
    .isEmail().normalizeEmail()
    .withMessage('Invalid email address'),
  body('password')
    .isLength({ min: 6 })
    .withMessage('Password must be at least 6 characters'),
  body('confirmPassword')
    .custom((value, { req }) => {
      if (value !== req.body.password) throw new Error('Passwords do not match');
      return true;
    }),
  body('role')
    .optional()
    .isIn(['recycler', 'vendor'])
    .withMessage('Invalid role specified'),
];

exports.loginValidation = [
  body('password').notEmpty().withMessage('Password is required'),
];

// ── POST /auth/register ─────────────────────────────────────────────────────
exports.register = async (req, res, next) => {
  try {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(422).json({ errors: errors.array() });
    }

    const { name, phone, email, password, language, role } = req.body;

    if (!phone && !email) {
      return res.status(422).json({ errors: [{ msg: 'Provide phone or email' }] });
    }

    const client = await pool.connect();
    try {
      // Check duplicates
      const dup = await client.query(
        `SELECT id FROM users WHERE phone = $1 OR email = $2`,
        [phone || null, email || null]
      );
      if (dup.rowCount > 0) {
        return res.status(409).json({ error: 'User already exists' });
      }

      const password_hash = await bcrypt.hash(password, SALT_ROUNDS);

      const { rows } = await client.query(
        `INSERT INTO users (name, phone, email, password_hash, language, role)
         VALUES ($1, $2, $3, $4, $5, $6)
         RETURNING id, name, phone, email, language, role, is_verified, certificate_url, created_at`,
        [name, phone || null, email || null, password_hash, language || 'en', role || 'recycler']
      );
      const user = rows[0];

      const accessToken  = signAccessToken(user.id);
      const refreshToken = signRefreshToken(user.id);
      await storeRefreshToken(client, user.id, refreshToken);

      return res.status(201).json({ user, accessToken, refreshToken });
    } finally {
      client.release();
    }
  } catch (err) {
    next(err);
  }
};

// ── POST /auth/login ────────────────────────────────────────────────────────
exports.login = async (req, res, next) => {
  try {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(422).json({ errors: errors.array() });
    }

    const { phone, email, password } = req.body;

    if (!phone && !email) {
      return res.status(422).json({ errors: [{ msg: 'Provide phone or email' }] });
    }

    const { rows } = await pool.query(
      `SELECT * FROM users WHERE phone = $1 OR email = $2`,
      [phone || null, email || null]
    );

    if (rows.length === 0) {
      return res.status(401).json({ error: 'Invalid credentials' });
    }

    const user = rows[0];
    const match = await bcrypt.compare(password, user.password_hash);
    if (!match) {
      return res.status(401).json({ error: 'Invalid credentials' });
    }

    const client = await pool.connect();
    try {
      const accessToken  = signAccessToken(user.id);
      const refreshToken = signRefreshToken(user.id);
      await storeRefreshToken(client, user.id, refreshToken);

      const { password_hash: _, ...safeUser } = user;
      return res.json({ user: safeUser, accessToken, refreshToken });
    } finally {
      client.release();
    }
  } catch (err) {
    next(err);
  }
};

// ── POST /auth/refresh ──────────────────────────────────────────────────────
exports.refresh = async (req, res, next) => {
  try {
    const { refreshToken } = req.body;
    if (!refreshToken) {
      return res.status(400).json({ error: 'Refresh token required' });
    }

    let decoded;
    try {
      decoded = jwt.verify(refreshToken, process.env.JWT_REFRESH_SECRET);
    } catch {
      return res.status(401).json({ error: 'Invalid or expired refresh token' });
    }

    const tokenHash = hashToken(refreshToken);
    const client = await pool.connect();
    try {
      const { rows } = await client.query(
        `SELECT * FROM refresh_tokens
         WHERE token_hash = $1 AND expires_at > NOW()`,
        [tokenHash]
      );

      if (rows.length === 0) {
        return res.status(401).json({ error: 'Refresh token revoked or expired' });
      }

      // Rotate: delete old, issue new
      await client.query(`DELETE FROM refresh_tokens WHERE token_hash = $1`, [tokenHash]);

      const newAccessToken  = signAccessToken(decoded.sub);
      const newRefreshToken = signRefreshToken(decoded.sub);
      await storeRefreshToken(client, decoded.sub, newRefreshToken);

      return res.json({ accessToken: newAccessToken, refreshToken: newRefreshToken });
    } finally {
      client.release();
    }
  } catch (err) {
    next(err);
  }
};

// ── POST /auth/logout ───────────────────────────────────────────────────────
exports.logout = async (req, res, next) => {
  try {
    const { refreshToken } = req.body;
    if (refreshToken) {
      const tokenHash = hashToken(refreshToken);
      await pool.query(`DELETE FROM refresh_tokens WHERE token_hash = $1`, [tokenHash]);
    }
    return res.json({ message: 'Logged out' });
  } catch (err) {
    next(err);
  }
};
