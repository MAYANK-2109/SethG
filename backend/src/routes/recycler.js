const router = require('express').Router();
const authenticate = require('../middleware/authenticate');
const requireRole = require('../middleware/requireRole');
const ctrl = require('../controllers/recyclerController');

router.use(authenticate, requireRole('recycler'));
router.put('/profile', ctrl.profileValidation, ctrl.updateProfile);
router.get('/lots/nearby', ctrl.nearbyValidation, ctrl.nearbyLots);
router.post('/lots/:id/offers', ctrl.offerValidation, ctrl.makeOffer);
router.get('/lots/accepted', ctrl.acceptedLots);
router.get('/trips', ctrl.trips);
router.post('/lots/:id/handover', ctrl.handoverValidation, ctrl.handover);

module.exports = router;
