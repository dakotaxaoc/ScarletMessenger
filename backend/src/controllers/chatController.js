const { Op } = require('sequelize');
const { Chat, User, Message, ChatParticipant } = require('../models');

// Создать приватный чат (или вернуть существующий)
// src/controllers/chatController.js

const createPrivateChat = async (req, res) => {
    try {
        const { userId } = req.body; // ID того, с кем хотим общаться
        const currentUserId = req.user.id; // Наш ID

        if (userId === currentUserId) {
            return res.status(400).json({ message: 'Нельзя создать чат с самим собой' });
        }

        // Проверяем существование собеседника
        const otherUser = await User.findByPk(userId);
        if (!otherUser) {
            return res.status(404).json({ message: 'Пользователь не найден' });
        }

        // === ИСПРАВЛЕННАЯ ЛОГИКА ПОИСКА ===

        // 1. Находим все ID чатов, где есть МЫ
        const myChats = await ChatParticipant.findAll({
            where: { userId: currentUserId },
            attributes: ['chatId']
        });
        const myChatIds = myChats.map(c => c.chatId);

        // 2. Ищем чат, где есть МЫ (из списка выше) И второй участник
        // И при этом тип чата 'private'
        let existingChat = null;

        if (myChatIds.length > 0) {
            // Ищем участие собеседника в одном из НАШИХ чатов
            const commonParticipant = await ChatParticipant.findOne({
                where: {
                    chatId: myChatIds, // Ищем только в наших чатах
                    userId: userId
                }
            });

            if (commonParticipant) {
                // Если нашли общий чат, проверяем, что это именно приватный чат (а не группа)
                const chatCheck = await Chat.findOne({
                    where: {
                        id: commonParticipant.chatId,
                        type: 'private'
                    }
                });

                if (chatCheck) {
                    existingChat = chatCheck;
                }
            }
        }
        // ===================================

        if (existingChat) {
            // Чат найден — возвращаем его
            const fullChat = await Chat.findByPk(existingChat.id, {
                include: [{
                    model: User,
                    attributes: ['id', 'username', 'avatar', 'isOnline']
                }]
            });
            return res.json({ chat: fullChat, isNew: false });
        }

        // Чат не найден — создаем НОВЫЙ
        const chat = await Chat.create({ type: 'private' });

        await ChatParticipant.bulkCreate([
            { chatId: chat.id, userId: currentUserId },
            { chatId: chat.id, userId: userId }
        ]);

        const fullChat = await Chat.findByPk(chat.id, {
            include: [{
                model: User,
                attributes: ['id', 'username', 'avatar', 'isOnline']
            }]
        });

        res.status(201).json({ chat: fullChat, isNew: true });

    } catch (error) {
        console.error('Create private chat error:', error);
        res.status(500).json({ message: 'Ошибка сервера' });
    }
};

// Создать групповой чат
const createGroupChat = async (req, res) => {
    try {
        const { name, userIds } = req.body;
        const currentUserId = req.user.id;

        if (!name || name.trim() === '') {
            return res.status(400).json({ message: 'Название группы обязательно' });
        }

        if (!userIds || userIds.length === 0) {
            return res.status(400).json({ message: 'Добавьте участников' });
        }

        const chat = await Chat.create({
            type: 'group',
            name: name.trim()
        });

        // Добавляем создателя как owner
        const participants = [
            { chatId: chat.id, userId: currentUserId, role: 'owner' }
        ];

        // Добавляем остальных как member
        userIds.forEach(userId => {
            if (userId !== currentUserId) {
                participants.push({ chatId: chat.id, userId, role: 'member' });
            }
        });

        await ChatParticipant.bulkCreate(participants);

        const fullChat = await Chat.findByPk(chat.id, {
            include: [{
                model: User,
                attributes: ['id', 'username', 'avatar', 'isOnline']
            }]
        });

        res.status(201).json({ chat: fullChat });

    } catch (error) {
        console.error('Create group chat error:', error);
        res.status(500).json({ message: 'Ошибка сервера' });
    }
};

// Получить все чаты пользователя

const getMyChats = async (req, res) => {
    try {
        const currentUserId = req.user.id;

        // ШАГ 1: Находим ID чатов, в которых мы состоим
        const participations = await ChatParticipant.findAll({
            where: { userId: currentUserId },
            attributes: ['chatId']
        });

        // Превращаем список объектов в список ID: ['id1', 'id2', ...]
        const chatIds = participations.map(p => p.chatId);

        // ШАГ 2: Загружаем сами чаты по этим ID
        const chats = await Chat.findAll({
            where: {
                id: chatIds // <--- Фильтруем тут
            },
            include: [
                {
                    model: User,
                    // Теперь User включается ТОЛЬКО ОДИН РАЗ -> Конфликта нет!
                    attributes: ['id', 'username', 'avatar', 'isOnline']
                },
                {
                    model: Message,
                    limit: 1,
                    order: [['createdAt', 'DESC']],
                    include: [{ model: User, as: 'sender', attributes: ['id', 'username'] }]
                }
            ],
            order: [['updatedAt', 'DESC']]
        });

        res.json({ chats });

    } catch (error) {
        console.error('Get chats error:', error);
        res.status(500).json({ message: 'Ошибка сервера' });
    }
};

// Получить сообщения чата
const getChatMessages = async (req, res) => {
    try {
        const { chatId } = req.params;
        const { limit = 50, offset = 0 } = req.query;

        // Проверяем что пользователь в чате
        const participant = await ChatParticipant.findOne({
            where: { chatId, userId: req.user.id }
        });

        if (!participant) {
            return res.status(403).json({ message: 'Нет доступа к чату' });
        }

        const messages = await Message.findAll({
            where: { chatId },
            include: [{
                model: User,
                as: 'sender',
                attributes: ['id', 'username', 'avatar']
            }],
            order: [['createdAt', 'DESC']],
            limit: parseInt(limit),
            offset: parseInt(offset)
        });

        res.json({ messages: messages.reverse() });

    } catch (error) {
        console.error('Get messages error:', error);
        res.status(500).json({ message: 'Ошибка сервера' });
    }
};

const deleteChat = async (req, res) => {
    try {
        const { chatId } = req.params;
        const userId = req.user.id;
        // Verify user is participant
        const participant = await ChatParticipant.findOne({
            where: { chatId, userId }
        });
        if (!participant) {
            return res.status(403).json({ message: 'Нет доступа к чату' });
        }
        // Delete messages first, then participants, then chat
        await Message.destroy({ where: { chatId } });
        await ChatParticipant.destroy({ where: { chatId } });
        await Chat.destroy({ where: { id: chatId } });
        res.json({ message: 'Чат удалён' });
    } catch (error) {
        console.error('Delete chat error:', error);
        res.status(500).json({ message: 'Ошибка сервера' });
    }
};

module.exports = {
    createPrivateChat,
    createGroupChat,
    getMyChats,
    getChatMessages,
    deleteChat
};