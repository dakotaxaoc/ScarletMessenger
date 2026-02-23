package com.roxy.messengerapp.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.roxy.messengerapp.R;
import com.roxy.messengerapp.adapters.ChatAdapter;
import com.roxy.messengerapp.entities.Chat;
import com.roxy.messengerapp.entities.Message;
import com.roxy.messengerapp.network.ApiClient;
import com.roxy.messengerapp.network.ApiService;
import com.roxy.messengerapp.network.SocketManager;
import com.roxy.messengerapp.utils.SwipeToDeleteCallback;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatsFragment extends Fragment {

    private RecyclerView recyclerView;
    private ChatAdapter chatAdapter;
    private List<Chat> chatList;
    private TextView emptyText;
    private SocketManager.OnMessageListener messageListener;
    private ProgressBar progressBar;
    private String currentUserId;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        chatList = new ArrayList<>();

        // Получаем ID текущего пользователя
        getCurrentUserAndLoadChats();

        // Слушаем новые сообщения
        setupSocketListener();
    }

    private void getCurrentUserAndLoadChats() {
        ApiClient.getApi().getMe().enqueue(new Callback<ApiService.UserResponse>() {
            @Override
            public void onResponse(Call<ApiService.UserResponse> call, Response<ApiService.UserResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    currentUserId = response.body().user.getId();
                    loadChats();
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

    private void loadChats() {
        ApiClient.getApi().getMyChats().enqueue(new Callback<ApiService.ChatsResponse>() {
            @Override
            public void onResponse(Call<ApiService.ChatsResponse> call, Response<ApiService.ChatsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    chatList.clear();
                    chatList.addAll(response.body().chats);
                    setupAdapter();
                }
            }

            @Override
            public void onFailure(Call<ApiService.ChatsResponse> call, Throwable t) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Ошибка загрузки чатов", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void setupAdapter() {
        chatAdapter = new ChatAdapter(chatList, currentUserId, new ChatAdapter.OnChatClickListener() {
            @Override
            public void onChatClick(Chat chat) {
                MessageChatFragment chatFragment = new MessageChatFragment();
                Bundle bundle = new Bundle();
                bundle.putString("chatId", chat.getId());

                if (chat.getType().equals("private")) {
                    var otherUser = chat.getOtherUser(currentUserId);
                    if (otherUser != null) {
                        bundle.putString("userId", otherUser.getId());
                        bundle.putString("username", otherUser.getUsername());
                        bundle.putString("imageURL", otherUser.getImageURL());
                    }
                } else {
                    bundle.putString("chatName", chat.getName());
                }
                chatFragment.setArguments(bundle);

                // Use Parent Fragment Manager (HomeFragment's manager)
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, chatFragment)
                        .addToBackStack(null) // This adds the "Back" capability
                        .commit();
            }
        });

        recyclerView.setAdapter(chatAdapter);

        ItemTouchHelper.SimpleCallback swipeCallback = new SwipeToDeleteCallback() {
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                Chat chat = chatAdapter.getChatAt(position);
                showDeleteChatConfirmation(chat, position);
            }
        };

        new ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView);
    }

    private void setupSocketListener() {
        messageListener = new SocketManager.OnMessageListener() {
            @Override
            public void onNewMessage(Message message) {
                // Обновляем список чатов при новом сообщении
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> loadChats());
                }
            }
        };
        SocketManager.getInstance().addMessageListener(messageListener);
    }

    private void showDeleteChatConfirmation(Chat chat, int position) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Удалить чат?")
                .setMessage("Все сообщения будут удалены навсегда")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    deleteChat(chat.getId(), position);
                })
                .setNegativeButton("Отмена", (dialog, which) -> {
                    // Restore the item (cancel swipe animation)
                    chatAdapter.notifyItemChanged(position);
                })
                .setOnCancelListener(dialog -> {
                    // Restore on outside tap
                    chatAdapter.notifyItemChanged(position);
                })
                .show();
    }

    private void deleteChat(String chatId, int position) {
        ApiClient.getApi().deleteChat(chatId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    chatAdapter.removeChat(position);
                    Toast.makeText(getContext(), "Чат удалён", Toast.LENGTH_SHORT).show();
                } else {
                    chatAdapter.notifyItemChanged(position);
                    Toast.makeText(getContext(), "Ошибка удаления", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                chatAdapter.notifyItemChanged(position);
                Toast.makeText(getContext(), "Ошибка сети", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (messageListener != null) {
            SocketManager.getInstance().removeMessageListener(messageListener);
        }
    }
}