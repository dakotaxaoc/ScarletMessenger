package com.roxy.messengerapp.services;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.roxy.messengerapp.network.ApiClient;
import com.roxy.messengerapp.network.ApiService;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFirebaseMsgService";

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "Refreshed token: " + token);
        sendRegistrationToServer(token);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "From: " + remoteMessage.getFrom());
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
        }
    }

    private void sendRegistrationToServer(String token) {
        ApiClient.init(getApplicationContext());
        if (!com.roxy.messengerapp.network.ApiClient.isLoggedIn()) {
             Log.d(TAG, "User not logged in, skipping token save");
             return;
        }

        ApiService apiService = ApiClient.getApi();
        retrofit2.Call<Void> call = apiService.saveFcmToken(new com.roxy.messengerapp.network.ApiService.FcmTokenRequest(token));
        call.enqueue(new retrofit2.Callback<Void>() {
            @Override
            public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Token sent to server successfully");
                } else {
                    Log.e(TAG, "Failed to send token: " + response.code());
                }
            }

            @Override
            public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                Log.e(TAG, "Error sending token", t);
            }
        });
    }
}
