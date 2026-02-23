const jwt = require('jsonwebtoken');
const { User, Chat, Message, ChatParticipant } = require('../models');
const admin = require('../config/firebase');

const setupSocket = (io) => {
    // Проверка токена при подключении
    io.use((socket, next) => {
        const token = socket.handshake.auth.token;

        if (!token) {
            console.log('Socket auth failed: no token');
            return next(new Error('Токен не предоставлен'));
        }

        try {
            const decoded = jwt.verify(token, process.env.JWT_SECRET);
            socket.userId = decoded.id;
            console.log('Socket auth success:', socket.userId);
            next();
        } catch (error) {
            console.log('Socket auth failed: invalid token');
            next(new Error('Неверный токен'));
        }
    });

    io.on('connection', async (socket) => {
        console.log('=== USER CONNECTED ===');
        console.log('User ID:', socket.userId);

        try {
            // Обновляем статус онлайн
            await User.update(
                { isOnline: true },
                { where: { id: socket.userId } }
            );

            // Находим все чаты пользователя через ChatParticipant
            const participants = await ChatParticipant.findAll({
                where: { userId: socket.userId },
                attributes: ['chatId']
            });

            // Присоединяемся к комнатам
            participants.forEach(p => {
                socket.join(p.chatId);
                console.log(`User ${socket.userId} joined room ${p.chatId}`);
            });

            console.log(`User joined ${participants.length} chat rooms`);

        } catch (error) {
            console.error('Connection setup error:', error);
        }

        // Отправка сообщения
        socket.on('send_message', async (data) => {
            console.log('=== SEND MESSAGE ===');
            console.log('From:', socket.userId);
            console.log('Data:', JSON.stringify(data));

            try {
                const { chatId, content, type = 'text' } = data;

                // Создаём сообщение
                const message = await Message.create({
                    content,
                    type,
                    chatId,
                    senderId: socket.userId
                });

                console.log('Message created:', message.id);

                // Достаём с данными отправителя
                const fullMessage = await Message.findByPk(message.id, {
                    include: [{ model: User, as: 'sender' }]
                });

                // Отправляем всем в чате
                io.to(chatId).emit('new_message', fullMessage);
                console.log('Emitted to room:', chatId);

                // === ОТПРАВКА PUSH УВЕДОМЛЕНИЯ ===
                if (admin.apps.length > 0) {
                    try {
                        const participants = await ChatParticipant.findAll({
                            where: { chatId },
                            include: [{
                                model: User,
                                attributes: ['id', 'fcmToken']
                            }]
                        });

                        const tokens = [];
                        participants.forEach(p => {
                            if (p.userId !== socket.userId && p.User && p.User.fcmToken) {
                                tokens.push(p.User.fcmToken);
                            }
                        });


                        if (tokens.length > 0) {
                            const messagePayload = {
                                notification: {
                                    title: fullMessage.sender.username,
                                    body: content
                                },
                                tokens: tokens,
                                data: {
                                    chatId: chatId.toString(),
                                    messageId: message.id.toString(),
                                    click_action: "FLUTTER_NOTIFICATION_CLICK" // Generic action, or customize
                                }
                            };

                            const response = await admin.messaging().sendEachForMulticast(messagePayload);
                            console.log('FCM sent:', response.successCount + ' messages');
                            if (response.failureCount > 0) {
                                const failedTokens = [];
                                response.responses.forEach((resp, idx) => {
                                    if (!resp.success) {
                                        failedTokens.push(tokens[idx]);
                                    }
                                });
                                console.log('List of tokens that caused failures: ' + failedTokens);
                            }
                        }
                    } catch (e) {
                        console.error("FCM send error", e);
                    }
                }
                // =================================

            } catch (error) {
                console.error('Send message error:', error);
                socket.emit('error', { message: 'Ошибка отправки сообщения' });
            }
        });

        socket.on('delete_message', async (data) => {
            const { messageId, chatId } = data;
            const userId = socket.userId;

            // Find and verify ownership
            const message = await Message.findByPk(messageId);
            if (!message || message.senderId !== userId) {
                return; // Not authorized
            }

            // Delete from database
            await message.destroy();

            // Broadcast to all users in chat
            io.to(chatId).emit('message_deleted', { messageId, chatId });
        });

        // Присоединиться к новому чату
        socket.on('join_chat', async (data) => {
            const { chatId } = data;
            // Проверка доступа
            const participant = await ChatParticipant.findOne({
                where: { chatId, userId: socket.userId }
            });

            if (participant) {
                socket.join(chatId);
                console.log(`User ${socket.userId} joined new room ${chatId}`);
            } else {
                socket.emit('error', { message: 'Нет доступа к этому чату' });
            }
        });

        // Пометить как прочитанные
        socket.on('mark_read', async (data) => {
            const { chatId } = data;
            const { Op } = require('sequelize');

            try {
                await Message.update(
                    { isRead: true },
                    {
                        where: {
                            chatId,
                            senderId: { [Op.ne]: socket.userId },
                            isRead: false
                        }
                    }
                );

                io.to(chatId).emit('messages_read', { chatId, userId: socket.userId });
            } catch (error) {
                console.error('Mark read error:', error);
            }
        });

        // Печатает...
        socket.on('typing', (data) => {
            const { chatId } = data;
            socket.to(chatId).emit('user_typing', {
                chatId,
                userId: socket.userId
            });
        });

        // Отключение
        socket.on('disconnect', async () => {
            console.log('=== USER DISCONNECTED ===');
            console.log('User ID:', socket.userId);

            try {
                await User.update(
                    { isOnline: false, lastSeen: new Date() },
                    { where: { id: socket.userId } }
                );

                socket.broadcast.emit('user_offline', { userId: socket.userId });
            } catch (error) {
                console.error('Disconnect error:', error);
            }
        });
    });
};

module.exports = setupSocket;