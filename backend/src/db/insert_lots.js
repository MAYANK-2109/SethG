require('dotenv').config();
const pool = require('./pool');

async function insertLots() {
    try {
        const { rows: [vendor] } = await pool.query("SELECT id FROM users WHERE role = 'vendor' LIMIT 1");
        if (!vendor) {
            console.log("No vendor found");
            return;
        }

        // Inserting lots near Indore (Lat: 22.7196, Lon: 75.8577)
        await pool.query(`
            INSERT INTO lots (id, collector_id, category, weight_kg, estimate_low, estimate_high, price_region, lat, lon, photo_hashes, status)
            VALUES 
            ('SG-TEST-001', $1, 'MOBILE', 50.5, 500, 1000, 'Indore', 22.71, 75.85, '{"hash1"}', 'LISTED'),
            ('SG-TEST-002', $1, 'PCB', 120.0, 1500, 3000, 'Indore', 22.72, 75.86, '{"hash2"}', 'LISTED'),
            ('SG-TEST-003', $1, 'CABLE', 45.0, 200, 450, 'Indore', 22.70, 75.84, '{"hash3"}', 'LISTED')
            ON CONFLICT DO NOTHING
        `, [vendor.id]);

        console.log("Inserted 3 LISTED lots near Indore.");
    } catch (err) {
        console.error("Error inserting lots:", err);
    } finally {
        pool.end();
    }
}

insertLots();
