/**
 * Geo helpers shared by matching, pooling and handover checks.
 */

// A collector only ever deals with recyclers close by: 3 km first, widened
// to 5 km only when no recycler is within 3 km. Never beyond 5 km.
const MATCH_RADII_KM = [3, 5];
const MAX_MATCH_KM = MATCH_RADII_KM[MATCH_RADII_KM.length - 1];

function haversineKm(lat1, lon1, lat2, lon2) {
  const toRad = (d) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a = Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 6371 * 2 * Math.asin(Math.sqrt(a));
}

/** Same formula in SQL, for filtering rows in Postgres (with PostGIS fallback). */
function haversineSql(lat1, lon1, lat2, lon2) {
  return `(6371 * 2 * ASIN(SQRT(
    POWER(SIN(RADIANS(${lat2} - ${lat1}) / 2), 2) +
    COS(RADIANS(${lat1})) * COS(RADIANS(${lat2})) * POWER(SIN(RADIANS(${lon2} - ${lon1}) / 2), 2)
  )))`;
}

/** PostGIS ST_DWithin query snippet (in meters) with Haversine fallback. */
function postgisDWithinSql(latCol1, lonCol1, lat2, lon2, radiusKm) {
  const radiusMeters = radiusKm * 1000;
  return `ST_DWithin(
    ST_SetSRID(ST_MakePoint(${lonCol1}, ${latCol1}), 4326)::geography,
    ST_SetSRID(ST_MakePoint(${lon2}, ${lat2}), 4326)::geography,
    ${radiusMeters}
  )`;
}

/**
 * Visit order for a vehicle starting at `start`: always go to the nearest
 * unvisited stop next. Simple, fast, and good enough for 5–15 stops.
 */
function nearestNeighbourRoute(start, stops) {
  const remaining = [...stops];
  const route = [];
  let here = start;
  while (remaining.length) {
    let best = 0;
    for (let i = 1; i < remaining.length; i++) {
      if (haversineKm(here.lat, here.lon, remaining[i].lat, remaining[i].lon) <
          haversineKm(here.lat, here.lon, remaining[best].lat, remaining[best].lon)) best = i;
    }
    here = remaining.splice(best, 1)[0];
    route.push(here);
  }
  return route;
}

module.exports = { MATCH_RADII_KM, MAX_MATCH_KM, haversineKm, haversineSql, postgisDWithinSql, nearestNeighbourRoute };
