const { body, validationResult } = require('express-validator');
const bcrypt = require('bcrypt');
const pool = require('../db/pool');

const SALT_ROUNDS = 12;

// ── GET /user/profile ───────────────────────────────────────────────────────
exports.getProfile = async (req, res, next) => {
  try {
    const { rows } = await pool.query(
      `SELECT id, name, phone, email, photo_url, language, role, certificate_url, is_verified, created_at
       FROM users WHERE id = $1`,
      [req.userId]
    );
    if (rows.length === 0) {
      return res.status(404).json({ error: 'User not found' });
    }
    return res.json({ user: rows[0] });
  } catch (err) {
    next(err);
  }
};

// ── Validation ───────────────────────────────────────────────────────────────
exports.updateProfileValidation = [
  body('name').optional().trim().notEmpty().withMessage('Name cannot be empty'),
  body('phone')
    .optional({ nullable: true, checkFalsy: true })
    .isMobilePhone().withMessage('Invalid phone'),
  body('email')
    .optional({ nullable: true, checkFalsy: true })
    .isEmail().normalizeEmail().withMessage('Invalid email'),
  body('language')
    .optional()
    .isIn(['en', 'hi', 'mr', 'te']).withMessage('Unsupported language code'),
  body('newPassword')
    .optional()
    .isLength({ min: 6 }).withMessage('Password must be at least 6 characters'),
  body('currentPassword')
    .if(body('newPassword').exists({ checkFalsy: true }))
    .notEmpty().withMessage('Current password required to change password'),
  body('certificate_url')
    .optional()
    .isString(),
];

// ── PUT /user/profile ───────────────────────────────────────────────────────
exports.updateProfile = async (req, res, next) => {
  try {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(422).json({ errors: errors.array() });
    }

    const { name, phone, email, language, photo_url, newPassword, currentPassword, certificate_url } = req.body;

    const client = await pool.connect();
    try {
      const { rows: existing } = await client.query(
        `SELECT * FROM users WHERE id = $1`,
        [req.userId]
      );
      if (existing.length === 0) return res.status(404).json({ error: 'User not found' });

      const user = existing[0];

      // Handle password change
      let password_hash = user.password_hash;
      if (newPassword) {
        const match = await bcrypt.compare(currentPassword, user.password_hash);
        if (!match) return res.status(401).json({ error: 'Current password incorrect' });
        password_hash = await bcrypt.hash(newPassword, SALT_ROUNDS);
      }

      let is_verified = user.is_verified;
      if (certificate_url && user.role === 'recycler') {
        is_verified = true;
      }

      const { rows } = await client.query(
        `UPDATE users
         SET name          = COALESCE($1, name),
             phone         = COALESCE($2, phone),
             email         = COALESCE($3, email),
             language      = COALESCE($4, language),
             photo_url     = COALESCE($5, photo_url),
             password_hash = $6,
             certificate_url = COALESCE($7, certificate_url),
             is_verified   = $8
         WHERE id = $9
         RETURNING id, name, phone, email, language, photo_url, role, certificate_url, is_verified, updated_at`,
        [
          name       || null,
          phone      || null,
          email      || null,
          language   || null,
          photo_url  || null,
          password_hash,
          certificate_url || null,
          is_verified,
          req.userId,
        ]
      );

      return res.json({ user: rows[0] });
    } finally {
      client.release();
    }
  } catch (err) {
    next(err);
  }
};
