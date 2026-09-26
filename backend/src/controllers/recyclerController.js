const { body, query, validationResult } = require('express-validator');
const pool = require('../db/pool');
const { ELIGIBLE_RECYCLER, ACCEPTS, LOT_RADIUS_SQL } = require('../lib/matching');
const { haversineKm, haversineSql } = require('../lib/geo');
const { planTrips } = require('../lib/pooling');
const { otpFor, finalize } = require('../lib/handover');

const CATEGORIES = ['CABLE', 'CHARGER', 'PCB', 'MOBILE', 'BATTERY', 'MOTOR',
  'SWITCH', 'LCD', 'CRT', 'PLASTIC', 'OTHER'];
const WEIGHT_FLAG_RATIO = 0.15;

function invalid(req, res) {
  const errors = validationResult(req);
  if (errors.isEmpty()) return false;
  res.status(422).json({ errors: errors.array() });
  return true;
}

function requireReady(req, res) {
  if (!req.user.is_verified) {
    res.status(403).json({ error: 'Upload your authorization certificate to get verified first' });
    return false;
  }
  if (req.user.lat == null || req.user.lon == null) {
    res.status(409).json({ error: 'Set your facility location first' });
    return false;
  }
  return true;
}

// ── PUT /recycler/profile — facility location, materials, vehicle minimum ──
exports.profileValidation = [
  body('lat').isFloat({ min: -90, max: 90 }),
  body('lon').isFloat({ min: -180, max: 180 }),
  body('accepted_materials').optional({ nullable: true }).isArray(),
  body('accepted_materials.*').isIn(CATEGORIES),
  body('vehicle_min_kg').optional().isFloat({ gt: 0, max: 10000 }),
];

exports.updateProfile = async (req, res, next) => {
  if (invalid(req, res)) return;
  try {
    const { lat, lon, accepted_materials: materials, vehicle_min_kg: minKg } = req.body;
    const { rows: [user] } = await pool.query(
      `UPDATE users SET lat = $1, lon = $2,
              accepted_materials = $3,
              vehicle_min_kg = COALESCE($4, vehicle_min_kg)
       WHERE id = $5
       RETURNING id, name, role, is_verified, lat, lon, accepted_materials, vehicle_min_kg`,
      [lat, lon, materials && materials.length ? materials : null, minKg || null, req.userId]);
    return res.json({ recycler: user });
  } catch (err) {
    next(err);
  }
};

// ── GET /recycler/lots/nearby?since=ISO — new lots this recycler may bid on ──
// Collector identity is limited to first name until an offer is accepted.
exports.nearbyValidation = [query('since').optional().isISO8601()];

exports.nearbyLots = async (req, res, next) => {
  if (invalid(req, res) || !requireReady(req, res)) return;
  try {
    const dist = haversineSql('l.lat', 'l.lon', 'r.lat', 'r.lon');
    const { rows } = await pool.query(
      `SELECT * FROM (
         SELECT l.id, l.category, l.weight_kg, l.estimate_low, l.estimate_high, l.price_region,
                l.created_at, split_part(c.name, ' ', 1) AS collector_first_name,
                ROUND((${dist})::numeric, 1) AS distance_km,
                ${LOT_RADIUS_SQL} AS match_radius_km,
                (SELECT COUNT(*) FROM offers x WHERE x.lot_id = l.id) AS offer_count,
                my.id AS my_offer_id, my.rate_per_kg AS my_rate_per_kg
         FROM lots l
         JOIN users r ON r.id = $1
         JOIN users c ON c.id = l.collector_id
         LEFT JOIN offers my ON my.lot_id = l.id AND my.recycler_id = r.id
         WHERE l.status = 'LISTED' AND ${ACCEPTS('l', 'r')}
           AND ($2::timestamptz IS NULL OR l.created_at > $2::timestamptz)
       ) m
       WHERE m.distance_km <= m.match_radius_km
       ORDER BY m.created_at DESC
       LIMIT 100`,
      [req.userId, req.query.since || null]);
    return res.json({ lots: rows, server_time: new Date().toISOString() });
  } catch (err) {
    next(err);
  }
};

// ── POST /recycler/lots/:id/offers — "₹22/kg, pickup Thursday" ─────────────
exports.offerValidation = [
  body('rate_per_kg').isFloat({ gt: 0, max: 100000 }),
  body('pickup_date').isISO8601({ strict: true }),
  body('note').optional().isString().isLength({ max: 200 }),
];

exports.makeOffer = async (req, res, next) => {
  if (invalid(req, res) || !requireReady(req, res)) return;
  try {
    const { rows: [lot] } = await pool.query(
      `SELECT l.*, ${LOT_RADIUS_SQL} AS match_radius_km FROM lots l WHERE l.id = $1`, [req.params.id]);
    if (!lot) return res.status(404).json({ error: 'Lot not found' });
    if (lot.status !== 'LISTED') return res.status(409).json({ error: `Lot is already ${lot.status}` });

    const distance = haversineKm(lot.lat, lot.lon, req.user.lat, req.user.lon);
    if (distance > lot.match_radius_km) {
      return res.status(403).json({ error: `Lot is ${distance.toFixed(1)} km away (limit ${lot.match_radius_km} km)` });
    }
    const { rows: [accepts] } = await pool.query(
      `SELECT 1 FROM users r WHERE r.id = $1 AND ${ELIGIBLE_RECYCLER}
         AND (r.accepted_materials IS NULL OR $2 = ANY(r.accepted_materials))`, [req.userId, lot.category]);
    if (!accepts) return res.status(403).json({ error: 'You do not accept this material' });

    const { rate_per_kg: rate, pickup_date: date, note } = req.body;
    const { rows: [offer] } = await pool.query(
      `INSERT INTO offers (lot_id, recycler_id, rate_per_kg, pickup_date, note, distance_km)
       VALUES ($1, $2, $3, $4, $5, $6)
       ON CONFLICT (lot_id, recycler_id)
         DO UPDATE SET rate_per_kg = EXCLUDED.rate_per_kg, pickup_date = EXCLUDED.pickup_date,
                       note = EXCLUDED.note, created_at = NOW()
         WHERE offers.status = 'PENDING'
       RETURNING *`,
      [lot.id, req.userId, rate, date, note || null, distance.toFixed(2)]);
    if (!offer) return res.status(409).json({ error: 'Your offer was already decided' });
    return res.status(201).json({ offer });
  } catch (err) {
    next(err);
  }
};

/** Adds the handover code the vendor must type in; kept on the recycler's phone for offline use. */
function withCode(row, lotId) {
  const { accepted_offer_id: offerId, ...rest } = row;
  return { ...rest, handover_otp: otpFor(lotId, offerId) };
}

// ── GET /recycler/lots/accepted — lots won, with collector contact + place ──
exports.acceptedLots = async (req, res, next) => {
  try {
    const { rows } = await pool.query(
      `SELECT l.id, l.accepted_offer_id, l.category, l.weight_kg, l.status, l.transport_mode, l.lat, l.lon,
              l.trip_id, l.stop_seq, l.slot_start, l.slot_end,
              o.rate_per_kg, o.pickup_date, ROUND(o.rate_per_kg * l.weight_kg) AS expected_amount,
              c.name AS collector_name, c.phone AS collector_phone, h.name AS hub_name
       FROM lots l
       JOIN offers o ON o.id = l.accepted_offer_id
       JOIN users c ON c.id = l.collector_id
       LEFT JOIN hubs h ON h.id = l.hub_id
       WHERE o.recycler_id = $1 AND l.status IN ('ACCEPTED', 'SCHEDULED', 'WEIGHED')
       ORDER BY l.slot_start NULLS LAST, l.updated_at DESC`, [req.userId]);
    return res.json({ lots: rows.map((l) => withCode(l, l.id)) });
  } catch (err) {
    next(err);
  }
};

// ── GET /recycler/trips — plans pending lots first, then lists trips ───────
exports.trips = async (req, res, next) => {
  try {
    await planTrips(pool, req.userId);
    const { rows: trips } = await pool.query(
      `SELECT t.*, h.name AS hub_name, h.lat AS hub_lat, h.lon AS hub_lon
       FROM pickup_trips t LEFT JOIN hubs h ON h.id = t.hub_id
       WHERE t.recycler_id = $1 AND t.status = 'SCHEDULED'
       ORDER BY t.scheduled_date, t.created_at`, [req.userId]);
    for (const trip of trips) {
      const { rows: stops } = await pool.query(
        `SELECT l.id AS lot_id, l.accepted_offer_id, l.stop_seq, l.slot_start, l.slot_end, l.category, l.weight_kg, l.status,
                l.lat, l.lon, c.name AS collector_name, c.phone AS collector_phone
         FROM lots l JOIN users c ON c.id = l.collector_id
         WHERE l.trip_id = $1 ORDER BY l.stop_seq`, [trip.id]);
      trip.stops = stops.map((st) => withCode(st, st.lot_id));
    }
    return res.json({ trips });
  } catch (err) {
    next(err);
  }
};

// ── POST /recycler/lots/:id/handover — weigh, photograph, GPS ──────────────
// Then the vendor confirms by typing the code shown on this recycler's phone
// (POST /lots/:id/confirm). Payment is booked once both are in, in either order.
exports.handoverValidation = [
  body('actual_weight_kg').isFloat({ gt: 0, max: 100000 }),
  body('lat').isFloat({ min: -90, max: 90 }),
  body('lon').isFloat({ min: -180, max: 180 }),
  body('captured_at').isISO8601(),
  body('photo_hashes').isArray({ min: 1, max: 5 }),
  body('photo_hashes.*').matches(/^[a-f0-9]{64}$/),
];

exports.handover = async (req, res, next) => {
  if (invalid(req, res)) return;
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const { rows: [lot] } = await client.query(
      `SELECT l.*, o.rate_per_kg, o.recycler_id
       FROM lots l JOIN offers o ON o.id = l.accepted_offer_id
       WHERE l.id = $1 FOR UPDATE OF l`, [req.params.id]);
    if (!lot || lot.recycler_id !== req.userId) {
      await client.query('ROLLBACK');
      return res.status(404).json({ error: 'Lot not found in your accepted lots' });
    }
    if (!['ACCEPTED', 'SCHEDULED'].includes(lot.status)) {
      await client.query('ROLLBACK');
      return res.status(409).json({ error: `Lot is ${lot.status}` });
    }
    const { actual_weight_kg: actual, lat, lon, captured_at: capturedAt, photo_hashes: photos } = req.body;
    const declared = Number(lot.weight_kg);
    const flagged = Math.abs(actual - declared) / declared > WEIGHT_FLAG_RATIO;
    const amount = Math.round(actual * Number(lot.rate_per_kg) * 100) / 100;   // always on the measured weight
    const { rows: [{ n }] } = await client.query(`SELECT nextval('handover_seq') AS n`);
    const handoverId = `HR-${new Date().getFullYear()}-${String(n).padStart(6, '0')}`;

    const { rows: [handover] } = await client.query(
      `INSERT INTO handovers (id, lot_id, recycler_id, declared_weight_kg, actual_weight_kg, weight_flagged,
                              rate_per_kg, final_amount, lat, lon, distance_from_lot_km, photo_hashes, captured_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13) RETURNING *`,
      [handoverId, lot.id, req.userId, declared, actual, flagged, lot.rate_per_kg, amount, lat, lon,
       haversineKm(lot.lat, lot.lon, lat, lon).toFixed(2), photos, capturedAt]);
    await client.query(`UPDATE lots SET status = 'WEIGHED' WHERE id = $1`, [lot.id]);
    if (lot.vendor_confirmed_at) {                  // vendor's confirmation synced first
      await finalize(client, lot, handover, lot.vendor_confirmed_at);
      handover.confirmed_at = lot.vendor_confirmed_at;
    }
    if (lot.trip_id) {
      await client.query(
        `UPDATE pickup_trips SET status = 'DONE' WHERE id = $1
           AND NOT EXISTS (SELECT 1 FROM lots WHERE trip_id = $1 AND status NOT IN ('WEIGHED', 'HANDED_OVER'))`,
        [lot.trip_id]);
    }
    await client.query('COMMIT');
    return res.status(201).json({ handover, handover_otp: otpFor(lot.id, lot.accepted_offer_id) });
  } catch (err) {
    await client.query('ROLLBACK').catch(() => {});
    next(err);
  } finally {
    client.release();
  }
};

// ── POST /hubs — register a collection hub (vendor / recycler) ─────────────
exports.hubValidation = [
  body('name').trim().isLength({ min: 2, max: 120 }),
  body('lat').isFloat({ min: -90, max: 90 }),
  body('lon').isFloat({ min: -180, max: 180 }),
];

exports.createHub = async (req, res, next) => {
  if (invalid(req, res)) return;
  try {
    const { rows: [hub] } = await pool.query(
      `INSERT INTO hubs (name, lat, lon, operator_id) VALUES ($1, $2, $3, $4) RETURNING *`,
      [req.body.name, req.body.lat, req.body.lon, req.userId]);
    return res.status(201).json({ hub });
  } catch (err) {
    next(err);
  }
};
