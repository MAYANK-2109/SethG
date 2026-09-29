const pool = require('../db/pool');

/**
 * Creates an in-app notification for a user.
 */
async function createNotification(userId, title, message, type = 'GENERAL', referenceId = null) {
  try {
    const { rows } = await pool.query(
      `INSERT INTO notifications (user_id, title, message, type, reference_id)
       VALUES ($1, $2, $3, $4, $5)
       RETURNING id, user_id, title, message, type, reference_id, is_read, created_at`,
      [userId, title, message, type, referenceId]
    );
    return rows[0];
  } catch (err) {
    console.error('Error creating notification:', err);
    return null;
  }
}

/**
 * Notify all members of a pool (admin + collectors with lots in pool).
 */
async function notifyPoolMembers(poolId, title, message, type = 'POOL', excludeUserId = null) {
  try {
    const { rows: members } = await pool.query(
      `SELECT DISTINCT user_id FROM (
         SELECT admin_id AS user_id FROM vendor_pools WHERE id = $1
         UNION
         SELECT collector_id AS user_id FROM lots WHERE vendor_pool_id = $1
       ) m WHERE user_id IS NOT NULL AND ($2::uuid IS NULL OR user_id != $2::uuid)`,
      [poolId, excludeUserId]
    );
    for (const member of members) {
      await createNotification(member.user_id, title, message, type, poolId);
    }
  } catch (err) {
    console.error('Error notifying pool members:', err);
  }
}

module.exports = { createNotification, notifyPoolMembers };
