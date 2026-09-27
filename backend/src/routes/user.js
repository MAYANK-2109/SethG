const router = require('express').Router();
const authenticate = require('../middleware/authenticate');
const ctrl = require('../controllers/userController');
const lotCtrl = require('../controllers/lotController');

router.get('/',    authenticate, ctrl.getProfile);
router.get('/profile', authenticate, ctrl.getProfile);
router.put('/profile', authenticate, ctrl.updateProfileValidation, ctrl.updateProfile);
router.post('/kyc',    authenticate, ctrl.uploadKyc);
router.get('/chats',   authenticate, lotCtrl.getMyChats);

module.exports = router;
