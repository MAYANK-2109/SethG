const router = require('express').Router();
const authenticate = require('../middleware/authenticate');
const requireRole = require('../middleware/requireRole');
const ctrl = require('../controllers/lotController');

// Collectors: kabadiwalas sign up as "vendor"; "user" is the older name for the same role
router.use(authenticate, requireRole('vendor', 'user'));
router.post('/', ctrl.syncLotValidation, ctrl.syncLot);
router.get('/mine', ctrl.myLots);
router.post('/:id/offers/:offerId/accept', ctrl.acceptOffer);
router.post('/:id/transport', ctrl.transportValidation, ctrl.chooseTransport);

module.exports = router;
