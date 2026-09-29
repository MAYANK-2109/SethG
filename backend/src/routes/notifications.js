const router = require('express').Router();
const authenticate = require('../middleware/authenticate');
const pool = require('../db/pool');

router.use(authenticate);

// GET /notifications — get user notifications
router.get('/', async (req, res, next) => {
  try {
    const { rows } = await pool.query(
      `SELECT id, title, message, type, reference_id, is_read, created_at
       FROM notifications
       WHERE user_id = $1
       ORDER BY created_at DESC
       LIMIT 50`,
      [req.userId]
    );

    const { rows: [{ count }] } = await pool.query(
      `SELECT COUNT(*)::int as count FROM notifications WHERE user_id = $1 AND is_read = false`,
      [req.userId]
    );

    res.json({ notifications: rows, unread_count: count });
  } catch (err) {
    next(err);
  }
});

// POST /notifications/:id/read — mark notification read
router.post('/:id/read', async (req, res, next) => {
  try {
    const { id } = req.params;
    await pool.query(
      `UPDATE notifications SET is_read = true WHERE id = $1 AND user_id = $2`,
      [id, req.userId]
    );
    res.json({ message: 'Notification marked as read' });
  } catch (err) {
    next(err);
  }
});

// POST /notifications/read-all — mark all as read
router.post('/read-all', async (req, res, next) => {
  try {
    await pool.query(
      `UPDATE notifications SET is_read = true WHERE user_id = $1`,
      [req.userId]
    );
    res.json({ message: 'All notifications marked as read' });
  } catch (err) {
    next(err);
  }
});

module.exports = router;
