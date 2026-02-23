const express = require('express');
const router = express.Router();
const { searchUsers, getUserById, updateAvatar, updateUsername, saveFcmToken } = require('../controllers/userController');
const authMiddleware = require('../middleware/auth');

router.use(authMiddleware); // все роуты требуют авторизации

router.get('/search', searchUsers);
router.get('/:userId', getUserById);
router.put('/avatar', updateAvatar); // Изменили на PUT и убрали multer
router.put('/username', updateUsername);
router.post('/fcm-token', saveFcmToken);

module.exports = router;