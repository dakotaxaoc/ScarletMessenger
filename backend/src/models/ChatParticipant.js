const { DataTypes } = require('sequelize');
const { sequelize } = require('../config/database');

const ChatParticipant = sequelize.define('ChatParticipant', {
    id: {
        type: DataTypes.UUID,
        defaultValue: DataTypes.UUIDV4,
        primaryKey: true
    },
    role: {
        type: DataTypes.ENUM('member', 'admin', 'owner'),
        defaultValue: 'member'
    }
}, {
    timestamps: true
});

module.exports = ChatParticipant;