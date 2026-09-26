const router = require('express').Router();
const ctrl = require('../controllers/authController');

router.post('/register', ctrl.registerValidation, ctrl.register);
router.post('/login',    ctrl.loginValidation,    ctrl.login);
router.post('/refresh',  ctrl.refresh);
router.post('/logout',   ctrl.logout);

module.exports = router;
