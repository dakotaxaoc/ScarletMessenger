package com.roxy.messengerapp.network;

import com.roxy.messengerapp.entities.Chat;
import com.roxy.messengerapp.entities.Message;
import com.roxy.messengerapp.entities.User;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {
    // ===== AUTH =====
    @POST("api/auth/register")
    Call<AuthResponse> register(@Body RegisterRequest request);

    @POST("api/auth/login")
    Call<AuthResponse> login(@Body LoginRequest request);

    @GET("api/auth/me")
    Call<UserResponse> getMe();

    // ===== CHATS =====
    @GET("api/chats")
    Call<ChatsResponse> getMyChats();

    @POST("api/chats/private")
    Call<ChatResponse> createPrivateChat(@Body CreateChatRequest request);

    @DELETE("api/chats/{chatId}")
    Call<Void> deleteChat(@Path("chatId") String chatId);

    @PUT("api/users/avatar")
    Call<UserResponse> updateAvatar(@Body UpdateAvatarRequest request);

    @GET("api/chats/{chatId}/messages")
    Call<MessagesResponse> getChatMessages(
            @Path("chatId") String chatId,
            @Query("limit") int limit,
            @Query("offset") int offset
    );

    // ===== USERS =====
    @GET("api/users/search")
    Call<UsersResponse> searchUsers(@Query("q") String query);

    @POST("api/users/fcm-token")
    Call<Void> saveFcmToken(@Body FcmTokenRequest request);

    @PUT("api/users/username")
    Call<UserResponse> updateUsername(@Body UpdateUsernameRequest request);

    // ===== Request/Response классы =====
    class FcmTokenRequest {
        public String fcmToken;
        public FcmTokenRequest(String fcmToken) {
            this.fcmToken = fcmToken;
        }
    }

    class RegisterRequest {
        public String username;
        public String email;
        public String password;
        public RegisterRequest(String username, String email, String password) {
            this.username = username;
            this.email = email;
            this.password = password;
        }
    }

    class LoginRequest {
        public String email;
        public String password;
        public LoginRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }
    }

    class CreateChatRequest {
        public String userId;
        public CreateChatRequest(String userId) {
            this.userId = userId;
        }
    }

    class AuthResponse {
        public String message;
        public User user;
        public String token;
    }

    class UserResponse {
        public User user;
    }

    class ChatsResponse {
        public List<Chat> chats;
    }

    class ChatResponse {
        public Chat chat;
        public boolean isNew;
    }

    class MessagesResponse {
        public List<Message> messages;
    }

    class UsersResponse {
        public List<User> users;
    }

    // ===== UPLOAD =====
    @GET("api/upload/presigned-url")
    Call<PresignedUrlResponse> getPresignedUrl(@Query("fileType") String fileType);

    @GET("api/upload/presigned-url-avatar")
    Call<PresignedUrlResponse> getPresignedUrlForAvatar(@Query("fileType") String fileType);

    class PresignedUrlResponse {
        public String presignedUrl;
        public String imageUrl;
        public String key;
    }

    class UpdateAvatarRequest {
        public String avatarUrl;
        public UpdateAvatarRequest(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }
    }

    class UpdateUsernameRequest {
        public String username;
        public UpdateUsernameRequest(String username) {
            this.username = username;
        }
    }
}
