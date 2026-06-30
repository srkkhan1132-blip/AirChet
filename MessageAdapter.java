package com.airchat.app.adapter;

import android.graphics.BitmapFactory;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.airchat.app.R;
import com.airchat.app.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VT_SENT_TEXT = 0;
    private static final int VT_RECEIVED_TEXT = 1;
    private static final int VT_SYSTEM = 2;
    private static final int VT_SENT_IMAGE = 3;
    private static final int VT_RECEIVED_IMAGE = 4;

    private final List<ChatMessage> messages = new ArrayList<>();
    private final boolean isGroupChat;

    public MessageAdapter(boolean isGroupChat) {
        this.isGroupChat = isGroupChat;
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage m = messages.get(position);
        if (m.getType() == ChatMessage.TYPE_SYSTEM) return VT_SYSTEM;
        boolean isImage = m.getContentType() == ChatMessage.CONTENT_IMAGE;
        boolean isSent = m.getType() == ChatMessage.TYPE_SENT;
        if (isImage) return isSent ? VT_SENT_IMAGE : VT_RECEIVED_IMAGE;
        return isSent ? VT_SENT_TEXT : VT_RECEIVED_TEXT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case VT_SENT_TEXT:
                return new TextVH(inflater.inflate(R.layout.item_message_sent, parent, false));
            case VT_RECEIVED_TEXT:
                return new TextVH(inflater.inflate(R.layout.item_message_received, parent, false));
            case VT_SENT_IMAGE:
                return new ImageVH(inflater.inflate(R.layout.item_message_image_sent, parent, false));
            case VT_RECEIVED_IMAGE:
                return new ImageVH(inflater.inflate(R.layout.item_message_image_received, parent, false));
            default:
                return new SystemVH(inflater.inflate(R.layout.item_message_system, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage m = messages.get(position);
        String time = DateFormat.format("h:mm a", m.getTimestamp()).toString();

        if (holder instanceof TextVH) {
            TextVH vh = (TextVH) holder;
            vh.text.setText(m.getText());
            vh.time.setText(time);
            if (vh.sender != null) {
                if (isGroupChat && m.getType() == ChatMessage.TYPE_RECEIVED) {
                    vh.sender.setVisibility(View.VISIBLE);
                    vh.sender.setText(m.getSenderName());
                } else {
                    vh.sender.setVisibility(View.GONE);
                }
            }
        } else if (holder instanceof ImageVH) {
            ImageVH vh = (ImageVH) holder;
            vh.time.setText(time);
            if (m.getImagePath() != null) {
                vh.image.setImageBitmap(BitmapFactory.decodeFile(m.getImagePath()));
            }
        } else if (holder instanceof SystemVH) {
            ((SystemVH) holder).text.setText(m.getText());
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class TextVH extends RecyclerView.ViewHolder {
        TextView text, time, sender;

        TextVH(View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.tvMessageText);
            time = itemView.findViewById(R.id.tvMessageTime);
            sender = itemView.findViewById(R.id.tvSenderName);
        }
    }

    static class ImageVH extends RecyclerView.ViewHolder {
        ImageView image;
        TextView time;

        ImageVH(View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.ivImage);
            time = itemView.findViewById(R.id.tvMessageTime);
        }
    }

    static class SystemVH extends RecyclerView.ViewHolder {
        TextView text;

        SystemVH(View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.tvSystemText);
        }
    }
}
