const pool = require('../db/pool');
const bcrypt = require('bcrypt');
const { createNotification, notifyPoolMembers } = require('../lib/notifications');

const CHAT_SALT_ROUNDS = 10;

function formatSimpleId(poolId) {
  const clean = String(poolId).replace(/[^a-zA-Z0-9]/g, '').toUpperCase();
  return 'SG-P' + clean.slice(0, 4);
}

// Get all open pools of a specific category (for joining)
exports.getOpenPools = async (req, res, next) => {
  const { category } = req.query;
  try {
    let query = `
      SELECT p.*, u.name as admin_name, 
             ('SG-P' || UPPER(SUBSTRING(REPLACE(p.id::text, '-', ''), 1, 4))) as simple_id,
             (p.admin_id = $1) as is_admin,
             COALESCE(SUM(l.weight_kg), 0)::float as total_weight,
             COUNT(l.id)::int as lot_count
      FROM vendor_pools p
      JOIN users u ON u.id = p.admin_id
      LEFT JOIN lots l ON l.vendor_pool_id = p.id
      WHERE p.status = 'OPEN'
    `;
    const values = [req.userId];
    if (category) {
      query += ` AND p.category = $2`;
      values.push(category);
    }
    query += ` GROUP BY p.id, u.name ORDER BY p.created_at DESC`;
    
    const { rows } = await pool.query(query, values);
    res.json(rows);
  } catch (err) {
    next(err);
  }
};

// Get existing pools of the calling user (either created by them or containing their lots)
exports.getMyPools = async (req, res, next) => {
  try {
    const userId = req.userId;
    const { rows } = await pool.query(
      `SELECT p.id,
              p.admin_id,
              p.category,
              p.status,
              p.created_at,
              u.name as admin_name,
              ('SG-P' || UPPER(SUBSTRING(REPLACE(p.id::text, '-', ''), 1, 4))) as simple_id,
              (p.admin_id = $1) as is_admin,
              COALESCE(SUM(l.weight_kg), 0)::float as total_weight,
              COUNT(l.id)::int as lot_count,
              (SELECT COALESCE(cm.content, cm.content_hash, '') FROM chat_messages cm WHERE cm.pool_id = p.id ORDER BY cm.created_at DESC LIMIT 1) as last_message,
              (SELECT cm.created_at FROM chat_messages cm WHERE cm.pool_id = p.id ORDER BY cm.created_at DESC LIMIT 1) as last_message_at,
              (SELECT cm.sender_id FROM chat_messages cm WHERE cm.pool_id = p.id ORDER BY cm.created_at DESC LIMIT 1) as last_sender_id,
              (SELECT COUNT(*) FROM chat_messages cm WHERE cm.pool_id = p.id)::int as message_count
       FROM vendor_pools p
       JOIN users u ON u.id = p.admin_id
       LEFT JOIN lots l ON l.vendor_pool_id = p.id
       WHERE p.admin_id = $1 OR EXISTS (SELECT 1 FROM lots my_lot WHERE my_lot.vendor_pool_id = p.id AND my_lot.collector_id = $1)
       GROUP BY p.id, u.name
       ORDER BY COALESCE((SELECT cm.created_at FROM chat_messages cm WHERE cm.pool_id = p.id ORDER BY cm.created_at DESC LIMIT 1), p.created_at) DESC`,
      [userId]
    );
    res.json({ pools: rows });
  } catch (err) {
    next(err);
  }
};

// Get a single pool's detail
exports.getPoolById = async (req, res, next) => {
  try {
    const { id } = req.params;
    const { rows: [p] } = await pool.query(
      `SELECT p.*, u.name as admin_name,
              ('SG-P' || UPPER(SUBSTRING(REPLACE(p.id::text, '-', ''), 1, 4))) as simple_id,
              (p.admin_id = $1) as is_admin,
              COALESCE(SUM(l.weight_kg), 0)::float as total_weight,
              COUNT(l.id)::int as lot_count
       FROM vendor_pools p
       JOIN users u ON u.id = p.admin_id
       LEFT JOIN lots l ON l.vendor_pool_id = p.id
       WHERE p.id = $2
       GROUP BY p.id, u.name`,
      [req.userId, id]
    );
    if (!p) return res.status(404).json({ error: 'Pool not found' });

    // Fetch lots in pool
    const { rows: lots } = await pool.query(
      `SELECT l.id, l.category, l.weight_kg, l.status, l.collector_id, u.name as collector_name, l.photo_url
       FROM lots l
       JOIN users u ON u.id = l.collector_id
       WHERE l.vendor_pool_id = $1`,
      [id]
    );

    res.json({ pool: { ...p, lots } });
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
    const simpleId = formatSimpleId(newPool.id);

    // Initial system message in chat
    const initialContent = `🎉 Pool ${simpleId} created for ${category}. Admin and members can coordinate here.`;
    const hash = await bcrypt.hash(initialContent, CHAT_SALT_ROUNDS);
    await pool.query(
      `INSERT INTO chat_messages (pool_id, sender_id, content, content_hash)
       VALUES ($1, $2, $3, $4)`,
      [newPool.id, req.userId, initialContent, hash]
    );

    // Create notification
    await createNotification(
      req.userId,
      'Pool Created',
      `Pool ${simpleId} for ${category} has been created and is ready for lots.`,
      'POOL',
      newPool.id
    );

    res.status(201).json({ ...newPool, simple_id: simpleId, is_admin: true });
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

    const simpleId = formatSimpleId(id);
    const joinMsg = `📦 ${req.userName || 'Member'} added Lot #${lot_id} (${lot.weight_kg} kg) to this pool.`;
    const hash = await bcrypt.hash(joinMsg, CHAT_SALT_ROUNDS);
    await client.query(
      `INSERT INTO chat_messages (pool_id, sender_id, content, content_hash)
       VALUES ($1, $2, $3, $4)`,
      [id, req.userId, joinMsg, hash]
    );

    await client.query('COMMIT');

    // Notify admin
    if (vendorPool.admin_id !== req.userId) {
      await createNotification(
        vendorPool.admin_id,
        'New Lot in Pool',
        `${req.userName || 'A vendor'} joined your Pool ${simpleId} with a ${lot.weight_kg} kg lot.`,
        'POOL',
        id
      );
    }

    // Notify user
    await createNotification(
      req.userId,
      'Joined Pool',
      `You successfully joined Pool ${simpleId}.`,
      'POOL',
      id
    );

    // Broadcast update via socket
    const io = req.app.get('io');
    if (io) {
      io.to(id).emit('pool_updated', { pool_id: id, action: 'member_joined' });
    }

    res.json({ message: 'Successfully joined pool', pool_id: id });
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
    if (vendorPool.admin_id !== req.userId) { 
      await client.query('ROLLBACK'); 
      return res.status(403).json({ error: 'Only the pool admin can post the pool' }); 
    }
    if (vendorPool.status !== 'OPEN') { 
      await client.query('ROLLBACK'); 
      return res.status(400).json({ error: 'Pool already posted' }); 
    }
    
    await client.query(`UPDATE vendor_pools SET status = 'POSTED' WHERE id = $1`, [id]);
    // Update all lots in the pool to 'LISTED' so recyclers can discover and bid on them
    await client.query(`UPDATE lots SET status = 'LISTED' WHERE vendor_pool_id = $1`, [id]);
    
    const simpleId = formatSimpleId(id);
    const postMsg = `📢 Pool ${simpleId} has been posted to the marketplace by Admin! Recyclers can now bid on this pooled material.`;
    const hash = await bcrypt.hash(postMsg, CHAT_SALT_ROUNDS);
    await client.query(
      `INSERT INTO chat_messages (pool_id, sender_id, content, content_hash)
       VALUES ($1, $2, $3, $4)`,
      [id, req.userId, postMsg, hash]
    );

    await client.query('COMMIT');

    // Notify all members of the pool
    await notifyPoolMembers(
      id,
      'Pool Posted to Market',
      `Pool ${simpleId} has been posted to the marketplace by Admin. Recyclers can now place bids!`,
      'POOL'
    );

    // Broadcast via Socket.io
    const io = req.app.get('io');
    if (io) {
      io.to(id).emit('pool_posted', { pool_id: id, status: 'POSTED' });
      io.to(id).emit('new_message', {
        pool_id: id,
        sender_id: req.userId,
        sender_name: 'Admin',
        content: postMsg,
        created_at: new Date().toISOString()
      });
    }

    res.json({ message: 'Pool posted successfully', status: 'POSTED', pool_id: id });
  } catch (err) {
    await client.query('ROLLBACK');
    next(err);
  } finally {
    client.release();
  }
};

// GET /pools/:id/messages — retrieve chat messages for a pool
exports.getPoolMessages = async (req, res, next) => {
  try {
    const { id } = req.params;
    const { rows } = await pool.query(
      `SELECT cm.id, cm.pool_id, cm.sender_id, COALESCE(cm.content, cm.content_hash) AS msg_content, cm.created_at, u.name AS sender_name
       FROM chat_messages cm
       JOIN users u ON u.id = cm.sender_id
       WHERE cm.pool_id = $1
       ORDER BY cm.created_at ASC`,
      [id]
    );

    res.json(rows.map(r => ({
      id: r.id,
      pool_id: r.pool_id,
      sender_id: r.sender_id,
      sender_name: r.sender_name,
      content: r.msg_content,
      created_at: r.created_at
    })));
  } catch (err) {
    next(err);
  }
};

// POST /pools/:id/messages — post a chat message in a pool
exports.postPoolMessage = async (req, res, next) => {
  try {
    const { id } = req.params;
    const { content } = req.body;
    const sender_id = req.userId;

    if (!content || content.trim() === '') {
      return res.status(422).json({ error: 'Message content required' });
    }

    // Verify user is in this pool (either admin or has lot in pool)
    const { rows: [vendorPool] } = await pool.query(
      `SELECT p.id, p.admin_id,
              EXISTS (SELECT 1 FROM lots l WHERE l.vendor_pool_id = p.id AND l.collector_id = $1) as is_member
       FROM vendor_pools p WHERE p.id = $2`,
      [sender_id, id]
    );
    if (!vendorPool) return res.status(404).json({ error: 'Pool not found' });
    if (vendorPool.admin_id !== sender_id && !vendorPool.is_member) {
      return res.status(403).json({ error: 'Only pool members can participate in this chat' });
    }

    const trimmed = content.trim();
    const content_hash = await bcrypt.hash(trimmed, CHAT_SALT_ROUNDS);

    const { rows: [msg] } = await pool.query(
      `INSERT INTO chat_messages (pool_id, sender_id, content, content_hash)
       VALUES ($1, $2, $3, $4)
       RETURNING id, pool_id, sender_id, content, created_at`,
      [id, sender_id, trimmed, content_hash]
    );

    const senderName = req.userName || 'Member';
    const messagePayload = { ...msg, sender_name: senderName };

    // Broadcast via Socket.io
    const io = req.app.get('io');
    if (io) {
      io.to(id).emit('new_message', messagePayload);
    }

    // Notify other pool members
    const simpleId = formatSimpleId(id);
    await notifyPoolMembers(
      id,
      `Pool ${simpleId} Message`,
      `${senderName}: ${trimmed}`,
      'POOL',
      sender_id
    );

    res.status(201).json(messagePayload);
  } catch (err) {
    next(err);
  }
};
