const router = require('express').Router();
const authenticate = require('../middleware/authenticate');
const requireRole = require('../middleware/requireRole');
const ctrl = require('../controllers/recyclerController');

router.get('/', ctrl.listHubs);
router.post('/', authenticate, requireRole('vendor', 'recycler'), ctrl.hubValidation, ctrl.createHub);

module.exports = router;
