require('dotenv').config();

const express = require('express');
const http = require('http');
const cors = require('cors');
const { Server } = require('socket.io');
const { connectDB } = require('./config/database');
const authRoutes = require('./routes/auth');
const setupSocket = require('./socket/socketHandler');
const chatRoutes = require('./routes/chat');
const userRoutes = require('./routes/user');
const uploadRoutes = require('./routes/upload');


require('./models');

const app = express();
const server = http.createServer(app);

const io = new Server(server, {
    cors: {
        origin: '*', // потом ограничим
        methods: ['GET', 'POST']
    }
});

// Middleware
app.use(cors());
app.use(express.json());

app.use('/api/auth', authRoutes);
app.use('/api/chats', chatRoutes);
app.use('/api/users', userRoutes);
app.use('/uploads', express.static('uploads'));
app.use('/api/upload', uploadRoutes);

// Тестовый роут
app.get('/', (req, res) => {
    res.json({ message: 'Scarlet Messenger API' });
});

setupSocket(io);

// Запуск сервера
const PORT = process.env.PORT || 3000;

const start = async () => {
    await connectDB();

    server.listen(PORT, () => {
        console.log(`Server running on port ${PORT}`);
    });
};
start();

module.exports = { io };