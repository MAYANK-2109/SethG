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

const blockchain = require('../services/blockchain');

/**
 * Completes a handover once both halves are in: the recycler's record (weight,
 * photos, GPS) and the vendor's code confirmation. Whichever arrives second calls this.
 * Pays the vendor on the measured weight and anchors immutable proof on-chain. Runs inside the caller's transaction.
 */
async function finalize(client, lot, handover, confirmedAt) {
  let blockchainResult = null;
  try {
    blockchainResult = await blockchain.recordHandoverAndMintEPR({
      lotId: lot.id,
      handoverId: handover.id,
      category: lot.category,
      actualWeightKg: handover.actual_weight_kg,
      photoHashes: handover.photo_hashes || [],
      lat: handover.lat,
      lon: handover.lon,
      capturedAt: handover.captured_at || confirmedAt,
      collectorId: lot.collector_id,
      recyclerId: handover.recycler_id,
      finalAmount: handover.final_amount
    });
  } catch (bcErr) {
    console.error('⚠️ Blockchain audit logging failed (non-blocking):', bcErr.message);
  }

  const txHash = blockchainResult ? blockchainResult.txHash : null;
  const proofHash = blockchainResult ? blockchainResult.proofHash : null;
  const tokenId = blockchainResult ? blockchainResult.tokenId : null;

  await client.query(
    `UPDATE handovers SET confirmed_at = $1, blockchain_proof_hash = $2, blockchain_tx_hash = $3 WHERE id = $4`,
    [confirmedAt, proofHash, txHash, handover.id]
  );
  await client.query(
    `UPDATE lots SET status = 'HANDED_OVER', blockchain_tx_hash = $1, blockchain_proof_hash = $2, epr_token_id = $3 WHERE id = $4`,
    [txHash, proofHash, tokenId, lot.id]
  );
  await client.query(
    `INSERT INTO earnings (user_id, amount, material, weight_kg, note) VALUES ($1, $2, $3, $4, $5)`,
    [lot.collector_id, handover.final_amount, lot.category.toLowerCase(), handover.actual_weight_kg,
     `${handover.id} · lot ${lot.id}`]);

  return blockchainResult;
}

module.exports = { otpFor, otpHash, otpMatches, finalize };
