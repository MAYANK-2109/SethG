const router = require('express').Router();
const authenticate = require('../middleware/authenticate');
const requireRole = require('../middleware/requireRole');
const ctrl = require('../controllers/lotController');

// Chat messages route (can be accessed by both vendor and recycler)
// Note: In a real app we'd verify the recycler has access to this lot
router.get('/:id/messages', authenticate, ctrl.getMessages);
router.post('/:id/messages', authenticate, ctrl.postMessage);

// Collectors: kabadiwalas sign up as "vendor"; "user" is the older name for the same role
router.use(authenticate, requireRole('vendor', 'user'));
router.post('/', ctrl.syncLotValidation, ctrl.syncLot);
router.get('/mine', ctrl.myLots);
router.post('/:id/offers/:offerId/accept', ctrl.acceptOffer);
router.post('/:id/transport', ctrl.transportValidation, ctrl.chooseTransport);
router.post('/:id/confirm', ctrl.confirmValidation, ctrl.confirmHandover);

module.exports = router;
