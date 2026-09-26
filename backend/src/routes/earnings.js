const router = require('express').Router();
const authenticate = require('../middleware/authenticate');
const ctrl = require('../controllers/earningsController');

router.get('/today',   authenticate, ctrl.today);
router.get('/weekly',  authenticate, ctrl.weekly);
router.get('/monthly', authenticate, ctrl.monthly);
router.post('/',       authenticate, ctrl.addEarning);

module.exports = router;
