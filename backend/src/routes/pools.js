const router = require('express').Router();
const authenticate = require('../middleware/authenticate');
const requireRole = require('../middleware/requireRole');
const ctrl = require('../controllers/poolController');

// All pool routes are for authenticated vendors
router.use(authenticate, requireRole('vendor', 'user'));

router.get('/', ctrl.getOpenPools);
router.post('/', ctrl.createPool);
router.post('/:id/join', ctrl.joinPool);
router.post('/:id/post', ctrl.postPool);

module.exports = router;
