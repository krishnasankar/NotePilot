package com.example.myassistant;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import io.noties.markwon.Markwon;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private final List<ChatMessage> messages;
    private final Markwon markwon;

    public ChatAdapter(List<ChatMessage> messages, Markwon markwon) {
        this.messages = messages;
        this.markwon = markwon;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getAuthor().ordinal();
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == ChatMessage.Author.USER.ordinal()) {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_user, parent, false);
        } else {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        }
        return new ChatViewHolder(view, markwon);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageContent;
        private final Markwon markwon;

        public ChatViewHolder(@NonNull View itemView, Markwon markwon) {
            super(itemView);
            this.messageContent = itemView.findViewById(R.id.chat_message_content);
            this.markwon = markwon;
        }

        public void bind(ChatMessage message) {
            if (message.getAuthor() == ChatMessage.Author.USER) {
                messageContent.setText(message.getContent());
            } else {
                markwon.setMarkdown(messageContent, message.getContent());
            }
        }
    }
}
