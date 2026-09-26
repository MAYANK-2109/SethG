/**
 * Turns accepted lots into pickup trips.
 *
 *  PICKUP  lot ≥ recycler's vehicle minimum → its own trip on the offer's pickup date
 *  POOLED  small lots: cluster lots within POOL_RADIUS_KM of each other; once a
 *          cluster reaches the vehicle minimum, create one milk-run trip and
 *          order the stops nearest-neighbour from the recycler's facility
 *  HUB     lots dropped at the same hub; once the hub total reaches the
 *          vehicle minimum, one trip collects them all from the hub
 *
 * Every stop gets a 2-hour window ("Thursday 10–12"), stops 30 min apart.
 * Runs whenever a collector picks a transport mode and when a recycler opens
 * their trips (Render's free plan has no cron).
 */
const { haversineKm, nearestNeighbourRoute } = require('./geo');

const POOL_RADIUS_KM = 5;
const DAY_START_HOUR = 10;     // first slot 10:00 IST
const MINUTES_PER_STOP = 30;
const WINDOW_HOURS = 2;

const istDate = (d) => new Date(d.getTime() + 5.5 * 3600e3).toISOString().slice(0, 10);

function tomorrowIst() {
  return istDate(new Date(Date.now() + 24 * 3600e3));
}

function slotFor(dateStr, index) {
  const dayStart = new Date(`${dateStr}T${String(DAY_START_HOUR).padStart(2, '0')}:00:00+05:30`);
  const start = new Date(dayStart.getTime() + index * MINUTES_PER_STOP * 60e3);
  const end = new Date(start.getTime() + WINDOW_HOURS * 3600e3);
  return { start, end };
}

async function createTrip(db, recycler, mode, lots, { hub = null, date = null } = {}) {
  const tripDate = [date, tomorrowIst(), ...lots.map((l) => l.pickup_date)]   // 'YYYY-MM-DD' strings
    .filter(Boolean).sort().pop();                         // latest of everyone's constraints
  const totalKg = lots.reduce((s, l) => s + Number(l.weight_kg), 0);
  const { rows: [trip] } = await db.query(
    `INSERT INTO pickup_trips (recycler_id, mode, hub_id, scheduled_date, total_kg)
     VALUES ($1, $2, $3, $4, $5) RETURNING *`,
    [recycler.id, mode, hub ? hub.id : null, tripDate, totalKg]
  );

  const route = hub ? lots : nearestNeighbourRoute({ lat: recycler.lat, lon: recycler.lon }, lots);
  for (let i = 0; i < route.length; i++) {
    const { start, end } = slotFor(tripDate, hub ? 0 : i);   // at a hub everything is collected at once
    await db.query(
      `UPDATE lots SET trip_id = $1, stop_seq = $2, slot_start = $3, slot_end = $4, status = 'SCHEDULED'
       WHERE id = $5`,
      [trip.id, i + 1, start, end, route[i].id]
    );
  }
  return trip;
}

/** Plans all unscheduled accepted lots for one recycler. Returns created trips. */
async function planTrips(db, recyclerId) {
  const { rows: [recycler] } = await db.query(
    `SELECT id, lat, lon, vehicle_min_kg FROM users WHERE id = $1`, [recyclerId]);
  if (!recycler) return [];
  const minKg = Number(recycler.vehicle_min_kg);

  const { rows: lots } = await db.query(
    `SELECT l.id, l.lat, l.lon, l.weight_kg, l.transport_mode, l.hub_id, l.created_at, o.pickup_date::text AS pickup_date
     FROM lots l JOIN offers o ON o.id = l.accepted_offer_id
     WHERE o.recycler_id = $1 AND l.status = 'ACCEPTED' AND l.trip_id IS NULL
       AND l.transport_mode IS NOT NULL
     ORDER BY l.created_at`,
    [recyclerId]
  );
  const trips = [];

  // A. Individual pickups
  for (const lot of lots.filter((l) => l.transport_mode === 'PICKUP')) {
    trips.push(await createTrip(db, recycler, 'PICKUP', [lot]));
  }

  // B. Pooled milk-runs: grow a cluster around each waiting lot (oldest first);
  //    lots in a too-small cluster stay available to later clusters
  const pool = lots.filter((l) => l.transport_mode === 'POOLED');
  const assigned = new Set();
  for (const seed of pool) {
    if (assigned.has(seed.id)) continue;
    const cluster = pool.filter((l) =>
      !assigned.has(l.id) && haversineKm(seed.lat, seed.lon, l.lat, l.lon) <= POOL_RADIUS_KM);
    const kg = cluster.reduce((s, l) => s + Number(l.weight_kg), 0);
    if (kg >= minKg) {
      trips.push(await createTrip(db, recycler, 'POOLED', cluster));
      cluster.forEach((l) => assigned.add(l.id));
    }
  }

  // C. Hub collections
  const byHub = {};
  for (const lot of lots.filter((l) => l.transport_mode === 'HUB')) (byHub[lot.hub_id] ||= []).push(lot);
  for (const [hubId, hubLots] of Object.entries(byHub)) {
    const kg = hubLots.reduce((s, l) => s + Number(l.weight_kg), 0);
    if (kg < minKg) continue;
    const { rows: [hub] } = await db.query(`SELECT id, lat, lon FROM hubs WHERE id = $1`, [hubId]);
    trips.push(await createTrip(db, recycler, 'HUB', hubLots, { hub }));
  }
  return trips;
}

module.exports = { planTrips, POOL_RADIUS_KM };
