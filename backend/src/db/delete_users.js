const pool = require('./pool');
require('dotenv').config({ path: '../../.env' }); // Adjust if needed

async function deleteUsers() {
    try {
        const res = await pool.query("DELETE FROM users WHERE role = 'user'");
        console.log(`Deleted ${res.rowCount} users with role 'user'.`);
        const updateRes = await pool.query("UPDATE users SET role = 'vendor' WHERE role IS NULL");
        console.log(`Updated ${updateRes.rowCount} users with NULL role to 'vendor'.`);
    } catch (err) {
        console.error("Error deleting users:", err);
    } finally {
        pool.end();
    }
}

deleteUsers();
