const User = require('./User');
const Chat = require('./Chat');
const Message = require('./Message');
const ChatParticipant = require('./ChatParticipant');

User.belongsToMany(Chat, { through: ChatParticipant, foreignKey: 'userId' });
Chat.belongsToMany(User, { through: ChatParticipant, foreignKey: 'chatId' });

// Сообщения
Message.belongsTo(User, { as: 'sender', foreignKey: 'senderId' });
Message.belongsTo(Chat, { foreignKey: 'chatId' });

Chat.hasMany(Message, { foreignKey: 'chatId' });
User.hasMany(Message, { foreignKey: 'senderId' });

ChatParticipant.belongsTo(User, { foreignKey: 'userId' });
ChatParticipant.belongsTo(Chat, { foreignKey: 'chatId' });

module.exports = {
    User,
    Chat,
    Message,
    ChatParticipant
};