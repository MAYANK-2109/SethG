require('dotenv').config();
const pool = require('./pool');
const bcrypt = require('bcrypt');

async function seedChat() {
  try {
    const lotId = 'SG-TEST-001';
    const recyclerId = '1748e390-8095-4e3f-a41c-250becb14da7'; // 9301095908
    const vendorId = '4f327c25-1b8e-4f40-8c30-ec711ae28678';   // 9340659812

    // Ensure lot collector is vendor 9340659812
    await pool.query('UPDATE lots SET collector_id = $1 WHERE id = $2', [vendorId, lotId]);

    // Clear old messages for this lot
    await pool.query('DELETE FROM chat_messages WHERE lot_id = $1', [lotId]);

    const messages = [
      { sender: recyclerId, text: 'Namaste bhaiya! Maine aapke 50.5 kg mobile e-waste lot (SG-TEST-001) par offer daala hai ₹18/kg ka. Kya material ready hai?' },
      { sender: vendorId,   text: 'Namaste bhai. Haan material bilkul ready hai, mostly Android motherboards, batteries alag se packed hain, aur display units hain.' },
      { sender: recyclerId, text: 'Great! Motherboards IC intact hain na ya stripped down hain?' },
      { sender: vendorId,   text: 'Poora intact hai bhai, koi component nikala nahi gaya hai. Genuine scrap lot hai.' },
      { sender: recyclerId, text: 'Achha, par ₹18/kg ka rate standard chal raha hai market mein abhi.' },
      { sender: vendorId,   text: 'Nahi sir, ₹18 thoda kam hai. High grade PCB boards hain isme, kam se kam ₹26/kg to banta hai.' },
      { sender: recyclerId, text: 'Bhaiya ₹26 bohot zyada ho jayega. Transport and refining cost bhi humari rehti hai Indore se plant tak.' },
      { sender: vendorId,   text: 'Par quality check kar lijiye aap, 50 kg me 35 kg direct high yield boards hain. ₹24/kg final kariye.' },
      { sender: recyclerId, text: 'Chaliye main rate thoda badha deta hoon, ₹20/kg kar lete hain. Immediate payment through escrow ho jayega.' },
      { sender: vendorId,   text: 'Bhai ₹20 me nuksan ho jayega mera collection cost hi ₹19 pad gaya tha. Kam se kam ₹22.50 to kariye.' },
      { sender: recyclerId, text: 'Dekhiye agar batteries separated hain and packaging proper hai, to main ₹21.50/kg de sakta hoon. Deal final karein?' },
      { sender: vendorId,   text: 'Agar aap kal subah pickup kar lete hain to ₹22/kg par lock kar dete hain. Deal done.' },
      { sender: recyclerId, text: 'Theek hai bhaiya, ₹22/kg final! Kal subah 10:30 AM tak gaadi bhej dunga pickup ke liye.' },
      { sender: vendorId,   text: 'Shukriya! Location warehouse ki accurate hai app me, Rajwada area ke pass.' },
      { sender: recyclerId, text: 'Haan location show ho rahi hai 1.33 km door. Driver ka number subah share kar dunga.' },
      { sender: vendorId,   text: 'Bilkul, digital scale available hai mere paas accurate weighing ke liye.' },
      { sender: recyclerId, text: 'Badiya! Hum bhi portable digital scale layenge. Dono side cross check ho jayega.' },
      { sender: vendorId,   text: 'Haan bhai, fair deal honi chahiye. Handover OTP ready rakhunga.' },
      { sender: recyclerId, text: 'Perfect! Main app par updated offer ₹22/kg ka submit kar deta hoon, aap accept kar lena.' },
      { sender: vendorId,   text: 'Theek hai bhai, jaise hi offer update hoga main accept karke escrow lock kar dunga.' },
      { sender: recyclerId, text: 'Offer updated to ₹22/kg! Kal subah milte hain. Dhanyawad!' },
      { sender: vendorId,   text: 'Accepted! See you tomorrow morning. Safe journey.' }
    ];

    console.log('Inserting ' + messages.length + ' negotiation messages...');
    const now = Date.now();
    for (let i = 0; i < messages.length; i++) {
      const m = messages[i];
      const hash = await bcrypt.hash(m.text, 10);
      const msgTime = new Date(now - (messages.length - i) * 90000); // spaced by 1.5 mins
      await pool.query(
        'INSERT INTO chat_messages (lot_id, sender_id, content, content_hash, created_at) VALUES ($1, $2, $3, $4, $5)',
        [lotId, m.sender, m.text, hash, msgTime]
      );
    }

    // Update offer to accepted at ₹22/kg
    await pool.query('UPDATE offers SET rate_per_kg = 22.00 WHERE lot_id = $1 AND recycler_id = $2', [lotId, recyclerId]);

    console.log('Done! All ' + messages.length + ' messages created successfully.');
  } catch (err) {
    console.error('Migration error:', err);
  } finally {
    await pool.end();
  }
}

seedChat();
