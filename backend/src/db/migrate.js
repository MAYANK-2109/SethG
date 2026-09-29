/**
 * Database migration script.
 * Run with: node src/db/migrate.js
 *
 * Creates all tables if they don't already exist — safe to re-run.
 */
require('dotenv').config();
const pool = require('./pool');

const schema = `
-- Enable UUID generation & PostGIS spatial extension (with fallback)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
DO $$ BEGIN
  CREATE EXTENSION IF NOT EXISTS postgis;
EXCEPTION WHEN OTHERS THEN
  RAISE NOTICE 'PostGIS extension not available or skipped';
END $$;

-- ── Users ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
  id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  name          VARCHAR(120)  NOT NULL,
  phone         VARCHAR(20)   UNIQUE,
  email         VARCHAR(254)  UNIQUE,
  password_hash TEXT          NOT NULL,
  photo_url     TEXT,
  role          VARCHAR(20)   NOT NULL DEFAULT 'vendor',
  certificate_url TEXT,
  is_verified   BOOLEAN       NOT NULL DEFAULT false,
  language      VARCHAR(10)   NOT NULL DEFAULT 'en',
  created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  CONSTRAINT users_phone_or_email CHECK (phone IS NOT NULL OR email IS NOT NULL)
);

-- ── Refresh tokens ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS refresh_tokens (
  id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash  TEXT          NOT NULL UNIQUE,
  expires_at  TIMESTAMPTZ   NOT NULL,
  created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens(user_id);

-- ── Earnings / Transactions ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS earnings (
  id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  amount       NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
  material     VARCHAR(80),        -- e.g. "plastic", "metal", "paper"
  weight_kg    NUMERIC(8,3),
  note         TEXT,
  earned_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_earnings_user_id       ON earnings(user_id);
CREATE INDEX IF NOT EXISTS idx_earnings_user_earned   ON earnings(user_id, earned_at DESC);

-- ── Roles (added after the users table already existed in production) ─────
-- CREATE TABLE IF NOT EXISTS never alters an existing table, so new columns
-- must also be added here or older databases never get them.
ALTER TABLE users ADD COLUMN IF NOT EXISTS role            VARCHAR(20) NOT NULL DEFAULT 'vendor';
ALTER TABLE users ADD COLUMN IF NOT EXISTS certificate_url TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_verified     BOOLEAN     NOT NULL DEFAULT false;

-- ── Location + recycler profile ────────────────────────────────────────────
-- Collectors: last known location. Recyclers: facility location, what they
-- accept, and the smallest load worth sending a vehicle for.
ALTER TABLE users ADD COLUMN IF NOT EXISTS lat                DOUBLE PRECISION;
ALTER TABLE users ADD COLUMN IF NOT EXISTS lon                DOUBLE PRECISION;
ALTER TABLE users ADD COLUMN IF NOT EXISTS accepted_materials TEXT[];      -- NULL = accepts all
ALTER TABLE users ADD COLUMN IF NOT EXISTS vehicle_min_kg     NUMERIC(8,2) NOT NULL DEFAULT 100;

-- ── Collection hubs (partner aggregators / kabadi shops) ───────────────────
CREATE TABLE IF NOT EXISTS hubs (
  id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  name        VARCHAR(120)  NOT NULL,
  lat         DOUBLE PRECISION NOT NULL,
  lon         DOUBLE PRECISION NOT NULL,
  operator_id UUID          REFERENCES users(id) ON DELETE SET NULL,
  created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- ── Pickup trips (one vehicle run: single pickup, pooled milk-run, or hub) ─
CREATE TABLE IF NOT EXISTS pickup_trips (
  id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  recycler_id    UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  mode           VARCHAR(10)   NOT NULL CHECK (mode IN ('PICKUP', 'POOLED', 'HUB')),
  hub_id         UUID          REFERENCES hubs(id),
  scheduled_date DATE          NOT NULL,
  total_kg       NUMERIC(10,3) NOT NULL,
  status         VARCHAR(20)   NOT NULL DEFAULT 'SCHEDULED',   -- SCHEDULED | DONE
  created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_trips_recycler ON pickup_trips(recycler_id, scheduled_date);

-- ── Lots (synced from the collector app; id is the app's offline lot id) ──
CREATE TABLE IF NOT EXISTS lots (
  id                TEXT          PRIMARY KEY,                   -- e.g. SG-260926-7KQ4
  collector_id      UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  category          VARCHAR(20)   NOT NULL,
  weight_kg         NUMERIC(10,3) NOT NULL CHECK (weight_kg > 0),
  estimate_low      INTEGER       NOT NULL,
  estimate_high     INTEGER       NOT NULL,
  price_region      TEXT,
  lat               DOUBLE PRECISION NOT NULL,
  lon               DOUBLE PRECISION NOT NULL,
  photo_hashes      TEXT[],                                      -- signed in-app photos (files stay on phone)
  status            VARCHAR(20)   NOT NULL DEFAULT 'LISTED',
    -- LISTED → ACCEPTED → SCHEDULED → WEIGHED → HANDED_OVER   (or CANCELLED)
  accepted_offer_id UUID,
  transport_mode    VARCHAR(10)   CHECK (transport_mode IN ('PICKUP', 'POOLED', 'HUB')),
  hub_id            UUID          REFERENCES hubs(id),
  trip_id           UUID          REFERENCES pickup_trips(id),
  stop_seq          INTEGER,                                     -- order in the trip route
  slot_start        TIMESTAMPTZ,
  slot_end          TIMESTAMPTZ,
  otp_hash          TEXT,                                        -- unused: code is derived, see lib/handover.js
  created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_lots_collector ON lots(collector_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_lots_status    ON lots(status);
CREATE INDEX IF NOT EXISTS idx_lots_trip      ON lots(trip_id);

-- ── Offers (recycler → lot) ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS offers (
  id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  lot_id       TEXT          NOT NULL REFERENCES lots(id) ON DELETE CASCADE,
  recycler_id  UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  rate_per_kg  NUMERIC(10,2) NOT NULL CHECK (rate_per_kg > 0),
  pickup_date  DATE          NOT NULL,
  note         TEXT,
  distance_km  NUMERIC(6,2)  NOT NULL,
  status       VARCHAR(20)   NOT NULL DEFAULT 'PENDING',       -- PENDING | ACCEPTED | REJECTED
  created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  UNIQUE (lot_id, recycler_id)
);
CREATE INDEX IF NOT EXISTS idx_offers_recycler ON offers(recycler_id, created_at DESC);

-- ── Handovers (verified custody transfer) ──────────────────────────────────
CREATE TABLE IF NOT EXISTS handovers (
  id                 TEXT          PRIMARY KEY,                  -- HR-2026-000456
  lot_id             TEXT          NOT NULL UNIQUE REFERENCES lots(id),
  recycler_id        UUID          NOT NULL REFERENCES users(id),
  declared_weight_kg NUMERIC(10,3) NOT NULL,
  actual_weight_kg   NUMERIC(10,3) NOT NULL CHECK (actual_weight_kg > 0),
  weight_flagged     BOOLEAN       NOT NULL DEFAULT false,        -- >15% off the declared weight
  rate_per_kg        NUMERIC(10,2) NOT NULL,                      -- locked at acceptance
  final_amount       NUMERIC(12,2) NOT NULL,
  lat                DOUBLE PRECISION NOT NULL,
  lon                DOUBLE PRECISION NOT NULL,
  distance_from_lot_km NUMERIC(6,2),
  photo_hashes       TEXT[]        NOT NULL,                      -- material + scale reading
  captured_at        TIMESTAMPTZ   NOT NULL,                      -- on-device time (may be offline)
  created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE SEQUENCE IF NOT EXISTS handover_seq;

-- Two-sided handover: recycler records weight, vendor confirms with the code
ALTER TABLE lots      ADD COLUMN IF NOT EXISTS vendor_confirmed_at TIMESTAMPTZ;   -- on-device time of the code entry
ALTER TABLE handovers ADD COLUMN IF NOT EXISTS confirmed_at        TIMESTAMPTZ;   -- NULL until the vendor confirms

-- Escrow & Dispute columns
ALTER TABLE lots      ADD COLUMN IF NOT EXISTS escrow_status VARCHAR(20) DEFAULT 'NONE'; -- NONE | HELD | RELEASED | DISPUTED
ALTER TABLE lots      ADD COLUMN IF NOT EXISTS escrow_tx_id  TEXT;
ALTER TABLE lots      ADD COLUMN IF NOT EXISTS escrow_amount NUMERIC(12,2);

-- Blockchain & EPR Verifiable Ledger columns
ALTER TABLE lots      ADD COLUMN IF NOT EXISTS blockchain_tx_hash    TEXT;
ALTER TABLE lots      ADD COLUMN IF NOT EXISTS blockchain_proof_hash TEXT;
ALTER TABLE lots      ADD COLUMN IF NOT EXISTS epr_token_id          BIGINT;
ALTER TABLE handovers ADD COLUMN IF NOT EXISTS blockchain_proof_hash TEXT;
ALTER TABLE handovers ADD COLUMN IF NOT EXISTS blockchain_tx_hash    TEXT;

-- PostGIS Geometry columns (with safe fallback for environments without PostGIS)
DO $$ BEGIN
  ALTER TABLE users ADD COLUMN IF NOT EXISTS geom GEOMETRY(Point, 4326);
  ALTER TABLE lots  ADD COLUMN IF NOT EXISTS geom GEOMETRY(Point, 4326);
  ALTER TABLE hubs  ADD COLUMN IF NOT EXISTS geom GEOMETRY(Point, 4326);
EXCEPTION WHEN OTHERS THEN
  RAISE NOTICE 'PostGIS GEOMETRY column creation skipped';
END $$;

-- ── Disputes (formal resolution for weight / price variance) ────────────────
CREATE TABLE IF NOT EXISTS disputes (
  id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  lot_id             TEXT          NOT NULL REFERENCES lots(id) ON DELETE CASCADE,
  raised_by          UUID          NOT NULL REFERENCES users(id),
  reason             TEXT          NOT NULL,
  declared_weight_kg NUMERIC(10,3),
  actual_weight_kg   NUMERIC(10,3),
  status             VARCHAR(20)   NOT NULL DEFAULT 'OPEN',       -- OPEN | RESOLVED | REJECTED
  resolution_notes   TEXT,
  created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_disputes_lot ON disputes(lot_id);

-- ── Vendor Pools ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS vendor_pools (
  id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  admin_id    UUID          NOT NULL REFERENCES users(id),
  category    VARCHAR(20)   NOT NULL,
  status      VARCHAR(20)   NOT NULL DEFAULT 'OPEN',       -- OPEN | POSTED
  created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
ALTER TABLE lots ADD COLUMN IF NOT EXISTS vendor_pool_id UUID REFERENCES vendor_pools(id);

UPDATE handovers h SET confirmed_at = h.created_at
  FROM lots l WHERE l.id = h.lot_id AND l.status = 'HANDED_OVER' AND h.confirmed_at IS NULL;

-- ── Chat messages ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS chat_messages (
  id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  lot_id     VARCHAR(30) REFERENCES lots(id) ON DELETE CASCADE,
  sender_id  UUID        REFERENCES users(id) ON DELETE CASCADE,
  content    TEXT        NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS content TEXT;
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS content_hash TEXT;
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS pool_id UUID REFERENCES vendor_pools(id) ON DELETE CASCADE;
ALTER TABLE chat_messages ALTER COLUMN lot_id DROP NOT NULL;
CREATE INDEX IF NOT EXISTS idx_chat_messages_lot_id     ON chat_messages(lot_id);
CREATE INDEX IF NOT EXISTS idx_chat_messages_pool_id    ON chat_messages(pool_id);
CREATE INDEX IF NOT EXISTS idx_chat_messages_created_at ON chat_messages(created_at);

ALTER TABLE lots ADD COLUMN IF NOT EXISTS photo_url TEXT;

-- ── Notifications ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS notifications (
  id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title        TEXT        NOT NULL,
  message      TEXT        NOT NULL,
  type         VARCHAR(50) DEFAULT 'GENERAL',
  reference_id TEXT,
  is_read      BOOLEAN     NOT NULL DEFAULT false,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id, created_at DESC);

-- ── Trigger: auto-update updated_at on users ───────────────────────────────
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_users_updated_at ON users;
CREATE TRIGGER trg_users_updated_at
  BEFORE UPDATE ON users
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_lots_updated_at ON lots;
CREATE TRIGGER trg_lots_updated_at
  BEFORE UPDATE ON lots
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ── Seed demo earnings for testing (idempotent) ────────────────────────────
-- Uncomment if you want seed data:
-- INSERT INTO earnings (user_id, amount, material, weight_kg, note, earned_at)
-- SELECT u.id, 150.00, 'plastic', 5.0, 'Demo entry', NOW()
-- FROM   users u LIMIT 1
-- ON CONFLICT DO NOTHING;
`;

async function migrate() {
  const client = await pool.connect();
  try {
    console.log('Running migrations…');
    await client.query(schema);
    console.log('✅ Migration complete.');
  } catch (err) {
    console.error('❌ Migration failed:', err.message);
    process.exit(1);
  } finally {
    client.release();
    await pool.end();
  }
}

migrate();
