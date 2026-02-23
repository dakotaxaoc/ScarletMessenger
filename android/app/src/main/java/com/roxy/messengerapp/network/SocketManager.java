package com.roxy.messengerapp.network;

import android.util.Log;

import com.google.gson.Gson;
import com.roxy.messengerapp.entities.Message;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class SocketManager {
    private static final String TAG = "SocketManager";
    private static final String SERVER_URL = "https://e1e5-2a0d-b201-5000-ed50-798f-23b3-80ea-24f5.ngrok-free.app";

    private static SocketManager instance;
    private Socket socket;
    private Gson gson = new Gson();

    private List<OnMessageListener> messageListeners = new ArrayList<>();
    private List<OnMessageDeletedListener> messageDeletedListeners = new ArrayList<>();
    private OnTypingListener typingListener;
    private OnConnectionListener connectionListener;

    public interface OnMessageListener {
        void onNewMessage(Message message);
    }

    public interface OnMessageDeletedListener {
        void onMessageDeleted(String messageId, String chatId);
    }

    public interface OnTypingListener {
        void onUserTyping(String chatId, String userId);
    }

    public interface OnConnectionListener {
        void onConnected();
        void onDisconnected();
        void onError(String error);
    }

    private SocketManager() {}

    public static SocketManager getInstance() {
        if (instance == null) {
            instance = new SocketManager();
        }
        return instance;
    }

    public void connect(String token) {
        try {
            IO.Options options = new IO.Options();
            options.auth = new java.util.HashMap<>();
            options.auth.put("token", token);
            options.reconnection = true;
            options.reconnectionAttempts = 5;

            socket = IO.socket(SERVER_URL, options);
            setupListeners();
            socket.connect();

            Log.d(TAG, "Socket connect() called");

        } catch (URISyntaxException e) {
            Log.e(TAG, "Socket URI error: " + e.getMessage());
        }
    }

    private void setupListeners() {
        if (socket != null) {
            socket.off(Socket.EVENT_CONNECT);
            socket.off(Socket.EVENT_DISCONNECT);
            socket.off(Socket.EVENT_CONNECT_ERROR);
            socket.off("new_message");
            socket.off("user_typing");
        }

        socket.on(Socket.EVENT_CONNECT, args -> {
            Log.d(TAG, "Socket connected");
            if (connectionListener != null) {
                connectionListener.onConnected();
            }
        });

        socket.on(Socket.EVENT_DISCONNECT, args -> {
            Log.d(TAG, "Socket disconnected");
            if (connectionListener != null) {
                connectionListener.onDisconnected();
            }
        });

        socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
            String error = args.length > 0 ? args[0].toString() : "Unknown error";
            Log.e(TAG, "Socket error: " + error);
            if (connectionListener != null) {
                connectionListener.onError(error);
            }
        });

        socket.on("new_message", args -> {
            if (args.length > 0) {
                try {
                    Message message = gson.fromJson(args[0].toString(), Message.class);
                    for (OnMessageListener listener : messageListeners) {
                        listener.onNewMessage(message);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Parse message error: " + e.getMessage());
                }
            }
        });

        socket.on("message_deleted", args -> {
            if (args.length > 0) {
                try {
                    org.json.JSONObject data = new org.json.JSONObject(args[0].toString());
                    String messageId = data.getString("messageId");
                    String chatId = data.getString("chatId");
                    for (OnMessageDeletedListener listener : messageDeletedListeners) {
                        listener.onMessageDeleted(messageId, chatId);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Parse delete error: " + e.getMessage());
                }
            }
        });


        socket.on("user_typing", args -> {
            if (args.length > 0 && typingListener != null) {
                try {
                    org.json.JSONObject data = new org.json.JSONObject(args[0].toString());
                    String chatId = data.getString("chatId");
                    String userId = data.getString("userId");
                    typingListener.onUserTyping(chatId, userId);
                } catch (Exception e) {
                    Log.e(TAG, "Parse typing error: " + e.getMessage());
                }
            }
        });
    }

    public void sendMessage(String chatId, String content) {
        sendMessage(chatId, content, "text");
    }

    public void sendMessage(String chatId, String content, String type) {
        if (socket != null && socket.connected()) {
            try {
                org.json.JSONObject data = new org.json.JSONObject();
                data.put("chatId", chatId);
                data.put("content", content);
                data.put("type", type);
                socket.emit("send_message", data);
            } catch (Exception e) {
                Log.e(TAG, "Send message error: " + e.getMessage());
            }
        }
    }

    public void sendTyping(String chatId) {
        if (socket != null && socket.connected()) {
            try {
                org.json.JSONObject data = new org.json.JSONObject();
                data.put("chatId", chatId);
                socket.emit("typing", data);
            } catch (Exception e) {
                Log.e(TAG, "Send typing error: " + e.getMessage());
            }
        }
    }

    public void deleteMessage(String chatId, String messageId) {
        if (socket != null && socket.connected()) {
            try {
                org.json.JSONObject data = new org.json.JSONObject();
                data.put("chatId", chatId);
                data.put("messageId", messageId);
                socket.emit("delete_message", data);
            } catch (Exception e) {
                Log.e(TAG, "Delete message error: " + e.getMessage());
            }
        }
    }

    public void markAsRead(String chatId) {
        if (socket != null && socket.connected()) {
            try {
                org.json.JSONObject data = new org.json.JSONObject();
                data.put("chatId", chatId);
                socket.emit("mark_read", data);
            } catch (Exception e) {
                Log.e(TAG, "Mark read error: " + e.getMessage());
            }
        }
    }

    public void joinChat(String chatId) {
        if (socket != null && socket.connected()) {
            try {
                org.json.JSONObject data = new org.json.JSONObject();
                data.put("chatId", chatId);
                socket.emit("join_chat", data);
            } catch (Exception e) {
                Log.e(TAG, "Join chat error: " + e.getMessage());
            }
        }
    }

    public void disconnect() {
        if (socket != null) {
            socket.disconnect();
            socket.off();
        }
    }

    public boolean isConnected() {
        return socket != null && socket.connected();
    }

    public void setTypingListener(OnTypingListener listener) {
        this.typingListener = listener;
    }

    public void setConnectionListener(OnConnectionListener listener) {
        this.connectionListener = listener;
    }

    public void addMessageListener(OnMessageListener listener) {
        if (!messageListeners.contains(listener)) {
            messageListeners.add(listener);
        }
    }

    public void removeMessageListener(OnMessageListener listener) {
        messageListeners.remove(listener);
    }

    public void addMessageDeletedListener(OnMessageDeletedListener listener) {
        if (!messageDeletedListeners.contains(listener)) {
            messageDeletedListeners.add(listener);
        }
    }
    public void removeMessageDeletedListener(OnMessageDeletedListener listener) {
        messageDeletedListeners.remove(listener);
    }

    public void removeTypingListener() {
        this.typingListener = null;
    }

    public void removeConnectionListener() {
        this.connectionListener = null;
    }
}