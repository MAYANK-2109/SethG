require('dotenv').config();
const pool = require('./pool');

async function checkLots() {
    try {
        const res = await pool.query(
            "SELECT * FROM lots"
        );
        console.log(`Found ${res.rowCount} lots.`);
        if (res.rowCount > 0) {
            console.log(res.rows);
        }
    } catch (err) {
        console.error("Error checking lots:", err);
    } finally {
        pool.end();
    }
}

checkLots();
