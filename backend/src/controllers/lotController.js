const { body, validationResult } = require('express-validator');
const pool = require('../db/pool');
const { recyclersForLot } = require('../lib/matching');
const { planTrips } = require('../lib/pooling');
const { MAX_MATCH_KM, haversineSql } = require('../lib/geo');
const { otpFor, otpHash, otpMatches, finalize } = require('../lib/handover');
const escrow = require('../lib/escrow');
const bcrypt = require('bcrypt');

const CHAT_SALT_ROUNDS = 10;

const CATEGORIES = ['CABLE', 'CHARGER', 'PCB', 'MOBILE', 'BATTERY', 'MOTOR',
  'SWITCH', 'LCD', 'CRT', 'PLASTIC', 'OTHER'];

function invalid(req, res) {
  const errors = validationResult(req);
  if (errors.isEmpty()) return false;
  res.status(422).json({ errors: errors.array() });
  return true;
}

/** Lot as the collector sees it: offers, schedule and handover included. */
async function collectorLotView(db, lotId, collectorId) {
  const { rows: [lot] } = await db.query(
    `SELECT l.*, t.scheduled_date, t.mode AS trip_mode, h.name AS hub_name
     FROM lots l
     LEFT JOIN pickup_trips t ON t.id = l.trip_id
     LEFT JOIN hubs h ON h.id = l.hub_id
     WHERE l.id = $1 AND l.collector_id = $2`,
    [lotId, collectorId]
  );
  if (!lot) return null;
  const { rows: offers } = await db.query(
    `SELECT o.id, o.rate_per_kg, o.pickup_date, o.note, o.distance_km, o.status, o.created_at,
            u.name AS recycler_name, u.vehicle_min_kg,
            ROUND(o.rate_per_kg * $2::numeric) AS offer_total
     FROM offers o JOIN users u ON u.id = o.recycler_id
     WHERE o.lot_id = $1
     ORDER BY o.rate_per_kg DESC, o.distance_km`,
    [lotId, lot.weight_kg]
  );
  const { rows: [handover] } = await db.query(`SELECT * FROM handovers WHERE lot_id = $1`, [lotId]);
  const { rows: disputes } = await db.query(`SELECT * FROM disputes WHERE lot_id = $1 ORDER BY created_at DESC`, [lotId]);
  delete lot.otp_hash;
  // Hash only: lets the vendor's phone check the recycler's code offline
  lot.handover_otp_hash = lot.accepted_offer_id ? otpHash(lot.id, otpFor(lot.id, lot.accepted_offer_id)) : null;
  return { ...lot, offers, handover: handover || null, disputes: disputes || [] };
}

// ── POST /lots — sync a lot created offline on the phone (idempotent) ──────
exports.syncLotValidation = [
  body('id').matches(/^SG-\d{6}-[A-Z0-9]{4}$/).withMessage('Invalid lot id'),
  body('category').isIn(CATEGORIES),
  body('weight_kg').isFloat({ gt: 0, max: 100000 }),
  body('estimate_low').isInt({ min: 0 }),
  body('estimate_high').isInt({ min: 0 }),
  body('lat').isFloat({ min: -90, max: 90 }),
  body('lon').isFloat({ min: -180, max: 180 }),
  body('photo_hashes').optional().isArray({ max: 3 }),
  body('price_region').optional({ nullable: true }).isString(),
];

exports.syncLot = async (req, res, next) => {
  if (invalid(req, res)) return;
  try {
    const l = req.body;
    const { rows: [lot] } = await pool.query(
      `INSERT INTO lots (id, collector_id, category, weight_kg, estimate_low, estimate_high,
                         price_region, lat, lon, photo_hashes)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)
       ON CONFLICT (id) DO UPDATE SET id = lots.id          -- re-sync is a no-op
       RETURNING id, collector_id, category, lat, lon`,
      [l.id, req.userId, l.category, l.weight_kg, l.estimate_low, l.estimate_high,
       l.price_region || null, l.lat, l.lon, l.photo_hashes || []]
    );
    if (lot.collector_id !== req.userId) return res.status(409).json({ error: 'Lot id already used' });

    await pool.query(
      `UPDATE users SET lat = $1, lon = $2,
              geom = ST_SetSRID(ST_MakePoint($2, $1), 4326) WHERE id = $3`,
      [l.lat, l.lon, req.userId]
    ).catch(() => pool.query(`UPDATE users SET lat = $1, lon = $2 WHERE id = $3`, [l.lat, l.lon, req.userId]));

    const match = await recyclersForLot(pool, lot);
    return res.status(201).json({
      lot: await collectorLotView(pool, lot.id, req.userId),
      matched_recyclers: match.recyclers.length,
      match_radius_km: match.radiusKm,
    });
  } catch (err) {
    next(err);
  }
};

// ── GET /lots/mine ──────────────────────────────────────────────────────────
exports.myLots = async (req, res, next) => {
  try {
    const { rows } = await pool.query(
      `SELECT id FROM lots WHERE collector_id = $1 ORDER BY created_at DESC LIMIT 100`, [req.userId]);
    const lots = [];
    for (const { id } of rows) lots.push(await collectorLotView(pool, id, req.userId));
    return res.json({ lots });
  } catch (err) {
    next(err);
  }
};

// ── POST /lots/:id/offers/:offerId/accept — locks the price, creates escrow hold ──
exports.acceptOffer = async (req, res, next) => {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const { rows: [lot] } = await client.query(
      `SELECT * FROM lots WHERE id = $1 AND collector_id = $2 FOR UPDATE`, [req.params.id, req.userId]);
    if (!lot) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'Lot not found' }); }
    if (lot.status !== 'LISTED') {
      await client.query('ROLLBACK');
      return res.status(409).json({ error: `Lot is already ${lot.status}` });
    }
    const { rows: [offer] } = await client.query(
      `SELECT o.*, u.vehicle_min_kg FROM offers o JOIN users u ON u.id = o.recycler_id
       WHERE o.id = $1 AND o.lot_id = $2 AND o.status = 'PENDING'`, [req.params.offerId, lot.id]);
    if (!offer) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'Offer not found' }); }

    // Create Escrow Hold via Razorpay Sandbox adapter
    const offerTotal = parseFloat(offer.rate_per_kg) * parseFloat(lot.weight_kg);
    const escrowHold = await escrow.createEscrowHold(lot.id, offerTotal, offer.recycler_id);

    await client.query(
      `UPDATE lots SET status = 'ACCEPTED', accepted_offer_id = $1,
              escrow_status = $2, escrow_tx_id = $3, escrow_amount = $4
       WHERE id = $5`,
      [offer.id, escrowHold.status, escrowHold.escrowTxId, escrowHold.amount, lot.id]
    );
    await client.query(`UPDATE offers SET status = 'ACCEPTED' WHERE id = $1`, [offer.id]);
    await client.query(
      `UPDATE offers SET status = 'REJECTED' WHERE lot_id = $1 AND id <> $2`, [lot.id, offer.id]);
    await client.query('COMMIT');

    const { rows: hubs } = await pool.query(
      `SELECT id, name, ${haversineSql('$1::float8', '$2::float8', 'lat', 'lon')} AS distance_km
       FROM hubs ORDER BY distance_km LIMIT 5`, [lot.lat, lot.lon]);
    return res.json({
      lot: await collectorLotView(pool, lot.id, req.userId),
      escrow: escrowHold,
      transport_options: {
        pickup_allowed: Number(lot.weight_kg) >= Number(offer.vehicle_min_kg),
        vehicle_min_kg: Number(offer.vehicle_min_kg),
        hubs: hubs.filter((h) => h.distance_km <= MAX_MATCH_KM),
      },
    });
  } catch (err) {
    await client.query('ROLLBACK').catch(() => {});
    next(err);
  } finally {
    client.release();
  }
};

// ── POST /lots/:id/transport — PICKUP | POOLED | HUB ───────────────────────
exports.transportValidation = [
  body('mode').isIn(['PICKUP', 'POOLED', 'HUB']),
  body('hub_id').if(body('mode').equals('HUB')).isUUID(),
];

exports.chooseTransport = async (req, res, next) => {
  if (invalid(req, res)) return;
  try {
    const { rows: [lot] } = await pool.query(
      `SELECT l.*, o.recycler_id, u.vehicle_min_kg
       FROM lots l JOIN offers o ON o.id = l.accepted_offer_id JOIN users u ON u.id = o.recycler_id
       WHERE l.id = $1 AND l.collector_id = $2`, [req.params.id, req.userId]);
    if (!lot) return res.status(404).json({ error: 'Accepted lot not found' });
    if (lot.status !== 'ACCEPTED' || lot.trip_id) {
      return res.status(409).json({ error: 'Transport already scheduled' });
    }
    const { mode, hub_id: hubId } = req.body;
    if (mode === 'PICKUP' && Number(lot.weight_kg) < Number(lot.vehicle_min_kg)) {
      return res.status(422).json({
        error: `Direct pickup needs at least ${lot.vehicle_min_kg} kg — choose pooled pickup or a hub`,
      });
    }
    if (mode === 'HUB') {
      const { rows: [hub] } = await pool.query(
        `SELECT id FROM hubs WHERE id = $1 AND ${haversineSql('lat', 'lon', '$2::float8', '$3::float8')} <= $4`,
        [hubId, lot.lat, lot.lon, MAX_MATCH_KM]);
      if (!hub) return res.status(422).json({ error: `Hub not found within ${MAX_MATCH_KM} km` });
    }
    await pool.query(`UPDATE lots SET transport_mode = $1, hub_id = $2 WHERE id = $3`,
      [mode, mode === 'HUB' ? hubId : null, lot.id]);
    await planTrips(pool, lot.recycler_id);
    return res.json({ lot: await collectorLotView(pool, lot.id, req.userId) });
  } catch (err) {
    next(err);
  }
};

// ── POST /lots/:id/confirm — vendor enters the code shown on the recycler's phone ──
// The phone checks the code offline against the hash and sends it here at sync.
exports.confirmValidation = [
  body('otp').matches(/^\d{6}$/),
  body('confirmed_at').isISO8601(),
];

exports.confirmHandover = async (req, res, next) => {
  if (invalid(req, res)) return;
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const { rows: [lot] } = await client.query(
      `SELECT * FROM lots WHERE id = $1 AND collector_id = $2 FOR UPDATE`, [req.params.id, req.userId]);
    if (!lot || !lot.accepted_offer_id) {
      await client.query('ROLLBACK');
      return res.status(404).json({ error: 'Accepted lot not found' });
    }
    if (!otpMatches(lot.id, lot.accepted_offer_id, req.body.otp)) {
      await client.query('ROLLBACK');
      return res.status(403).json({ error: "Wrong code — enter the code shown on the recycler's phone" });
    }
    if (lot.status !== 'HANDED_OVER') {
      const confirmedAt = lot.vendor_confirmed_at || req.body.confirmed_at;
      await client.query(`UPDATE lots SET vendor_confirmed_at = $1 WHERE id = $2`, [confirmedAt, lot.id]);
      const { rows: [handover] } = await client.query(`SELECT * FROM handovers WHERE lot_id = $1`, [lot.id]);
      if (handover) {
        await finalize(client, lot, handover, confirmedAt);
        // Release Digital Escrow to vendor if held
        if (lot.escrow_tx_id && lot.escrow_status === 'HELD') {
          const escrowResult = await escrow.releaseEscrow(lot.escrow_tx_id, lot.collector_id, handover.final_amount);
          await client.query(`UPDATE lots SET escrow_status = $1 WHERE id = $2`, [escrowResult.status, lot.id]);
        }
      }
    }
    await client.query('COMMIT');
    return res.json({ lot: await collectorLotView(pool, lot.id, req.userId) });
  } catch (err) {
    await client.query('ROLLBACK').catch(() => {});
    next(err);
  } finally {
    client.release();
  }
};

// ── Disputes Management ──────────────────────────────────────────────────────
exports.raiseDisputeValidation = [
  body('reason').trim().notEmpty().withMessage('Reason for dispute is required'),
];

exports.raiseDispute = async (req, res, next) => {
  if (invalid(req, res)) return;
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const { rows: [lot] } = await client.query(`SELECT * FROM lots WHERE id = $1 FOR UPDATE`, [req.params.id]);
    if (!lot) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'Lot not found' }); }

    const { rows: [handover] } = await client.query(`SELECT * FROM handovers WHERE lot_id = $1`, [lot.id]);
    const declaredWeight = lot.weight_kg;
    const actualWeight = handover ? handover.actual_weight_kg : null;

    const { rows: [dispute] } = await client.query(
      `INSERT INTO disputes (lot_id, raised_by, reason, declared_weight_kg, actual_weight_kg)
       VALUES ($1, $2, $3, $4, $5) RETURNING *`,
      [lot.id, req.userId, req.body.reason, declaredWeight, actualWeight]
    );

    // Freeze digital escrow hold
    if (lot.escrow_tx_id) {
      await escrow.holdEscrowForDispute(lot.escrow_tx_id, dispute.id);
      await client.query(`UPDATE lots SET escrow_status = 'DISPUTED', status = 'DISPUTED' WHERE id = $1`, [lot.id]);
    } else {
      await client.query(`UPDATE lots SET status = 'DISPUTED' WHERE id = $1`, [lot.id]);
    }

    await client.query('COMMIT');
    return res.status(201).json({ message: 'Dispute raised successfully. Escrow funds frozen.', dispute });
  } catch (err) {
    await client.query('ROLLBACK').catch(() => {});
    next(err);
  } finally {
    client.release();
  }
};

exports.getDispute = async (req, res, next) => {
  try {
    const { rows } = await pool.query(
      `SELECT d.*, u.name AS raised_by_name FROM disputes d
       JOIN users u ON u.id = d.raised_by
       WHERE d.lot_id = $1 ORDER BY d.created_at DESC`,
      [req.params.id]
    );
    return res.json({ disputes: rows });
  } catch (err) {
    next(err);
  }
};

exports.resolveDispute = async (req, res, next) => {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const { rows: [dispute] } = await client.query(`SELECT * FROM disputes WHERE id = $1 FOR UPDATE`, [req.params.disputeId]);
    if (!dispute) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'Dispute not found' }); }

    const { status = 'RESOLVED', resolution_notes = 'Dispute resolved by mutual agreement' } = req.body;
    await client.query(
      `UPDATE disputes SET status = $1, resolution_notes = $2 WHERE id = $3`,
      [status, resolution_notes, dispute.id]
    );

    const { rows: [lot] } = await client.query(`SELECT * FROM lots WHERE id = $1`, [dispute.lot_id]);
    if (lot && lot.escrow_tx_id) {
      const { rows: [handover] } = await client.query(`SELECT * FROM handovers WHERE lot_id = $1`, [lot.id]);
      const payoutAmount = handover ? handover.final_amount : lot.escrow_amount;
      await escrow.releaseEscrow(lot.escrow_tx_id, lot.collector_id, payoutAmount);
      await client.query(`UPDATE lots SET escrow_status = 'RELEASED', status = 'HANDED_OVER' WHERE id = $1`, [lot.id]);
    }

    await client.query('COMMIT');
    return res.json({ message: 'Dispute resolved and escrow released', dispute_id: dispute.id });
  } catch (err) {
    await client.query('ROLLBACK').catch(() => {});
    next(err);
  } finally {
    client.release();
  }
};

exports.getMessages = async (req, res, next) => {
  try {
    const { id } = req.params;
    const userId = req.userId;

    // Security: only the lot's collector or an invited recycler (who has made an offer) may read messages
    const { rows: [lot] } = await pool.query(
      `SELECT l.id, l.collector_id,
              (SELECT recycler_id FROM offers WHERE lot_id = l.id AND status = 'ACCEPTED' LIMIT 1) AS accepted_recycler_id
       FROM lots l WHERE l.id = $1`, [id]
    );
    if (!lot) return res.status(404).json({ error: 'Lot not found' });
    const isParty = lot.collector_id === userId || lot.accepted_recycler_id === userId;
    if (!isParty) {
      // Allow any recycler who submitted an offer to read (pre-acceptance chat)
      const { rows: [offer] } = await pool.query(
        `SELECT id FROM offers WHERE lot_id = $1 AND recycler_id = $2 LIMIT 1`, [id, userId]
      );
      if (!offer) return res.status(403).json({ error: 'Access denied' });
    }

    const { rows } = await pool.query(
      `SELECT cm.id, cm.lot_id, cm.sender_id, COALESCE(cm.content, cm.content_hash) AS msg_content, cm.created_at, u.name AS sender_name
       FROM chat_messages cm
       JOIN users u ON u.id = cm.sender_id
       WHERE cm.lot_id = $1 ORDER BY cm.created_at ASC`,
      [id]
    );

    res.json(rows.map(r => ({
      id: r.id,
      lot_id: r.lot_id,
      sender_id: r.sender_id,
      sender_name: r.sender_name,
      content: r.msg_content,
      created_at: r.created_at
    })));
  } catch (err) {
    next(err);
  }
};

exports.postMessage = async (req, res, next) => {
  try {
    const { id } = req.params;
    const { content } = req.body;
    const sender_id = req.userId;

    if (!content || content.trim() === '') return res.status(422).json({ error: 'Message content required' });

    // Determine the other party
    const { rows: [lot] } = await pool.query(
      `SELECT l.collector_id,
              (SELECT recycler_id FROM offers WHERE lot_id = l.id AND status = 'ACCEPTED' LIMIT 1) AS accepted_recycler_id
       FROM lots l WHERE l.id = $1`, [id]
    );
    if (!lot) return res.status(404).json({ error: 'Lot not found' });

    // A vendor/collector CANNOT initiate — they can only reply if a recycler has already messaged
    const isVendor = lot.collector_id === sender_id;
    if (isVendor) {
      const { rows: [firstMsg] } = await pool.query(
        `SELECT id FROM chat_messages WHERE lot_id = $1 LIMIT 1`, [id]
      );
      if (!firstMsg) {
        return res.status(403).json({ error: 'Vendor cannot initiate a chat. Recycler must message first.' });
      }
    }

    // Hash the message in bcrypt form for security/auditing
    const content_hash = await bcrypt.hash(content.trim(), CHAT_SALT_ROUNDS);
    const trimmed = content.trim();

    const { rows } = await pool.query(
      `INSERT INTO chat_messages (lot_id, sender_id, content, content_hash)
       VALUES ($1, $2, $3, $4)
       RETURNING id, lot_id, sender_id, content, created_at`,
      [id, sender_id, trimmed, content_hash]
    );
    const msg = rows[0];

    // Broadcast via Socket.io if available
    const io = req.app.get('io');
    if (io) io.to(id).emit('new_message', { ...msg, sender_name: req.userName });

    res.status(201).json(msg);
  } catch (err) {
    next(err);
  }
};

/** GET /user/chats — returns all lot conversations the calling user is party to */
exports.getMyChats = async (req, res, next) => {
  try {
    const userId = req.userId;
    const { rows } = await pool.query(
      `SELECT DISTINCT ON (l.id)
         l.id   AS lot_id,
         l.category,
         l.status,
         CASE WHEN l.collector_id = $1 THEN 'vendor' ELSE 'recycler' END AS my_role,
         CASE WHEN l.collector_id = $1 THEN
           (SELECT u.name FROM users u JOIN offers o ON o.recycler_id = u.id WHERE o.lot_id = l.id ORDER BY o.created_at LIMIT 1)
         ELSE
           (SELECT u.name FROM users u WHERE u.id = l.collector_id LIMIT 1)
         END AS other_name,
         CASE WHEN l.collector_id = $1 THEN
           (SELECT o.recycler_id FROM offers o WHERE o.lot_id = l.id ORDER BY o.created_at LIMIT 1)
         ELSE
           l.collector_id
         END AS other_id,
         (SELECT COALESCE(cm.content, cm.content_hash, '') FROM chat_messages cm WHERE cm.lot_id = l.id ORDER BY cm.created_at DESC LIMIT 1) AS last_message,
         (SELECT cm.created_at   FROM chat_messages cm WHERE cm.lot_id = l.id ORDER BY cm.created_at DESC LIMIT 1) AS last_message_at,
         (SELECT cm.sender_id    FROM chat_messages cm WHERE cm.lot_id = l.id ORDER BY cm.created_at DESC LIMIT 1) AS last_sender_id,
         (SELECT COUNT(*) FROM chat_messages cm WHERE cm.lot_id = l.id) AS message_count
       FROM lots l
       WHERE l.id IN (
         SELECT DISTINCT lot_id FROM chat_messages
       )
       AND (
         l.collector_id = $1
         OR EXISTS (SELECT 1 FROM offers o WHERE o.lot_id = l.id AND o.recycler_id = $1)
       )
       ORDER BY l.id, last_message_at DESC NULLS LAST`,
      [userId]
    );
    res.json({ chats: rows });
  } catch (err) {
    next(err);
  }
};
