package com.example.myassistant;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import io.noties.markwon.Markwon;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnMessageActionListener {
        void onSpeak(ChatMessage message, int position);
        void onCopy(ChatMessage message);
        void onSaveAsNote(ChatMessage message);
    }

    private static final int VIEW_TYPE_LOADING = 99;

    private final List<ChatMessage> messages;
    private final Markwon markwon;
    private OnMessageActionListener actionListener;
    private int currentlySpeakingPosition = -1;
    private boolean isLoading = false;

    public ChatAdapter(List<ChatMessage> messages, Markwon markwon) {
        this(messages, markwon, null);
    }

    public ChatAdapter(List<ChatMessage> messages, Markwon markwon, OnMessageActionListener actionListener) {
        this.messages = messages;
        this.markwon = markwon;
        this.actionListener = actionListener;
    }

    public void setOnMessageActionListener(OnMessageActionListener listener) {
        this.actionListener = listener;
    }

    public void setLoading(boolean loading) {
        if (this.isLoading != loading) {
            this.isLoading = loading;
            if (loading) {
                notifyItemInserted(messages.size());
            } else {
                notifyItemRemoved(messages.size());
            }
        }
    }

    public boolean isLoading() {
        return isLoading;
    }

    public void setCurrentlySpeakingPosition(int position) {
        int oldPos = currentlySpeakingPosition;
        currentlySpeakingPosition = position;
        if (oldPos >= 0 && oldPos < messages.size()) {
            notifyItemChanged(oldPos);
        }
        if (position >= 0 && position < messages.size()) {
            notifyItemChanged(position);
        }
    }

    public int getCurrentlySpeakingPosition() {
        return currentlySpeakingPosition;
    }

    @Override
    public int getItemViewType(int position) {
        if (isLoading && position == messages.size()) {
            return VIEW_TYPE_LOADING;
        }
        return messages.get(position).getAuthor().ordinal();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_LOADING) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_loading, parent, false);
            return new LoadingViewHolder(view);
        } else if (viewType == ChatMessage.Author.USER.ordinal()) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_user, parent, false);
            return new ChatViewHolder(view, markwon);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
            return new ChatViewHolder(view, markwon);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ChatViewHolder) {
            ChatMessage message = messages.get(position);
            boolean isSpeaking = (position == currentlySpeakingPosition);
            ((ChatViewHolder) holder).bind(message, position, isSpeaking, actionListener);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size() + (isLoading ? 1 : 0);
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageContent;
        private final Markwon markwon;
        private final ImageButton buttonSpeak;
        private final ImageButton buttonCopy;
        private final ImageButton buttonSaveNote;

        public ChatViewHolder(@NonNull View itemView, Markwon markwon) {
            super(itemView);
            this.messageContent = itemView.findViewById(R.id.chat_message_content);
            this.markwon = markwon;
            this.buttonSpeak = itemView.findViewById(R.id.buttonSpeak);
            this.buttonCopy = itemView.findViewById(R.id.buttonCopy);
            this.buttonSaveNote = itemView.findViewById(R.id.buttonSaveNote);
        }

        public void bind(ChatMessage message, int position, boolean isSpeaking, OnMessageActionListener listener) {
            if (message.getAuthor() == ChatMessage.Author.USER) {
                messageContent.setText(message.getContent());
            } else {
                markwon.setMarkdown(messageContent, message.getContent());
                if (buttonSpeak != null) {
                    buttonSpeak.setImageResource(isSpeaking ? R.drawable.ic_stop_24 : R.drawable.ic_volume_up_24);
                    buttonSpeak.setContentDescription(isSpeaking ? "Stop speaking" : "Read aloud");
                    buttonSpeak.setOnClickListener(v -> {
                        if (listener != null) listener.onSpeak(message, position);
                    });
                }
                if (buttonCopy != null) {
                    buttonCopy.setOnClickListener(v -> {
                        if (listener != null) listener.onCopy(message);
                    });
                }
                if (buttonSaveNote != null) {
                    buttonSaveNote.setOnClickListener(v -> {
                        if (listener != null) listener.onSaveAsNote(message);
                    });
                }
            }
        }
    }

    static class LoadingViewHolder extends RecyclerView.ViewHolder {
        public LoadingViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
