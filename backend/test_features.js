require('dotenv').config();
const http = require('http');
const express = require('express');
const { Server } = require('socket.io');
const { io: ClientIO } = require('socket.io-client');
const pool = require('./src/db/pool');

// Start a local test server using the actual app setup
const authRoutes = require('./src/routes/auth');
const userRoutes = require('./src/routes/user');
const earningsRoutes = require('./src/routes/earnings');
const lotRoutes = require('./src/routes/lots');
const recyclerRoutes = require('./src/routes/recycler');
const hubRoutes = require('./src/routes/hubs');
const { errorHandler } = require('./src/middleware/errorHandler');
const { setupSocketIO } = require('./src/socket');

const app = express();
app.use(express.json());
app.use('/auth', authRoutes);
app.use('/user', userRoutes);
app.use('/earnings', earningsRoutes);
app.use('/lots', lotRoutes);
app.use('/recycler', recyclerRoutes);
app.use('/hubs', hubRoutes);
app.get('/health', (_req, res) => res.json({ status: 'ok', service: 'SethG API' }));
app.use(errorHandler);

const server = http.createServer(app);
const ioServer = new Server(server, { cors: { origin: '*' } });
setupSocketIO(ioServer);

const TEST_PORT = 3199;
const BASE_URL = `http://localhost:${TEST_PORT}`;

async function request(path, options = {}) {
  const url = `${BASE_URL}${path}`;
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  const res = await fetch(url, {
    method: options.method || 'GET',
    headers,
    body: options.body ? JSON.stringify(options.body) : undefined,
  });
  const data = await res.json().catch(() => null);
  return { status: res.status, ok: res.ok, data };
}

async function runTests() {
  console.log('--- STARTING COMPREHENSIVE BACKEND & FEATURE VERIFICATION ---');
  await new Promise(resolve => server.listen(TEST_PORT, resolve));
  console.log(`Test server running at ${BASE_URL}\n`);

  let passed = 0;
  let failed = 0;

  function assert(condition, name, details = '') {
    if (condition) {
      console.log(`[PASS] ${name}`);
      passed++;
    } else {
      console.error(`[FAIL] ${name} - ${details}`);
      failed++;
    }
  }

  try {
    // 1. Health check
    const health = await request('/health');
    assert(health.status === 200 && health.data?.status === 'ok', '1. Health check /health');

    // 2. Authentication: Vendor Registration
    const vendorPhone = `+9198${Math.floor(10000000 + Math.random() * 90000000)}`;
    const regVendor = await request('/auth/register', {
      method: 'POST',
      body: {
        name: 'Ramesh Kabadiwala',
        phone: vendorPhone,
        password: 'Password123!',
        confirmPassword: 'Password123!',
        role: 'vendor',
        language: 'hi'
      }
    });
    assert(regVendor.status === 201 && regVendor.data?.accessToken, '2. Vendor Registration', JSON.stringify(regVendor.data));
    const vendorToken = regVendor.data?.accessToken;
    const vendorRefreshToken = regVendor.data?.refreshToken;
    const vendorId = regVendor.data?.user?.id;

    // 3. Authentication: Recycler Registration
    const recyclerPhone = `+9199${Math.floor(10000000 + Math.random() * 90000000)}`;
    const regRecycler = await request('/auth/register', {
      method: 'POST',
      body: {
        name: 'GreenEarth Recycling Hub',
        phone: recyclerPhone,
        password: 'Password123!',
        confirmPassword: 'Password123!',
        role: 'recycler',
        language: 'en'
      }
    });
    assert(regRecycler.status === 201 && regRecycler.data?.accessToken, '3. Recycler Registration', JSON.stringify(regRecycler.data));
    const recyclerToken = regRecycler.data?.accessToken;
    const recyclerId = regRecycler.data?.user?.id;

    // 4. Token Refresh
    const refreshRes = await request('/auth/refresh', {
      method: 'POST',
      body: { refreshToken: vendorRefreshToken }
    });
    assert(refreshRes.status === 200 && refreshRes.data?.accessToken, '4. Auth Token Refresh');

    // 5. User Profile: GET & PUT
    const getProfile = await request('/user/profile', {
      headers: { Authorization: `Bearer ${vendorToken}` }
    });
    assert(getProfile.status === 200 && getProfile.data?.user?.name === 'Ramesh Kabadiwala', '5. User Profile GET');

    const updateProfile = await request('/user/profile', {
      method: 'PUT',
      headers: { Authorization: `Bearer ${vendorToken}` },
      body: { name: 'Ramesh Kumar (Kabadi)', language: 'hi' }
    });
    assert(updateProfile.status === 200 && updateProfile.data?.user?.name === 'Ramesh Kumar (Kabadi)', '6. User Profile Update');

    // Recycler profile update with coordinates & accepted materials
    const updateRecycler = await request('/recycler/profile', {
      method: 'PUT',
      headers: { Authorization: `Bearer ${recyclerToken}` },
      body: {
        lat: 22.72,
        lon: 75.86,
        accepted_materials: ['CABLE', 'MOBILE', 'PCB', 'BATTERY'],
        vehicle_min_kg: 50.0
      }
    });
    // Mark recycler as verified in database for test
    await pool.query('UPDATE users SET is_verified = true WHERE id = $1', [recyclerId]);
    assert(updateRecycler.status === 200, '7. Recycler Facility Profile & Geolocation Setup');

    // 8. Earnings: Add Earning and Fetch Summaries
    const addEarning = await request('/earnings', {
      method: 'POST',
      headers: { Authorization: `Bearer ${vendorToken}` },
      body: {
        amount: 450.00,
        material: 'CABLE',
        weight_kg: 9.0,
        note: 'Scrap copper wire'
      }
    });
    assert(addEarning.status === 201 && parseFloat(addEarning.data?.earning?.amount) === 450.00, '8. POST /earnings');

    const todayEarnings = await request('/earnings/today', {
      headers: { Authorization: `Bearer ${vendorToken}` }
    });
    assert(todayEarnings.status === 200 && parseFloat(todayEarnings.data?.total) >= 450.00, '9. GET /earnings/today');

    const weeklyEarnings = await request('/earnings/weekly', {
      headers: { Authorization: `Bearer ${vendorToken}` }
    });
    assert(weeklyEarnings.status === 200 && parseFloat(weeklyEarnings.data?.total) >= 450.00, '10. GET /earnings/weekly');

    const monthlyEarnings = await request('/earnings/monthly', {
      headers: { Authorization: `Bearer ${vendorToken}` }
    });
    assert(monthlyEarnings.status === 200 && parseFloat(monthlyEarnings.data?.total) >= 450.00, '11. GET /earnings/monthly');

    // 12. E-Waste Lot Lifecycle: Vendor Syncs a Lot
    const randCode = Math.random().toString(36).substring(2, 6).toUpperCase();
    const testLotId = `SG-260927-${randCode}`;
    const syncLot = await request('/lots', {
      method: 'POST',
      headers: { Authorization: `Bearer ${vendorToken}` },
      body: {
        id: testLotId,
        category: 'CABLE',
        weight_kg: 60.0,
        estimate_low: 600,
        estimate_high: 1800,
        price_region: 'Indore',
        lat: 22.719,
        lon: 75.859,
        photo_hashes: ['hash_dummy_test_123']
      }
    });
    assert(syncLot.status === 201 && syncLot.data?.lot?.id === testLotId, '12. Vendor Lot Sync (POST /lots)', JSON.stringify(syncLot.data));

    // 13. Vendor fetches their lots
    const myLots = await request('/lots/mine', {
      headers: { Authorization: `Bearer ${vendorToken}` }
    });
    const foundLotInMine = Array.isArray(myLots.data?.lots) && myLots.data.lots.some(l => l.id === testLotId);
    assert(myLots.status === 200 && foundLotInMine, '13. Vendor Lots List (GET /lots/mine)');

    // 14. Recycler fetches nearby lots
    const nearbyLots = await request('/recycler/lots/nearby', {
      headers: { Authorization: `Bearer ${recyclerToken}` }
    });
    const foundLotNearby = Array.isArray(nearbyLots.data?.lots) && nearbyLots.data.lots.some(l => l.id === testLotId);
    assert(nearbyLots.status === 200 && foundLotNearby, '14. Recycler Nearby Lots (GET /recycler/lots/nearby)');

    // 15. Recycler makes an offer on the lot
    const makeOffer = await request(`/recycler/lots/${testLotId}/offers`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${recyclerToken}` },
      body: {
        rate_per_kg: 25.50,
        pickup_date: '2026-09-30',
        note: 'Will pick up in pickup truck'
      }
    });
    assert(makeOffer.status === 201 && makeOffer.data?.offer?.id, '15. Recycler Make Offer (POST /recycler/lots/:id/offers)', JSON.stringify(makeOffer.data));
    const offerId = makeOffer.data?.offer?.id;

    // 16. Vendor accepts the offer
    const acceptOffer = await request(`/lots/${testLotId}/offers/${offerId}/accept`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${vendorToken}` }
    });
    assert(acceptOffer.status === 200 && acceptOffer.data?.lot?.status === 'ACCEPTED', '16. Vendor Accept Offer (POST /lots/:id/offers/:offerId/accept)', JSON.stringify(acceptOffer.data));

    // 17. In-App Chat: Vendor sends a message
    const vendorMsg = await request(`/lots/${testLotId}/messages`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${vendorToken}` },
      body: { content: 'Namaste, when will you arrive?' }
    });
    assert(vendorMsg.status === 201 && vendorMsg.data?.content === 'Namaste, when will you arrive?', '17. Vendor Sends Chat Message (POST /lots/:id/messages)');

    // 18. In-App Chat: Recycler retrieves messages
    const recyclerGetMsg = await request(`/lots/${testLotId}/messages`, {
      headers: { Authorization: `Bearer ${recyclerToken}` }
    });
    assert(recyclerGetMsg.status === 200 && Array.isArray(recyclerGetMsg.data) && recyclerGetMsg.data.length >= 1, '18. Recycler Retrieves Chat Messages (GET /lots/:id/messages)');

    // 19. In-App Chat: Recycler replies
    const recyclerReply = await request(`/lots/${testLotId}/messages`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${recyclerToken}` },
      body: { content: 'We will reach by 10 AM tomorrow morning.' }
    });
    assert(recyclerReply.status === 201 && recyclerReply.data?.content.includes('10 AM'), '19. Recycler Replies in Chat (POST /lots/:id/messages)');

    // 20. Transport Selection: Vendor chooses transport
    const chooseTransport = await request(`/lots/${testLotId}/transport`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${vendorToken}` },
      body: {
        mode: 'PICKUP',
        slot_start: '2026-09-30T04:30:00.000Z',
        slot_end: '2026-09-30T06:30:00.000Z'
      }
    });
    assert(chooseTransport.status === 200 && chooseTransport.data?.lot?.status === 'SCHEDULED', '20. Vendor Selects Transport (POST /lots/:id/transport)', JSON.stringify(chooseTransport.data));

    // 21. Recycler Trips
    const trips = await request('/recycler/trips', {
      headers: { Authorization: `Bearer ${recyclerToken}` }
    });
    assert(trips.status === 200, '21. Recycler Trips Schedule (GET /recycler/trips)');

    // 22. Handover: Recycler weighs and records handover
    const nowIso = new Date().toISOString();
    const shaHash1 = 'a'.repeat(64);
    const shaHash2 = 'b'.repeat(64);
    const handover = await request(`/recycler/lots/${testLotId}/handover`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${recyclerToken}` },
      body: {
        actual_weight_kg: 62.0,
        lat: 22.719,
        lon: 75.859,
        photo_hashes: [shaHash1, shaHash2],
        captured_at: nowIso
      }
    });
    assert(handover.status === 201 && handover.data?.handover?.id, '22. Recycler Handover Record (POST /recycler/lots/:id/handover)', JSON.stringify(handover.data));
    const handoverOtp = handover.data?.handover_otp;
    assert(handoverOtp != null && /^\d{6}$/.test(handoverOtp), '22b. Handover generated 6-digit OTP: ' + handoverOtp);

    // 23. Vendor Handover Confirmation with code
    const confirmHandover = await request(`/lots/${testLotId}/confirm`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${vendorToken}` },
      body: {
        otp: handoverOtp,
        confirmed_at: new Date().toISOString()
      }
    });
    assert(confirmHandover.status === 200 && confirmHandover.data?.lot?.status === 'HANDED_OVER', '23. Vendor Confirms Handover with Code (POST /lots/:id/confirm)', JSON.stringify(confirmHandover.data));

    // 24. Collection Hubs Endpoint
    const hubs = await request('/hubs');
    assert(hubs.status === 200 && Array.isArray(hubs.data?.hubs), '24. Collection Hubs (GET /hubs)');

    // 25. Real-Time Socket.io Connection & Event Emission
    const socketClient = ClientIO(BASE_URL, { transports: ['websocket'] });
    const socketConnected = await new Promise((resolve) => {
      const timeout = setTimeout(() => resolve(false), 4000);
      socketClient.on('connect', () => {
        clearTimeout(timeout);
        socketClient.emit('join', testLotId);
        resolve(true);
      });
    });
    assert(socketConnected, '25. Real-Time Socket.io Connection & Room Join');
    socketClient.disconnect();

    // 26. KYC Document Registration & Verification Test
    const kycVendorPhone = `+9197${Math.floor(10000000 + Math.random() * 90000000)}`;
    const regKycVendor = await request('/auth/register', {
      method: 'POST',
      body: {
        name: 'Verified Collector Ramesh',
        phone: kycVendorPhone,
        password: 'Password123!',
        confirmPassword: 'Password123!',
        role: 'vendor',
        language: 'hi',
        certificate_url: 'https://kyc.sethg.in/docs/cert_test_123.pdf'
      }
    });
    assert(regKycVendor.status === 201 && regKycVendor.data?.user?.is_verified === true, '26. Registration with KYC Certificate Document', JSON.stringify(regKycVendor.data));
    const kycVendorToken = regKycVendor.data?.accessToken;

    // 27. Dedicated KYC Document Upload API Endpoint
    const uploadKycRes = await request('/user/kyc', {
      method: 'POST',
      headers: { Authorization: `Bearer ${vendorToken}` },
      body: { certificate_url: 'https://kyc.sethg.in/docs/vendor_license_999.pdf' }
    });
    assert(uploadKycRes.status === 200 && uploadKycRes.data?.user?.is_verified === true, '27. Dedicated KYC Upload (POST /user/kyc)', JSON.stringify(uploadKycRes.data));

    // 28. Digital Escrow & Dispute Lifecycle Test
    const disputeLotId = `SG-260927-DISP`;
    await request('/lots', {
      method: 'POST',
      headers: { Authorization: `Bearer ${kycVendorToken}` },
      body: {
        id: disputeLotId, category: 'BATTERY', weight_kg: 100.0, estimate_low: 1000, estimate_high: 2500,
        price_region: 'Indore', lat: 22.72, lon: 75.86, photo_hashes: ['hash_disp_123']
      }
    });
    const makeDispOffer = await request(`/recycler/lots/${disputeLotId}/offers`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${recyclerToken}` },
      body: { rate_per_kg: 20.00, pickup_date: '2026-09-30', note: 'Heavy load' }
    });
    const dispOfferId = makeDispOffer.data?.offer?.id;

    // Accept offer -> Holds Digital Escrow (Razorpay)
    const acceptDispOffer = await request(`/lots/${disputeLotId}/offers/${dispOfferId}/accept`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${kycVendorToken}` }
    });
    assert(acceptDispOffer.status === 200 && acceptDispOffer.data?.lot?.escrow_status === 'HELD', '28. Razorpay Digital Escrow Hold on Offer Acceptance', JSON.stringify(acceptDispOffer.data));

    // Raise dispute -> Freezes Escrow
    const raiseDisputeRes = await request(`/lots/${disputeLotId}/dispute`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${recyclerToken}` },
      body: { reason: 'Actual delivered weight 30% lower than declared weight' }
    });
    assert(raiseDisputeRes.status === 201 && raiseDisputeRes.data?.dispute?.id, '29a. Raise Escrow Dispute (POST /lots/:id/dispute)', JSON.stringify(raiseDisputeRes.data));
    const disputeId = raiseDisputeRes.data?.dispute?.id;

    // Resolve dispute -> Releases Escrow
    const resolveDisputeRes = await request(`/lots/disputes/${disputeId}/resolve`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${kycVendorToken}` },
      body: { status: 'RESOLVED', resolution_notes: 'Settled by mutual weight adjustment' }
    });
    assert(resolveDisputeRes.status === 200, '29b. Resolve Dispute & Release Escrow (POST /lots/disputes/:id/resolve)', JSON.stringify(resolveDisputeRes.data));

  } catch (err) {
    console.error('Test Execution Error:', err);
    failed++;
  } finally {
    server.close();
    await pool.end();
    console.log(`\n=========================================`);
    console.log(`TEST SUMMARY: ${passed} PASSED, ${failed} FAILED`);
    console.log(`=========================================`);
    process.exit(failed > 0 ? 1 : 0);
  }
}

runTests();
