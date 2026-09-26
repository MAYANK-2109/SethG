/**
 * Database migration script.
 * Run with: node src/db/migrate.js
 *
 * Creates all tables if they don't already exist — safe to re-run.
 */
require('dotenv').config();
const pool = require('./pool');

const schema = `
-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── Users ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
  id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  name          VARCHAR(120)  NOT NULL,
  phone         VARCHAR(20)   UNIQUE,
  email         VARCHAR(254)  UNIQUE,
  password_hash TEXT          NOT NULL,
  photo_url     TEXT,
  role          VARCHAR(20)   NOT NULL DEFAULT 'user',
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
