const crypto = require('crypto');
const { body, validationResult } = require('express-validator');
const pool = require('../db/pool');
const { recyclersForLot } = require('../lib/matching');
const { planTrips } = require('../lib/pooling');
const { MAX_MATCH_KM, haversineSql } = require('../lib/geo');

const CATEGORIES = ['CABLE', 'CHARGER', 'PCB', 'MOBILE', 'BATTERY', 'MOTOR',
  'SWITCH', 'LCD', 'CRT', 'PLASTIC', 'OTHER'];

const otpHash = (lotId, otp) => crypto.createHash('sha256').update(`${lotId}:${otp}`).digest('hex');

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
  delete lot.otp_hash;
  return { ...lot, offers, handover: handover || null };
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

    await pool.query(`UPDATE users SET lat = $1, lon = $2 WHERE id = $3`, [l.lat, l.lon, req.userId]);
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

// ── POST /lots/:id/offers/:offerId/accept — locks the price, issues the OTP ─
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

    // 6-digit handover code. Only its hash is stored; the collector's phone keeps
    // the code and shows it to the driver at pickup (works offline).
    const otp = String(crypto.randomInt(0, 1_000_000)).padStart(6, '0');
    await client.query(
      `UPDATE lots SET status = 'ACCEPTED', accepted_offer_id = $1, otp_hash = $2 WHERE id = $3`,
      [offer.id, otpHash(lot.id, otp), lot.id]);
    await client.query(`UPDATE offers SET status = 'ACCEPTED' WHERE id = $1`, [offer.id]);
    await client.query(
      `UPDATE offers SET status = 'REJECTED' WHERE lot_id = $1 AND id <> $2`, [lot.id, offer.id]);
    await client.query('COMMIT');

    const { rows: hubs } = await pool.query(
      `SELECT id, name, ${haversineSql('$1::float8', '$2::float8', 'lat', 'lon')} AS distance_km
       FROM hubs ORDER BY distance_km LIMIT 5`, [lot.lat, lot.lon]);
    return res.json({
      lot: await collectorLotView(pool, lot.id, req.userId),
      handover_otp: otp,
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
