package com.roxy.messengerapp.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.roxy.messengerapp.R;
import com.roxy.messengerapp.adapters.MessageAdapter;
import com.roxy.messengerapp.entities.Message;
import com.roxy.messengerapp.network.ApiClient;
import com.roxy.messengerapp.network.ApiService;
import com.roxy.messengerapp.network.SocketManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MessageChatFragment extends Fragment {

    private CircleImageView profileImage;
    private TextView usernameText;
    private ImageButton btnSend;
    private ImageButton btnAttach;
    private EditText textSend;
    private TextView typingIndicator;
    private final Handler typingHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideTypingRunnable = () -> {
        if (typingIndicator != null) {
            typingIndicator.setVisibility(View.GONE);
        }
    };
    private RecyclerView recyclerView;

    private MessageAdapter messageAdapter;
    private SocketManager.OnMessageListener messageListener;
    private SocketManager.OnMessageDeletedListener messageDeletedListener;
    private List<Message> messageList;

    private String chatId;
    private String otherUserId;
    private String otherUsername;
    private String otherImageURL;
    private String currentUserId;

    private final OkHttpClient okHttpClient = new OkHttpClient();

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        uploadImageToS3(selectedImageUri);
                    }
                }
            }
    );

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_message_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Toolbar
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle("");
        ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        toolbar.setNavigationOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            } else {
                requireActivity().onBackPressed();
            }
        });

        // Views
        profileImage = view.findViewById(R.id.profile_image);
        usernameText = view.findViewById(R.id.username);
        btnSend = view.findViewById(R.id.btn_send);
        btnAttach = view.findViewById(R.id.btn_attach);
        textSend = view.findViewById(R.id.text_send);
        recyclerView = view.findViewById(R.id.recycler_view);
        typingIndicator = view.findViewById(R.id.typing_indicator);

        // RecyclerView
        messageList = new ArrayList<>();
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);

        // Get arguments
        if (getArguments() != null) {
            chatId = getArguments().getString("chatId");
            otherUserId = getArguments().getString("userId");
            otherUsername = getArguments().getString("username");
            otherImageURL = getArguments().getString("imageURL");
        }

        // Display other user's data
        usernameText.setText(otherUsername);
        if (otherImageURL == null || otherImageURL.isEmpty()) {
            profileImage.setImageResource(R.drawable.ic_profile_pic);
        } else {
            Glide.with(requireContext()).load(otherImageURL).into(profileImage);
        }

        // Get current user and load messages
        getCurrentUserAndLoadMessages();

        // Send message
        btnSend.setOnClickListener(v -> {
            String msg = textSend.getText().toString().trim();
            if (!msg.isEmpty()) {
                sendMessage(msg, "text");
                textSend.setText("");
            }
        });

        // Attach image
        btnAttach.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            pickImageLauncher.launch(intent);
        });

        // Typing indicator
        textSend.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    SocketManager.getInstance().sendTyping(chatId);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // Socket listener for new messages
        setupSocketListener();
    }

    private void uploadImageToS3(Uri imageUri) {
        Toast.makeText(getContext(), "Загрузка изображения...", Toast.LENGTH_SHORT).show();

        // Determine MIME type from URI
        String mimeType = requireContext().getContentResolver().getType(imageUri);
        if (mimeType == null || !mimeType.startsWith("image/")) {
            mimeType = "image/jpeg"; // Fallback
        }

        final String finalMimeType = mimeType;

        // Step 1: Get presigned URL from backend
        ApiClient.getApi().getPresignedUrl(mimeType).enqueue(new retrofit2.Callback<ApiService.PresignedUrlResponse>() {
            @Override
            public void onResponse(retrofit2.Call<ApiService.PresignedUrlResponse> call, retrofit2.Response<ApiService.PresignedUrlResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String presignedUrl = response.body().presignedUrl;
                    String imageUrl = response.body().imageUrl;

                    // Step 2: Upload image to S3
                    uploadToS3(imageUri, presignedUrl, imageUrl, finalMimeType);
                } else {
                    Toast.makeText(getContext(), "Ошибка получения URL", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiService.PresignedUrlResponse> call, Throwable t) {
                Toast.makeText(getContext(), "Ошибка сети: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void uploadToS3(Uri imageUri, String presignedUrl, String finalImageUrl, String mimeType) {
        try {
            InputStream inputStream = requireContext().getContentResolver().openInputStream(imageUri);
            byte[] imageBytes = new byte[inputStream.available()];
            inputStream.read(imageBytes);
            inputStream.close();

            RequestBody requestBody = RequestBody.create(MediaType.parse(mimeType), imageBytes);

            Request request = new Request.Builder()
                    .url(presignedUrl)
                    .put(requestBody)
                    .build();

            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (response.isSuccessful()) {
                                // Step 3: Send message with image URL
                                sendMessage(finalImageUrl, "image");
                                Toast.makeText(getContext(), "Изображение отправлено!", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getContext(), "Ошибка S3: " + response.code(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            });

        } catch (IOException e) {
            Toast.makeText(getContext(), "Ошибка чтения файла", Toast.LENGTH_SHORT).show();
        }
    }

    private void getCurrentUserAndLoadMessages() {
        ApiClient.getApi().getMe().enqueue(new retrofit2.Callback<ApiService.UserResponse>() {
            @Override
            public void onResponse(retrofit2.Call<ApiService.UserResponse> call, retrofit2.Response<ApiService.UserResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    currentUserId = response.body().user.getId();

                    // Initialize adapter
                    messageAdapter = new MessageAdapter(getContext(), currentUserId, otherImageURL);
                    recyclerView.setAdapter(messageAdapter);
                    messageAdapter.setOnMessageLongClickListener((message, position) -> {
                        showMessageActionsDialog(message);
                    });

                    // Load messages
                    loadMessages();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiService.UserResponse> call, Throwable t) {
                Toast.makeText(getContext(), "Ошибка загрузки", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadMessages() {
        android.util.Log.d("MessageChat", "=== LOADING MESSAGES ===");
        android.util.Log.d("MessageChat", "ChatId: " + chatId);
        ApiClient.getApi().getChatMessages(chatId, 50, 0).enqueue(new retrofit2.Callback<ApiService.MessagesResponse>() {
            @Override
            public void onResponse(retrofit2.Call<ApiService.MessagesResponse> call, retrofit2.Response<ApiService.MessagesResponse> response) {
                android.util.Log.d("MessageChat", "Response code: " + response.code());
                if (response.isSuccessful() && response.body() != null) {
                    android.util.Log.d("MessageChat", "Messages count: " + response.body().messages.size());

                    // Update list through adapter
                    messageAdapter.setMessages(response.body().messages);

                    // Scroll to bottom
                    if (!response.body().messages.isEmpty()) {
                        recyclerView.scrollToPosition(response.body().messages.size() - 1);
                    }

                    // Mark as read
                    SocketManager.getInstance().markAsRead(chatId);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiService.MessagesResponse> call, Throwable t) {
                Toast.makeText(getContext(), "Ошибка загрузки сообщений", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendMessage(String content, String type) {
        android.util.Log.d("MessageChat", "Sending message to chat: " + chatId);
        android.util.Log.d("MessageChat", "Content: " + content + ", Type: " + type);
        // Send through socket
        SocketManager.getInstance().sendMessage(chatId, content, type);
    }

    private void setupSocketListener() {
        android.util.Log.d("MessageChat", "=== SETTING UP SOCKET LISTENER ===");

        messageListener = new SocketManager.OnMessageListener() {
            @Override
            public void onNewMessage(Message message) {
                String incomingChatId = message.getChatId();

                if (incomingChatId != null && incomingChatId.equals(chatId)) {

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            messageAdapter.addMessage(message);
                            recyclerView.smoothScrollToPosition(messageAdapter.getItemCount());

                            // Mark as read if from other user
                            if (message.getSenderId() != null && !message.getSenderId().equals(currentUserId)) {
                                SocketManager.getInstance().markAsRead(chatId);
                            }
                        });
                    }
                } else {
                    android.util.Log.d("ChatFilter", "Ignoring message for chat: " + incomingChatId + ", we are in: " + chatId);
                }
            }
        };
        SocketManager.getInstance().addMessageListener(messageListener);

        android.util.Log.d("MessageChat", "Listener set!");

        SocketManager.getInstance().setTypingListener(new SocketManager.OnTypingListener() {
            @Override
            public void onUserTyping(String typingChatId, String userId) {
                if (typingChatId.equals(chatId) && !userId.equals(currentUserId)) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            typingIndicator.setVisibility(View.VISIBLE);

                            // Hide after 3 seconds of no typing
                            typingHandler.removeCallbacks(hideTypingRunnable);
                            typingHandler.postDelayed(hideTypingRunnable, 3000);
                        });
                    }
                }
            }
        });
        messageDeletedListener = (messageId, deletedChatId) -> {
            if (deletedChatId.equals(chatId)) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        messageAdapter.removeMessage(messageId);
                    });
                }
            }
        };
        SocketManager.getInstance().addMessageDeletedListener(messageDeletedListener);
    }

    private void showMessageActionsDialog(Message message) {
        boolean isOwnMessage = message.getSenderId() != null &&
                message.getSenderId().equals(currentUserId);

        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        // Copy option (for everyone)
        TextView copyOption = new TextView(requireContext());
        copyOption.setText("📋 Копировать");
        copyOption.setTextSize(18);
        copyOption.setPadding(16, 32, 16, 32);
        copyOption.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("message", message.getContent());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "Скопировано", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        layout.addView(copyOption);

        if (isOwnMessage) {
            // Edit option (only for own messages)
            TextView editOption = new TextView(requireContext());
            editOption.setText("✏️ Редактировать");
            editOption.setTextSize(18);
            editOption.setPadding(16, 32, 16, 32);
            editOption.setOnClickListener(v -> {
                // TODO: Implement edit later
                Toast.makeText(getContext(), "Редактирование скоро!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
            layout.addView(editOption);

            // Delete option (only for own messages)
            TextView deleteOption = new TextView(requireContext());
            deleteOption.setText("🗑️ Удалить");
            deleteOption.setTextSize(18);
            deleteOption.setPadding(16, 32, 16, 32);
            deleteOption.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            deleteOption.setOnClickListener(v -> {
                dialog.dismiss();
                showDeleteConfirmation(message);
            });
            layout.addView(deleteOption);
        }

        dialog.setContentView(layout);
        dialog.show();
    }

    private void showDeleteConfirmation(Message message) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Удалить сообщение?")
                .setMessage("Это действие нельзя отменить")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    SocketManager.getInstance().deleteMessage(chatId, message.getId());
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Remove typing callbacks to prevent updates after view destruction
        typingHandler.removeCallbacks(hideTypingRunnable);
        
        // Clean up socket listeners to prevent Memory Leaks
        if (messageListener != null) {
            SocketManager.getInstance().removeMessageListener(messageListener);
        }
        
        if (messageDeletedListener != null) {
            SocketManager.getInstance().removeMessageDeletedListener(messageDeletedListener);
        }
        
        SocketManager.getInstance().removeTypingListener();
    }
}