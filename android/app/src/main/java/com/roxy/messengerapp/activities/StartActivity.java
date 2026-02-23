package com.roxy.messengerapp.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.roxy.messengerapp.R;
import com.roxy.messengerapp.network.ApiClient;

import com.roxy.messengerapp.network.ApiService;
import com.roxy.messengerapp.network.SocketManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StartActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        // Инициализируем ApiClient (загружает токен из SharedPreferences)
        ApiClient.init(this);

        // Проверяем есть ли сохранённый токен
        if (ApiClient.isLoggedIn()) {
            // Токен есть — проверяем его валидность
            checkToken();
        } else {
            // Токена нет — показываем экран логина
            setContentView(R.layout.activity_start);
        }
    }

    private void checkToken() {
        ApiClient.getApi().getMe().enqueue(new Callback<ApiService.UserResponse>() {
            @Override
            public void onResponse(Call<ApiService.UserResponse> call, Response<ApiService.UserResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Токен валидный — подключаем сокет и идём в MainActivity
                    SocketManager.getInstance().connect(ApiClient.getToken());

                    Intent intent = new Intent(StartActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                } else {
                    // Токен протух — чистим и показываем логин
                    ApiClient.clearToken(StartActivity.this);
                    setContentView(R.layout.activity_start);
                }
            }

            @Override
            public void onFailure(Call<ApiService.UserResponse> call, Throwable t) {
                // Ошибка сети — всё равно показываем логин
                // (можно добавить офлайн режим потом)
                setContentView(R.layout.activity_start);
            }
        });
    }
}