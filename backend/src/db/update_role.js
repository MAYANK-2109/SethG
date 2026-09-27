require('dotenv').config(); // Load from cwd which is backend/
const pool = require('./pool');

async function updateRole() {
    try {
        const res = await pool.query(
            "UPDATE users SET role = 'recycler' WHERE phone = $1 RETURNING *",
            ['9301095908']
        );
        if (res.rowCount > 0) {
            console.log(`Updated user ${res.rows[0].name} (Phone: ${res.rows[0].phone}) to role: ${res.rows[0].role}`);
        } else {
            console.log("No user found with that phone number.");
        }
    } catch (err) {
        console.error("Error updating user:", err);
    } finally {
        pool.end();
    }
}

updateRole();
