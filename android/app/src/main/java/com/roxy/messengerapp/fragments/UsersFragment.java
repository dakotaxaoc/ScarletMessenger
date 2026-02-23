package com.roxy.messengerapp.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.roxy.messengerapp.R;
import com.roxy.messengerapp.adapters.UserAdapter;
import com.roxy.messengerapp.entities.Chat;
import com.roxy.messengerapp.entities.User;
import com.roxy.messengerapp.network.ApiClient;
import com.roxy.messengerapp.network.ApiService;
import com.roxy.messengerapp.network.SocketManager;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UsersFragment extends Fragment {

    private RecyclerView recyclerView;
    private UserAdapter userAdapter;
    private List<User> mUsers;
    private EditText searchUsers;
    private ProgressBar progressBar;
    private String currentUserId;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_users, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.rvUsers);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        searchUsers = view.findViewById(R.id.search_users);
        mUsers = new ArrayList<>();

        // Получаем текущего пользователя и загружаем список
        getCurrentUser();

        // Поиск
        searchUsers.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (query.isEmpty()) {
                    loadAllUsers();
                } else {
                    searchUsers(query);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void getCurrentUser() {
        ApiClient.getApi().getMe().enqueue(new Callback<ApiService.UserResponse>() {
            @Override
            public void onResponse(Call<ApiService.UserResponse> call, Response<ApiService.UserResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    currentUserId = response.body().user.getId();
                    loadAllUsers();
                }
            }

            @Override
            public void onFailure(Call<ApiService.UserResponse> call, Throwable t) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Ошибка загрузки", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void loadAllUsers() {
        ApiClient.getApi().searchUsers("").enqueue(new Callback<ApiService.UsersResponse>() {
            @Override
            public void onResponse(Call<ApiService.UsersResponse> call, Response<ApiService.UsersResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    mUsers.clear();
                    // Фильтруем себя из списка
                    for (User user : response.body().users) {
                        if (!user.getId().equals(currentUserId)) {
                            mUsers.add(user);
                        }
                    }
                    setupAdapter();
                }
            }

            @Override
            public void onFailure(Call<ApiService.UsersResponse> call, Throwable t) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Ошибка загрузки пользователей", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void searchUsers(String query) {
        ApiClient.getApi().searchUsers(query).enqueue(new Callback<ApiService.UsersResponse>() {
            @Override
            public void onResponse(Call<ApiService.UsersResponse> call, Response<ApiService.UsersResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    mUsers.clear();
                    for (User user : response.body().users) {
                        if (!user.getId().equals(currentUserId)) {
                            mUsers.add(user);
                        }
                    }
                    setupAdapter();
                }
            }

            @Override
            public void onFailure(Call<ApiService.UsersResponse> call, Throwable t) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Ошибка поиска", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void setupAdapter() {
        userAdapter = new UserAdapter(mUsers, new UserAdapter.OnUserClickListener() {
            @Override
            public void onUserClick(User user) {
                // Создаём приватный чат и переходим в него
                createChatAndOpen(user);
            }
        }, false);
        recyclerView.setAdapter(userAdapter);
    }

    private void createChatAndOpen(User user) {
        ApiService.CreateChatRequest request = new ApiService.CreateChatRequest(user.getId());

        ApiClient.getApi().createPrivateChat(request).enqueue(new Callback<ApiService.ChatResponse>() {
            @Override
            public void onResponse(Call<ApiService.ChatResponse> call, Response<ApiService.ChatResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Chat chat = response.body().chat;
                    SocketManager.getInstance().joinChat(chat.getId());

                    // Prepare bundle
                    Bundle bundle = new Bundle();
                    bundle.putString("chatId", chat.getId());
                    bundle.putString("userId", user.getId());
                    bundle.putString("username", user.getUsername());
                    bundle.putString("imageURL", user.getImageURL());

                    // Open MessageChatFragment
                    MessageChatFragment chatFragment = new MessageChatFragment();
                    chatFragment.setArguments(bundle);

                    getParentFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, chatFragment)
                            .addToBackStack(null) // Enable Back Arrow navigation
                            .commit();

                } else {
                    Toast.makeText(getContext(), "Не удалось создать чат", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiService.ChatResponse> call, Throwable t) {
                Toast.makeText(getContext(), "Ошибка: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}