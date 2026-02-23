const jwt = require('jsonwebtoken');
const { User } = require('../models');

const generateToken = (userId) => {
    return jwt.sign({ id: userId }, process.env.JWT_SECRET, {
        expiresIn: '7d'
    });
};

// Регистрация
const register = async (req, res) => {
    try {
        const { username, email, password } = req.body;

        // Проверяем существует ли пользователь
        const existingUser = await User.findOne({
            where: { email }
        });

        if (existingUser) {
            return res.status(400).json({ message: 'Email уже используется' });
        }

        const existingUsername = await User.findOne({
            where: { username }
        });

        if (existingUsername) {
            return res.status(400).json({ message: 'Username уже занят' });
        }

        // Создаём пользователя
        const user = await User.create({
            username,
            email,
            password
        });

        const token = generateToken(user.id);

        res.status(201).json({
            message: 'Регистрация успешна',
            user,
            token
        });
    } catch (error) {
        console.error('Register error:', error);
        res.status(500).json({ message: 'Ошибка сервера' });
    }
};

// Логин
const login = async (req, res) => {
    try {
        const { email, password } = req.body;

        const user = await User.findOne({
            where: { email }
        });

        if (!user) {
            return res.status(400).json({ message: 'Неверные данные' });
        }

        const isMatch = await user.comparePassword(password);

        if (!isMatch) {
            return res.status(400).json({ message: 'Неверные данные' });
        }

        // Обновляем статус онлайн
        await user.update({ isOnline: true });

        const token = generateToken(user.id);

        res.json({
            message: 'Вход выполнен',
            user,
            token
        });
    } catch (error) {
        console.error('Login error:', error);
        res.status(500).json({ message: 'Ошибка сервера' });
    }
};

// Получить текущего пользователя
const getMe = async (req, res) => {
    try {
        const user = await User.findByPk(req.user.id);

        if (!user) {
            return res.status(404).json({ message: 'Пользователь не найден' });
        }

        res.json({ user });
    } catch (error) {
        res.status(500).json({ message: 'Ошибка сервера' });
    }
};

module.exports = {
    register,
    login,
    getMe
};