package com.roxy.messengerapp.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.roxy.messengerapp.R;
import com.roxy.messengerapp.entities.Message;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

public class MessageAdapter extends ListAdapter<Message, MessageAdapter.ViewHolder> {

    private static final int MSG_TYPE_LEFT = 0;
    private static final int MSG_TYPE_RIGHT = 1;

    private Context context;
    private String currentUserId;
    private String otherUserImageURL;


    public interface OnMessageLongClickListener {
        void onMessageLongClick(Message message, int position);
    }

    private OnMessageLongClickListener longClickListener;

    public void setOnMessageLongClickListener(OnMessageLongClickListener listener) {
        this.longClickListener = listener;
    }

    public MessageAdapter(Context context, String currentUserId, String otherUserImageURL) {
        super(new MessageDiffCallback());
        this.context = context;
        this.currentUserId = currentUserId;
        this.otherUserImageURL = otherUserImageURL;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == MSG_TYPE_RIGHT) {
            view = LayoutInflater.from(context).inflate(R.layout.chat_item_right, parent, false);
        } else {
            view = LayoutInflater.from(context).inflate(R.layout.chat_item_left, parent, false);
        }
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Message message = getItem(position);

        // Check if this is an image message
        boolean isImage = "image".equals(message.getType());

        if (isImage) {
            // Image message
            holder.showMessage.setVisibility(View.GONE);
            holder.imageMessage.setVisibility(View.VISIBLE);
            Glide.with(context)
                    .load(message.getContent())
                    .into(holder.imageMessage);
        } else {
            // Text message
            holder.showMessage.setVisibility(View.VISIBLE);
            holder.imageMessage.setVisibility(View.GONE);
            holder.showMessage.setText(message.getContent());
        }

        holder.timeTv.setText(formatTime(message.getCreatedAt()));

        // Avatar (only for left messages)
        if (holder.profileImage != null) {
            if (otherUserImageURL == null || otherUserImageURL.isEmpty()) {
                holder.profileImage.setImageResource(R.drawable.ic_profile_pic);
            } else {
                Glide.with(context)
                        .load(otherUserImageURL)
                        .into(holder.profileImage);
            }
        }

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onMessageLongClick(message, position);
            }
            return true;
        });

        // Seen indicator (only for right messages)
        if (holder.seenIndicator != null) {
            holder.seenIndicator.setVisibility(View.VISIBLE);
            if (message.isSeen()) {
                holder.seenIndicator.setImageResource(R.drawable.ic_seen);
            } else {
                holder.seenIndicator.setImageResource(R.drawable.ic_sent);
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        Message message = getItem(position);
        if (message.getSenderId() != null && message.getSenderId().equals(currentUserId)) {
            return MSG_TYPE_RIGHT;
        } else {
            return MSG_TYPE_LEFT;
        }
    }

    public void addMessage(Message message) {
        if (message == null || message.getId() == null) return;
        for (Message m : getCurrentList()) {
            if (m.getId().equals(message.getId())) {
                return; // Duplicate! Skip.
            }
        }
        List<Message> currentList = new ArrayList<>(getCurrentList());
        currentList.add(message);
        submitList(currentList);
    }

    public void setMessages(List<Message> messages) {
        submitList(new ArrayList<>(messages));
    }

    public void markAllAsRead() {
        List<Message> newList = new ArrayList<>();
        for (Message message : getCurrentList()) {
            message.setSeen(true);
            newList.add(message);
        }
        submitList(newList);
    }

    public void removeMessage(String messageId) {
        List<Message> newList = new ArrayList<>();
        for (Message m : getCurrentList()) {
            if (!m.getId().equals(messageId)) {
                newList.add(m);
            }
        }
        submitList(newList);
    }

    private String formatTime(String isoTime) {
        if (isoTime == null) return "";
        try {
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            SimpleDateFormat displayFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Date date = isoFormat.parse(isoTime);
            return displayFormat.format(date);
        } catch (ParseException e) {
            try {
                SimpleDateFormat isoFormat2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                SimpleDateFormat displayFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                Date date = isoFormat2.parse(isoTime);
                return displayFormat.format(date);
            } catch (ParseException e2) {
                return isoTime;
            }
        }
    }

    static class MessageDiffCallback extends DiffUtil.ItemCallback<Message> {
        @Override
        public boolean areItemsTheSame(@NonNull Message oldItem, @NonNull Message newItem) {
            return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Message oldItem, @NonNull Message newItem) {
            return oldItem.getContent().equals(newItem.getContent()) &&
                   oldItem.isSeen() == newItem.isSeen() &&
                   (oldItem.getType() == null ? newItem.getType() == null : oldItem.getType().equals(newItem.getType()));
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView showMessage;
        TextView timeTv;
        ImageView imageMessage;
        CircleImageView profileImage;
        ImageView seenIndicator;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            showMessage = itemView.findViewById(R.id.show_message);
            timeTv = itemView.findViewById(R.id.time_tv);
            imageMessage = itemView.findViewById(R.id.image_message);
            profileImage = itemView.findViewById(R.id.profile_image);
            seenIndicator = itemView.findViewById(R.id.seen_indicator);
        }
    }
}