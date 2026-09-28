const pool = require('../db/pool');

// Get all open pools of a specific category
exports.getOpenPools = async (req, res, next) => {
  const { category } = req.query;
  try {
    let query = `
      SELECT p.*, u.name as admin_name, 
             COALESCE(SUM(l.weight_kg), 0) as total_weight,
             COUNT(l.id) as lot_count
      FROM vendor_pools p
      JOIN users u ON u.id = p.admin_id
      LEFT JOIN lots l ON l.vendor_pool_id = p.id
      WHERE p.status = 'OPEN'
    `;
    const values = [];
    if (category) {
      query += ` AND p.category = $1`;
      values.push(category);
    }
    query += ` GROUP BY p.id, u.name ORDER BY p.created_at DESC`;
    
    const { rows } = await pool.query(query, values);
    res.json(rows);
  } catch (err) {
    next(err);
  }
};

// Create a new pool
exports.createPool = async (req, res, next) => {
  const { category } = req.body;
  if (!category) return res.status(400).json({ error: 'Category is required' });
  try {
    const { rows: [newPool] } = await pool.query(
      `INSERT INTO vendor_pools (admin_id, category) VALUES ($1, $2) RETURNING *`,
      [req.userId, category]
    );
    res.status(201).json(newPool);
  } catch (err) {
    next(err);
  }
};

// Join a pool with a specific lot
exports.joinPool = async (req, res, next) => {
  const { id } = req.params; // pool id
  const { lot_id } = req.body;
  
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const { rows: [vendorPool] } = await client.query(`SELECT * FROM vendor_pools WHERE id = $1 FOR UPDATE`, [id]);
    if (!vendorPool) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'Pool not found' }); }
    if (vendorPool.status !== 'OPEN') { await client.query('ROLLBACK'); return res.status(400).json({ error: 'Pool is not open' }); }
    
    const { rows: [lot] } = await client.query(`SELECT * FROM lots WHERE id = $1 AND collector_id = $2 FOR UPDATE`, [lot_id, req.userId]);
    if (!lot) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'Lot not found or unauthorized' }); }
    if (lot.category !== vendorPool.category) { await client.query('ROLLBACK'); return res.status(400).json({ error: 'Lot category does not match pool category' }); }
    if (lot.status !== 'LISTED' && lot.status !== 'POOLED') { await client.query('ROLLBACK'); return res.status(400).json({ error: 'Lot cannot be added to pool in current status' }); }
    
    await client.query(`UPDATE lots SET vendor_pool_id = $1, status = 'POOLED' WHERE id = $2`, [id, lot_id]);
    await client.query('COMMIT');
    res.json({ message: 'Successfully joined pool' });
  } catch (err) {
    await client.query('ROLLBACK');
    next(err);
  } finally {
    client.release();
  }
};

// Post a pool (admin only) - This changes pool status and posts all lots in it
exports.postPool = async (req, res, next) => {
  const { id } = req.params;
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const { rows: [vendorPool] } = await client.query(`SELECT * FROM vendor_pools WHERE id = $1 FOR UPDATE`, [id]);
    if (!vendorPool) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'Pool not found' }); }
    if (vendorPool.admin_id !== req.userId) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'Only the admin can post the pool' }); }
    if (vendorPool.status !== 'OPEN') { await client.query('ROLLBACK'); return res.status(400).json({ error: 'Pool already posted' }); }
    
    await client.query(`UPDATE vendor_pools SET status = 'POSTED' WHERE id = $1`, [id]);
    // Optionally update all lots in the pool to 'LISTED' or broadcast them
    await client.query(`UPDATE lots SET status = 'LISTED' WHERE vendor_pool_id = $1`, [id]);
    
    await client.query('COMMIT');
    res.json({ message: 'Pool posted successfully' });
  } catch (err) {
    await client.query('ROLLBACK');
    next(err);
  } finally {
    client.release();
  }
};
