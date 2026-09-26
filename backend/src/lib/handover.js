const crypto = require('crypto');

// Handover code: the recycler's phone shows it, the vendor types it in to confirm.
// Derived from the lot + accepted offer, so the server never stores the code itself;
// the vendor's phone gets only its hash and can check it offline.
const SECRET = process.env.OTP_SECRET || process.env.JWT_ACCESS_SECRET || 'dev-otp-secret';

const otpFor = (lotId, offerId) => {
  const mac = crypto.createHmac('sha256', SECRET).update(`handover:${lotId}:${offerId}`).digest();
  return String(mac.readUInt32BE(0) % 1_000_000).padStart(6, '0');
};

const otpHash = (lotId, otp) => crypto.createHash('sha256').update(`${lotId}:${otp}`).digest('hex');

const otpMatches = (lotId, offerId, given) =>
  crypto.timingSafeEqual(Buffer.from(otpHash(lotId, otpFor(lotId, offerId)), 'hex'),
    Buffer.from(otpHash(lotId, given), 'hex'));

/**
 * Completes a handover once both halves are in: the recycler's record (weight,
 * photos, GPS) and the vendor's code confirmation. Whichever arrives second calls this.
 * Pays the vendor on the measured weight. Runs inside the caller's transaction.
 */
async function finalize(client, lot, handover, confirmedAt) {
  await client.query(`UPDATE handovers SET confirmed_at = $1 WHERE id = $2`, [confirmedAt, handover.id]);
  await client.query(`UPDATE lots SET status = 'HANDED_OVER' WHERE id = $1`, [lot.id]);
  await client.query(
    `INSERT INTO earnings (user_id, amount, material, weight_kg, note) VALUES ($1, $2, $3, $4, $5)`,
    [lot.collector_id, handover.final_amount, lot.category.toLowerCase(), handover.actual_weight_kg,
     `${handover.id} · lot ${lot.id}`]);
}

module.exports = { otpFor, otpHash, otpMatches, finalize };
