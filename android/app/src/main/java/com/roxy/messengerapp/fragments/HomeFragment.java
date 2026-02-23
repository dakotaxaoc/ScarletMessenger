package com.roxy.messengerapp.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.roxy.messengerapp.R;
import com.roxy.messengerapp.activities.StartActivity;
import com.roxy.messengerapp.adapters.ViewPagerAdapter;
import com.roxy.messengerapp.entities.User;
import com.roxy.messengerapp.entities.UserManager;
import com.roxy.messengerapp.network.ApiClient;
import com.roxy.messengerapp.network.ApiService;
import com.roxy.messengerapp.network.SocketManager;

import java.io.IOException;
import java.io.InputStream;

import de.hdodenhof.circleimageview.CircleImageView;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HomeFragment extends Fragment {

    BottomNavigationView bottomNavigation;
    TextView headerTitle;

    private User currentUser;
    private final OkHttpClient okHttpClient = new OkHttpClient();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        bottomNavigation = view.findViewById(R.id.bottom_navigation);
        headerTitle = view.findViewById(R.id.header_title);
        // Load default fragment
        loadFragment(new ChatsFragment());
        headerTitle.setText("Chats");
        bottomNavigation.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            String title = "";
            int itemId = item.getItemId();
            if (itemId == R.id.nav_chats) {
                selectedFragment = new ChatsFragment();
                title = "Чаты";
            } else if (itemId == R.id.nav_users) {
                selectedFragment = new UsersFragment();
                title = "Люди";
            } else if (itemId == R.id.nav_profile) {
                selectedFragment = new ProfileFragment();
                title = "Мой профиль";
            }
            if (selectedFragment != null) {
                headerTitle.setText(title);
                loadFragment(selectedFragment);
            }
            return true;
        });
        getChildFragmentManager().addOnBackStackChangedListener(() -> {
            if (getChildFragmentManager().getBackStackEntryCount() > 0) {
                // We are inside a Chat -> Hide standard UI
                bottomNavigation.setVisibility(View.GONE);
                headerTitle.setVisibility(View.GONE);
            } else {
                // We are at the main list -> Show standard UI
                bottomNavigation.setVisibility(View.VISIBLE);
                headerTitle.setVisibility(View.VISIBLE);

                // Optional: Update title based on selected tab when coming back
                // (This ensures title doesn't stay stuck if logic gets complex)
                int selectedId = bottomNavigation.getSelectedItemId();
                if (selectedId == R.id.nav_chats) headerTitle.setText("Чаты");
                else if (selectedId == R.id.nav_users) headerTitle.setText("Люди");
                else if (selectedId == R.id.nav_profile) headerTitle.setText("Мой профиль");
            }
        });
        // Pre-load current user data just for caching
        loadCurrentUser();
    }
    private void loadFragment(Fragment fragment) {
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private void loadCurrentUser() {
        ApiClient.getApi().getMe().enqueue(new retrofit2.Callback<ApiService.UserResponse>() {
            @Override
            public void onResponse(retrofit2.Call<ApiService.UserResponse> call, retrofit2.Response<ApiService.UserResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    currentUser = response.body().user;
                    UserManager.getInstance().setCurrentUser(currentUser);
                    // Обновляем UI
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiService.UserResponse> call, Throwable t) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Ошибка загрузки профиля", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // Получить текущего пользователя (для других фрагментов)
    public User getCurrentUser() {
        return currentUser;
    }

}