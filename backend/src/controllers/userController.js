const { Op } = require('sequelize');
const { User } = require('../models');

// Поиск пользователей
const searchUsers = async (req, res) => {
    try {
        const { q = '' } = req.query;
        const currentUserId = req.user.id;

        let whereClause = {
            id: { [Op.ne]: currentUserId } // исключаем себя
        };

        // Если есть поисковый запрос
        if (q.trim() !== '') {
            whereClause.username = {
                [Op.iLike]: `%${q}%` // регистронезависимый поиск
            };
        }

        const users = await User.findAll({
            where: whereClause,
            attributes: ['id', 'username', 'avatar', 'isOnline', 'lastSeen'],
            order: [['username', 'ASC']],
            limit: 50
        });

        res.json({ users });

    } catch (error) {
        console.error('Search users error:', error);
        res.status(500).json({ message: 'Ошибка сервера' });
    }
};

// Получить пользователя по ID
const getUserById = async (req, res) => {
    try {
        const { userId } = req.params;

        const user = await User.findByPk(userId, {
            attributes: ['id', 'username', 'avatar', 'isOnline', 'lastSeen']
        });

        if (!user) {
            return res.status(404).json({ message: 'Пользователь не найден' });
        }

        res.json({ user });

    } catch (error) {
        console.error('Get user error:', error);
        res.status(500).json({ message: 'Ошибка сервера' });
    }
};

// Обновление аватара (клиент уже загрузил файл в S3)
const updateAvatar = async (req, res) => {
    try {
        const { avatarUrl } = req.body;

        if (!avatarUrl) {
            return res.status(400).json({ message: 'Avatar URL is required' });
        }

        const userId = req.user.id;

        // Обновляем юзера
        await User.update(
            { avatar: avatarUrl },
            { where: { id: userId } }
        );

        // Возвращаем обновленного юзера
        const user = await User.findByPk(userId, {
            attributes: ['id', 'username', 'avatar', 'isOnline', 'lastSeen']
        });

        res.json({ message: 'Аватар обновлен', user });

    } catch (error) {
        console.error('Update avatar error:', error);
        res.status(500).json({ message: 'Ошибка обновления аватара' });
    }
};

const updateUsername = async (req, res) => {
    try {
        const { username } = req.body;
        const userId = req.user.id;
        if (!username || username.trim().length < 3) {
            return res.status(400).json({ message: 'Имя должно быть не короче 3 символов' });
        }
        // Тут можно добавить проверку на уникальность, если нужно
        await User.update({ username }, { where: { id: userId } });
        const user = await User.findByPk(userId, {
            attributes: ['id', 'username', 'avatar', 'isOnline', 'lastSeen']
        });
        res.json({ message: 'Имя обновлено', user });
    } catch (error) {
        console.error('Update username error:', error);
        res.status(500).json({ message: 'Ошибка сервера' });
    }
};

const saveFcmToken = async (req, res) => {
    try {
        const { fcmToken } = req.body;
        const userId = req.user.id;

        await User.update({ fcmToken }, { where: { id: userId } });

        res.json({ message: 'FCM токен обновлен' });
    } catch (error) {
        console.error('Save FCM token error:', error);
        res.status(500).json({ message: 'Ошибка сервера' });
    }
};

module.exports = {
    searchUsers,
    getUserById,
    updateAvatar,
    updateUsername,
    saveFcmToken
};