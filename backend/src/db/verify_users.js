require('dotenv').config();
const pool = require('./pool');

async function verifyAll() {
    try {
        const res = await pool.query(
            "UPDATE users SET is_verified = true RETURNING *"
        );
        console.log(`Verified ${res.rowCount} users.`);
    } catch (err) {
        console.error("Error updating users:", err);
    } finally {
        pool.end();
    }
}

verifyAll();
