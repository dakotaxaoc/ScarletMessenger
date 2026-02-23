package com.roxy.messengerapp.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.roxy.messengerapp.R;
import com.roxy.messengerapp.activities.StartActivity;
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


public class ProfileFragment extends Fragment {

    CircleImageView profile_image;
    TextView username;
    Button btn_logout;
    ConstraintLayout avatarFragment;
    private final OkHttpClient okHttpClient = new OkHttpClient();
    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        uploadAvatarToS3(selectedImageUri);
                    }
                }
            }
    );

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        profile_image = view.findViewById(R.id.profile_image);
        username = view.findViewById(R.id.username);
        btn_logout = view.findViewById(R.id.btn_logout);
        avatarFragment = view.findViewById(R.id.avatar_container);
        
        View usernameContainer = view.findViewById(R.id.username_container);

        User user = UserManager.getInstance().getCurrentUser();
        if (user != null) {
            displayUserData(user);
        } else {
            // If cache is empty, try to load from network
            loadCurrentUser();
        }
        
        avatarFragment.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            pickImageLauncher.launch(intent);
        });
        
        if (usernameContainer != null) {
            usernameContainer.setOnClickListener(v -> showEditUsernameDialog());
        }

        btn_logout.setOnClickListener(v -> logout());
    }

    private void showEditUsernameDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle("Изменить имя");

        final android.widget.EditText input = new EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        User currentUser = UserManager.getInstance().getCurrentUser();
        if (currentUser != null) {
            input.setText(currentUser.getUsername());
        }
        
        // Add margins
        FrameLayout container = new android.widget.FrameLayout(requireContext());
        FrameLayout.LayoutParams params = new  FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = getResources().getDimensionPixelSize(R.dimen.dialog_margin);
        params.rightMargin = getResources().getDimensionPixelSize(R.dimen.dialog_margin);
        input.setLayoutParams(params);
        container.addView(input);

        builder.setView(container);

        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            String newUsername = input.getText().toString().trim();
            if (!newUsername.isEmpty() && newUsername.length() >= 3) {
                updateUsernameApi(newUsername);
            } else {
                Toast.makeText(getContext(), "Имя слишком короткое", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void updateUsernameApi(String newUsername) {
        ApiService.UpdateUsernameRequest request = new ApiService.UpdateUsernameRequest(newUsername);
        ApiClient.getApi().updateUsername(request).enqueue(new retrofit2.Callback<ApiService.UserResponse>() {
            @Override
            public void onResponse(retrofit2.Call<ApiService.UserResponse> call, retrofit2.Response<ApiService.UserResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    User updatedUser = response.body().user;
                    UserManager.getInstance().setCurrentUser(updatedUser);
                    
                    if (isAdded()) {
                        username.setText(updatedUser.getUsername());
                        Toast.makeText(getContext(), "Имя обновлено!", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(getContext(), "Ошибка: " + response.message(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiService.UserResponse> call, Throwable t) {
                Toast.makeText(getContext(), "Ошибка сети", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayUserData(User user) {
        if (user.getUsername() != null) {
            username.setText(user.getUsername());
        }
        
        String imageUrl = user.getImageURL();
        if (imageUrl == null || imageUrl.isEmpty() || imageUrl.equals("default")) {
            profile_image.setImageResource(R.drawable.ic_profile_pic);
        } else {
            if (getContext() != null) {
                Glide.with(getContext()).load(imageUrl).into(profile_image);
            }
        }
    }

    private void loadCurrentUser() {
        ApiClient.getApi().getMe().enqueue(new retrofit2.Callback<ApiService.UserResponse>() {
            @Override
            public void onResponse(retrofit2.Call<ApiService.UserResponse> call, retrofit2.Response<ApiService.UserResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    User currentUser = response.body().user;
                    UserManager.getInstance().setCurrentUser(currentUser);
                    if (isAdded()) {
                        displayUserData(currentUser);
                    }
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiService.UserResponse> call, Throwable t) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Не удалось загрузить профиль", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void uploadAvatarToS3(Uri imageUri) {
        Toast.makeText(getContext(), "Загрузка аватара...", Toast.LENGTH_SHORT).show();

        // Determine MIME type from URI
        String mimeType = requireContext().getContentResolver().getType(imageUri);
        if (mimeType == null || !mimeType.startsWith("image/")) {
            mimeType = "image/jpeg"; // Fallback
        }

        final String finalMimeType = mimeType;

        // Step 1: Get presigned URL for avatar from backend
        ApiClient.getApi().getPresignedUrlForAvatar(mimeType).enqueue(new retrofit2.Callback<ApiService.PresignedUrlResponse>() {
            @Override
            public void onResponse(retrofit2.Call<ApiService.PresignedUrlResponse> call, retrofit2.Response<ApiService.PresignedUrlResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String presignedUrl = response.body().presignedUrl;
                    String avatarUrl = response.body().imageUrl;

                    // Step 2: Upload avatar to S3
                    uploadToS3(imageUri, presignedUrl, avatarUrl, finalMimeType);
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

    private void uploadToS3(Uri imageUri, String presignedUrl, String finalAvatarUrl, String mimeType) {
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
                                // Step 3: Update avatar URL on backend
                                updateAvatarUrl(finalAvatarUrl);
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

    private void updateAvatarUrl(String avatarUrl) {
        ApiService.UpdateAvatarRequest request = new ApiService.UpdateAvatarRequest(avatarUrl);
        ApiClient.getApi().updateAvatar(request).enqueue(new retrofit2.Callback<ApiService.UserResponse>() {
            @Override
            public void onResponse(retrofit2.Call<ApiService.UserResponse> call, retrofit2.Response<ApiService.UserResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    User updatedUser = response.body().user;
                    UserManager.getInstance().setCurrentUser(updatedUser);

                    if (getContext() != null) {
                        Glide.with(getContext())
                                .load(updatedUser.getImageURL())
                                .into(profile_image);
                        Toast.makeText(getContext(), "Аватар обновлен!", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(getContext(), "Ошибка обновления профиля", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<ApiService.UserResponse> call, Throwable t) {
                Toast.makeText(getContext(), "Ошибка сети: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void logout() {
        SocketManager.getInstance().disconnect();
        ApiClient.clearToken(requireContext());
        Intent intent = new Intent(getActivity(), StartActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}