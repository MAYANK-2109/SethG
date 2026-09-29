const pool = require('./db/pool');

function setupSocketIO(io) {
  io.on('connection', (socket) => {
    // Client joins a lot-specific room to listen for messages
    socket.on('join_lot', (lotId) => {
      socket.join(lotId);
      console.log(`Socket ${socket.id} joined lot room: ${lotId}`);
    });

    socket.on('leave_lot', (lotId) => {
      socket.leave(lotId);
      console.log(`Socket ${socket.id} left lot room: ${lotId}`);
    });

    // Pool-specific rooms
    socket.on('join_pool', (poolId) => {
      socket.join(poolId);
      console.log(`Socket ${socket.id} joined pool room: ${poolId}`);
    });

    socket.on('leave_pool', (poolId) => {
      socket.leave(poolId);
      console.log(`Socket ${socket.id} left pool room: ${poolId}`);
    });

    // Client sends a message (lot or pool)
    socket.on('send_message', async (data) => {
      const { lot_id, pool_id, sender_id, content } = data;
      try {
        const roomId = pool_id || lot_id;
        const { rows } = await pool.query(
          `INSERT INTO chat_messages (lot_id, pool_id, sender_id, content) 
           VALUES ($1, $2, $3, $4) 
           RETURNING id, lot_id, pool_id, sender_id, content, created_at`,
          [lot_id || null, pool_id || null, sender_id, content]
        );
        const msg = rows[0];

        // Fetch sender name
        const userRes = await pool.query('SELECT name FROM users WHERE id = $1', [sender_id]);
        const senderName = userRes.rows[0]?.name || 'Member';
        
        // Broadcast the message to everyone in the room (including sender)
        io.to(roomId).emit('new_message', { ...msg, sender_name: senderName });
      } catch (err) {
        console.error("Error saving/sending message:", err);
      }
    });

    socket.on('disconnect', () => {
      console.log(`Socket ${socket.id} disconnected`);
    });
  });
}

module.exports = { setupSocketIO };
