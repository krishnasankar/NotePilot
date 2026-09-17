package com.example.myassistant;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NoteAdapter extends ListAdapter<Note, NoteAdapter.NoteViewHolder> {

    public interface OnNoteClickListener {
        void onNoteClick(Note note, int position);
        void onPinClick(Note note, int position);
        void onDeleteClick(Note note, int position);
    }

    private final Context context;
    private final OnNoteClickListener listener;

    public NoteAdapter(Context context, OnNoteClickListener listener) {
        super(DIFF_CALLBACK);
        this.context = context;
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Note> DIFF_CALLBACK = new DiffUtil.ItemCallback<Note>() {
        @Override
        public boolean areItemsTheSame(@NonNull Note oldItem, @NonNull Note newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Note oldItem, @NonNull Note newItem) {
            return oldItem.equals(newItem);
        }
    };

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        Note note = getItem(position);
        holder.bind(note, listener, context);
    }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        final View colorStrip;
        final TextView textViewNoteTitle;
        final TextView textViewNoteSnippet;
        final TextView textNoteDate;
        final TextView textChecklistProgress;
        final ImageButton buttonPinNote;
        final ImageButton buttonDeleteNote;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            colorStrip = itemView.findViewById(R.id.colorStrip);
            textViewNoteTitle = itemView.findViewById(R.id.textViewNoteTitle);
            textViewNoteSnippet = itemView.findViewById(R.id.textViewNoteSnippet);
            textNoteDate = itemView.findViewById(R.id.textNoteDate);
            textChecklistProgress = itemView.findViewById(R.id.textChecklistProgress);
            buttonPinNote = itemView.findViewById(R.id.buttonPinNote);
            buttonDeleteNote = itemView.findViewById(R.id.buttonDeleteNote);
        }

        public void bind(final Note note, final OnNoteClickListener listener, final Context context) {
            // Title
            String titleText = note.getTitle();
            if (TextUtils.isEmpty(titleText)) {
                if (note.isChecklist() && note.getChecklist() != null && !note.getChecklist().isEmpty()) {
                    titleText = note.getChecklist().get(0).text;
                } else if (!TextUtils.isEmpty(note.getContent())) {
                    String[] lines = note.getContent().split("\\R");
                    titleText = lines[0].trim();
                }
            }
            if (TextUtils.isEmpty(titleText)) {
                titleText = "Untitled Note";
            }
            textViewNoteTitle.setText(titleText);

            // Snippet Preview
            StringBuilder snippet = new StringBuilder();
            if (note.isChecklist()) {
                List<ChecklistItem> items = note.getChecklist();
                if (items != null && !items.isEmpty()) {
                    int shown = 0;
                    for (ChecklistItem item : items) {
                        if (item.text != null && !item.text.trim().isEmpty()) {
                            if (shown > 0) snippet.append("\n");
                            snippet.append(item.checked ? "☑ " : "☐ ").append(item.text.trim());
                            shown++;
                            if (shown >= 2) break;
                        }
                    }
                }
            } else {
                String content = note.getContent();
                if (!TextUtils.isEmpty(content)) {
                    String clean = content.replaceAll("(?m)^#{1,6}\\s+", "").trim();
                    snippet.append(clean);
                }
            }
            String snippetStr = snippet.toString().trim();
            if (snippetStr.isEmpty()) {
                textViewNoteSnippet.setVisibility(View.GONE);
            } else {
                textViewNoteSnippet.setVisibility(View.VISIBLE);
                textViewNoteSnippet.setText(snippetStr);
            }

            // Accent Strip
            int accentColor = note.getColor();
            if (accentColor == 0 || accentColor == Color.TRANSPARENT) {
                colorStrip.setVisibility(View.GONE);
            } else {
                colorStrip.setVisibility(View.VISIBLE);
                colorStrip.setBackgroundColor(accentColor);
            }

            // Date
            long time = note.getLastModified();
            if (time > 0) {
                String timeStr = formatRelativeTime(time);
                textNoteDate.setText(timeStr);
                textNoteDate.setVisibility(View.VISIBLE);
            } else {
                textNoteDate.setVisibility(View.GONE);
            }

            // Checklist Badge
            if (note.isChecklist() && note.getChecklist() != null && !note.getChecklist().isEmpty()) {
                int total = note.getChecklist().size();
                int checked = 0;
                for (ChecklistItem item : note.getChecklist()) {
                    if (item.checked) checked++;
                }
                textChecklistProgress.setText(context.getString(R.string.stat_items, checked, total));
                textChecklistProgress.setVisibility(View.VISIBLE);
            } else {
                textChecklistProgress.setVisibility(View.GONE);
            }

            // Pin State
            boolean pinned = note.isPinned();
            buttonPinNote.setImageResource(pinned ? R.drawable.ic_pin_on : R.drawable.ic_pin_off);
            int pinTint = pinned
                    ? ContextCompat.getColor(context, R.color.status_warning)
                    : ContextCompat.getColor(context, R.color.textColorTertiary);
            buttonPinNote.setImageTintList(ColorStateList.valueOf(pinTint));
            buttonPinNote.setContentDescription(context.getString(pinned ? R.string.action_unpin_note : R.string.action_pin_note));

            // Click Listeners
            itemView.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onNoteClick(note, pos);
                }
            });

            buttonPinNote.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onPinClick(note, pos);
                }
            });

            buttonDeleteNote.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onDeleteClick(note, pos);
                }
            });
        }

        private String formatRelativeTime(long timestamp) {
            long now = System.currentTimeMillis();
            long diff = Math.abs(now - timestamp);

            if (diff < DateUtils.MINUTE_IN_MILLIS) {
                return "Just now";
            } else if (DateUtils.isToday(timestamp)) {
                return new SimpleDateFormat("'Today', h:mm a", Locale.getDefault()).format(new Date(timestamp));
            } else if (diff < 2 * DateUtils.DAY_IN_MILLIS && !DateUtils.isToday(timestamp)) {
                return new SimpleDateFormat("'Yesterday', h:mm a", Locale.getDefault()).format(new Date(timestamp));
            } else if (diff < 7 * DateUtils.DAY_IN_MILLIS) {
                return new SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(new Date(timestamp));
            } else {
                return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date(timestamp));
            }
        }
    }
}
