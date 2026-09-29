const router = require('express').Router();
const authenticate = require('../middleware/authenticate');
const requireRole = require('../middleware/requireRole');
const ctrl = require('../controllers/poolController');

// All pool routes are for authenticated vendors / collectors
router.use(authenticate, requireRole('vendor', 'user'));

router.get('/', ctrl.getOpenPools);
router.get('/mine', ctrl.getMyPools);
router.get('/my', ctrl.getMyPools);
router.post('/', ctrl.createPool);

router.get('/:id', ctrl.getPoolById);
router.post('/:id/join', ctrl.joinPool);
router.post('/:id/post', ctrl.postPool);

// Pool chat messages
router.get('/:id/messages', ctrl.getPoolMessages);
router.post('/:id/messages', ctrl.postPoolMessage);

module.exports = router;
