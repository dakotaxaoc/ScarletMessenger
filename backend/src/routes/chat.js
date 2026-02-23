const express = require('express');
const router = express.Router();
const {
    createPrivateChat,
    createGroupChat,
    getMyChats,
    getChatMessages,
    deleteChat
} = require('../controllers/chatController');
const authMiddleware = require('../middleware/auth');

router.use(authMiddleware); // все роуты требуют авторизации

router.post('/private', createPrivateChat);
router.post('/group', createGroupChat);
router.get('/', getMyChats);
router.get('/:chatId/messages', getChatMessages);
router.delete('/:chatId', deleteChat); // удалить чат!

module.exports = router;