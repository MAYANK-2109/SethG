const router = require('express').Router();
const authenticate = require('../middleware/authenticate');
const ctrl = require('../controllers/userController');
const lotCtrl = require('../controllers/lotController');
const poolCtrl = require('../controllers/poolController');

router.get('/',    authenticate, ctrl.getProfile);
router.get('/profile', authenticate, ctrl.getProfile);
router.put('/profile', authenticate, ctrl.updateProfileValidation, ctrl.updateProfile);
router.post('/kyc',    authenticate, ctrl.uploadKyc);
router.get('/chats',   authenticate, lotCtrl.getMyChats);
router.get('/pools',   authenticate, poolCtrl.getMyPools);

module.exports = router;
