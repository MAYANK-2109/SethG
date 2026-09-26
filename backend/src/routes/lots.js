const router = require('express').Router();
const authenticate = require('../middleware/authenticate');
const requireRole = require('../middleware/requireRole');
const ctrl = require('../controllers/lotController');

// Collectors ("user" role)
router.use(authenticate, requireRole('user'));
router.post('/', ctrl.syncLotValidation, ctrl.syncLot);
router.get('/mine', ctrl.myLots);
router.post('/:id/offers/:offerId/accept', ctrl.acceptOffer);
router.post('/:id/transport', ctrl.transportValidation, ctrl.chooseTransport);

module.exports = router;
