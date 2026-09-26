/**
 * One canonical form for Indian mobile numbers, so "+91 91317 11386",
 * "919131711386", "09131711386" and "9131711386" are the same account.
 */
function normalizePhone(raw) {
  if (raw == null) return null;
  const digits = String(raw).replace(/\D/g, '');
  if (digits.length === 12 && digits.startsWith('91')) return digits.slice(2);
  if (digits.length === 11 && digits.startsWith('0')) return digits.slice(1);
  return digits || null;
}

/** SQL: stored phone column in the same canonical form (matches rows saved before normalizing). */
const PHONE_SQL = `RIGHT(regexp_replace(phone, '\\D', '', 'g'), 10)`;

module.exports = { normalizePhone, PHONE_SQL };
