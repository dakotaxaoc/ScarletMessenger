package com.roxy.messengerapp.activities;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.fragment.NavHostFragment;

import com.roxy.messengerapp.R;
import com.roxy.messengerapp.network.ApiClient;
import com.roxy.messengerapp.network.SocketManager;
import com.roxy.messengerapp.network.ApiService;
import android.content.pm.PackageManager;
import android.os.Build;
import android.Manifest;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Инициализируем API клиент (загружает токен)
        ApiClient.init(this);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            NavGraph navGraph = navController.getNavInflater().inflate(R.navigation.nav_auth);

            // Проверяем залогинен ли пользователь
            if (ApiClient.isLoggedIn()) {
                navGraph.setStartDestination(R.id.homeFragment);
            } else {
                navGraph.setStartDestination(R.id.registerFragment);
            }

            navController.setGraph(navGraph);
        }

        askNotificationPermission();
        logFcmToken();
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED) {
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            } else {
                requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 1);
            }
        }
    }

    private void logFcmToken() {
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(new OnCompleteListener<String>() {
                @Override
                public void onComplete(@NonNull Task<String> task) {
                    if (!task.isSuccessful()) return;
                    String token = task.getResult();
                    Log.d("MainActivity", "FCM Token: " + token);
                    sendTokenToServer(token);
                }
            });
    }

    private void sendTokenToServer(String token) {
        ApiService apiService = ApiClient.getApi();
        Call<Void> call = apiService.saveFcmToken(new ApiService.FcmTokenRequest(token));
        call.enqueue(new Callback<Void>() {
             @Override
             public void onResponse(Call<Void> call, Response<Void> response) {
                 if (response.isSuccessful()) Log.d("MainActivity", "Token saved");
                 else Log.e("MainActivity", "Failed to save token");
             }
             @Override
             public void onFailure(Call<Void> call, Throwable t) { Log.e("MainActivity", "Error", t); }
        });
    }

    // Обновляем онлайн статус через сокет
    @Override
    protected void onResume() {
        super.onResume();

        // Переподключаем сокет если отключился
        if (ApiClient.isLoggedIn() && !SocketManager.getInstance().isConnected()) {
            SocketManager.getInstance().connect(ApiClient.getToken());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Сокет сам отправит disconnect и сервер пометит offline
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // При закрытии приложения отключаем сокет
        SocketManager.getInstance().disconnect();
    }
}