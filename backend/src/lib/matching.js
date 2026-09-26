/**
 * Which recyclers may see / bid on which lots.
 *
 * A recycler is eligible for a lot when it is a verified recycler with a
 * facility location, accepts the lot's material, and is within the lot's
 * match radius: 3 km, or 5 km if no eligible recycler exists within 3 km.
 */
const { MATCH_RADII_KM, haversineSql } = require('./geo');

const ELIGIBLE_RECYCLER = `
  r.role = 'recycler' AND r.is_verified = true
  AND r.lat IS NOT NULL AND r.lon IS NOT NULL`;
const ACCEPTS = (lotAlias, recyclerAlias) =>
  `(${recyclerAlias}.accepted_materials IS NULL OR ${lotAlias}.category = ANY(${recyclerAlias}.accepted_materials))`;

/**
 * SQL expression: the match radius (km) for lot alias `l` — 3 if any eligible
 * recycler is within 3 km, otherwise 5.
 */
const LOT_RADIUS_SQL = `
  (CASE WHEN EXISTS (
     SELECT 1 FROM users r3
     WHERE r3.role = 'recycler' AND r3.is_verified = true AND r3.lat IS NOT NULL
       AND ${ACCEPTS('l', 'r3')}
       AND ${haversineSql('l.lat', 'l.lon', 'r3.lat', 'r3.lon')} <= ${MATCH_RADII_KM[0]}
   ) THEN ${MATCH_RADII_KM[0]} ELSE ${MATCH_RADII_KM[1]} END)`;

/** Eligible recyclers for one lot, nearest first, with distance. */
async function recyclersForLot(db, lot) {
  const { rows } = await db.query(
    `SELECT r.id, r.name, ${haversineSql('$1::float8', '$2::float8', 'r.lat', 'r.lon')} AS distance_km
     FROM users r
     WHERE ${ELIGIBLE_RECYCLER}
       AND (r.accepted_materials IS NULL OR $3 = ANY(r.accepted_materials))
     ORDER BY distance_km`,
    [lot.lat, lot.lon, lot.category]
  );
  for (const radius of MATCH_RADII_KM) {
    const within = rows.filter((r) => r.distance_km <= radius);
    if (within.length) return { radiusKm: radius, recyclers: within };
  }
  return { radiusKm: MATCH_RADII_KM[MATCH_RADII_KM.length - 1], recyclers: [] };
}

module.exports = { ELIGIBLE_RECYCLER, ACCEPTS, LOT_RADIUS_SQL, recyclersForLot };
