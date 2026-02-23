# 🔴 ScarletMessenger
A full-stack real-time messenger application with an Android client and Node.js backend.
Built as a personal project to explore real-time communication, cloud storage, and mobile development.

https://github.com/user-attachments/assets/52428dfa-3d05-4f32-8e57-d09c3e175ab5

---
## ✨ Features
- **Real-time messaging** via WebSocket (Socket.io)
- **Image sharing** — upload and send images through AWS S3 (presigned URLs)
- **Push notifications** via Firebase Cloud Messaging
- **Online/offline status** — see who's online in real time
- **Typing indicator** — "user is typing..." in chat
- **Message deletion** — delete messages with real-time sync
- **User search** — find and start conversations with other users
- **Profile customization** — change username and avatar
- **Swipe-to-delete** chats
- **JWT authentication** — secure registration and login
---
## 🛠 Tech Stack
### Backend (`/backend`)
| Technology | Purpose |
|---|---|
| **Node.js + Express 5** | REST API server |
| **Socket.io** | Real-time bidirectional communication |
| **PostgreSQL + Sequelize** | Database and ORM |
| **JWT** | Authentication |
| **AWS S3** | Image storage (presigned URLs) |
| **Firebase Admin SDK** | Push notifications |
### Android (`/android`)
| Technology | Purpose |
|---|---|
| **Java** | Application language |
| **Retrofit** | HTTP client for REST API |
| **Socket.io Client** | Real-time messaging |
| **Navigation Component** | Fragment-based navigation |
| **Glide** | Image loading and caching |
| **Firebase Messaging** | Push notifications |
| **EncryptedSharedPreferences** | Secure token storage |
| **CircleImageView** | Circular avatar views |


## 🚀 Getting Started
### Prerequisites
- Node.js 18+
- PostgreSQL
- AWS S3 bucket
- Firebase project (for push notifications)
- Android Studio
### Backend Setup
```bash
cd backend
cp .env.example .env
# Fill in your environment variables in .env
npm install
npm run dev
