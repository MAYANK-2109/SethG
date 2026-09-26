const pool = require('../db/pool');

// ── Helper ──────────────────────────────────────────────────────────────────
async function sumEarnings(userId, startISO, endISO) {
  const { rows } = await pool.query(
    `SELECT COALESCE(SUM(amount), 0) AS total,
            COUNT(*) AS transaction_count
     FROM earnings
     WHERE user_id = $1
       AND earned_at >= $2
       AND earned_at <  $3`,
    [userId, startISO, endISO]
  );
  return {
    total: parseFloat(rows[0].total),
    transactionCount: parseInt(rows[0].transaction_count, 10),
  };
}

function startOfDay(d) {
  const dt = new Date(d);
  dt.setHours(0, 0, 0, 0);
  return dt;
}

function endOfDay(d) {
  const dt = new Date(d);
  dt.setHours(23, 59, 59, 999);
  return dt;
}

// ── GET /earnings/today ─────────────────────────────────────────────────────
exports.today = async (req, res, next) => {
  try {
    const now = new Date();
    const start = startOfDay(now);
    const end   = endOfDay(now);

    const summary = await sumEarnings(req.userId, start.toISOString(), end.toISOString());

    // Also return recent transactions for the day
    const { rows: transactions } = await pool.query(
      `SELECT id, amount, material, weight_kg, note, earned_at
       FROM earnings
       WHERE user_id = $1
         AND earned_at >= $2
         AND earned_at <  $3
       ORDER BY earned_at DESC
       LIMIT 20`,
      [req.userId, start.toISOString(), end.toISOString()]
    );

    return res.json({ period: 'today', ...summary, transactions });
  } catch (err) {
    next(err);
  }
};

// ── GET /earnings/weekly ────────────────────────────────────────────────────
exports.weekly = async (req, res, next) => {
  try {
    const now   = new Date();
    const day   = now.getDay(); // 0=Sun … 6=Sat
    const start = new Date(now);
    start.setDate(now.getDate() - day);
    start.setHours(0, 0, 0, 0);

    const end = endOfDay(now);

    const summary = await sumEarnings(req.userId, start.toISOString(), end.toISOString());

    // Daily breakdown for the current week
    const { rows: dailyBreakdown } = await pool.query(
      `SELECT DATE(earned_at AT TIME ZONE 'UTC') AS date,
              COALESCE(SUM(amount), 0)             AS total
       FROM earnings
       WHERE user_id = $1
         AND earned_at >= $2
         AND earned_at <  $3
       GROUP BY DATE(earned_at AT TIME ZONE 'UTC')
       ORDER BY date`,
      [req.userId, start.toISOString(), end.toISOString()]
    );

    return res.json({ period: 'weekly', ...summary, dailyBreakdown });
  } catch (err) {
    next(err);
  }
};

// ── GET /earnings/monthly ───────────────────────────────────────────────────
exports.monthly = async (req, res, next) => {
  try {
    const now   = new Date();
    const start = new Date(now.getFullYear(), now.getMonth(), 1);
    const end   = endOfDay(now);

    const summary = await sumEarnings(req.userId, start.toISOString(), end.toISOString());

    // Weekly breakdown within the month
    const { rows: weeklyBreakdown } = await pool.query(
      `SELECT DATE_TRUNC('week', earned_at AT TIME ZONE 'UTC') AS week_start,
              COALESCE(SUM(amount), 0)                          AS total
       FROM earnings
       WHERE user_id = $1
         AND earned_at >= $2
         AND earned_at <  $3
       GROUP BY week_start
       ORDER BY week_start`,
      [req.userId, start.toISOString(), end.toISOString()]
    );

    return res.json({ period: 'monthly', ...summary, weeklyBreakdown });
  } catch (err) {
    next(err);
  }
};

// ── POST /earnings (add a transaction — optional helper for demo) ────────────
exports.addEarning = async (req, res, next) => {
  try {
    const { amount, material, weight_kg, note, earned_at } = req.body;

    if (amount === undefined || amount < 0) {
      return res.status(422).json({ error: 'Valid amount required' });
    }

    const { rows } = await pool.query(
      `INSERT INTO earnings (user_id, amount, material, weight_kg, note, earned_at)
       VALUES ($1, $2, $3, $4, $5, COALESCE($6::timestamptz, NOW()))
       RETURNING *`,
      [req.userId, amount, material || null, weight_kg || null, note || null, earned_at || null]
    );

    return res.status(201).json({ earning: rows[0] });
  } catch (err) {
    next(err);
  }
};
