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

    // Client sends a message
    socket.on('send_message', async (data) => {
      const { lot_id, sender_id, content } = data;
      try {
        const { rows } = await pool.query(
          `INSERT INTO chat_messages (lot_id, sender_id, content) 
           VALUES ($1, $2, $3) RETURNING id, lot_id, sender_id, content, created_at`,
          [lot_id, sender_id, content]
        );
        const msg = rows[0];
        
        // Broadcast the message to everyone in the room (including sender)
        io.to(lot_id).emit('new_message', msg);
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
