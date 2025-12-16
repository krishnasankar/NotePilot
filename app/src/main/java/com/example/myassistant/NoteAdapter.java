package com.example.myassistant;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

public class NoteAdapter extends ListAdapter<Note, NoteAdapter.NoteViewHolder> {

    private final Context context;
    private final OnNoteClickListener listener;

    public interface OnNoteClickListener {
        void onNoteClick(Note note, int position);
        void onPinClick(Note note, int position);
        void onDeleteClick(Note note, int position);
    }

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
    holder.bind(note, listener);

    String displayText;
    if (note.isChecklist()) {
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(note.getTitle())) {
            sb.append(note.getTitle()).append("\n");
        }
        for (ChecklistItem item : note.getChecklist()) {
            sb.append(item.checked ? "✓ " : "- ").append(item.text).append("\n");
        }
        displayText = sb.toString().trim();
    } else {
        displayText = !TextUtils.isEmpty(note.getTitle())
                ? note.getTitle()
                : note.getContent();
    }
    holder.textViewNoteTitle.setText(displayText);
    holder.textViewNoteTitle.setMaxLines(note.isChecklist() ? Integer.MAX_VALUE : 2);
    holder.itemView.setBackgroundColor(note.getColor());

    holder.buttonPinNote.setImageResource(note.isPinned() ? R.drawable.ic_pin_on : R.drawable.ic_pin_off);

    holder.buttonPinNote.setOnClickListener(v -> {
        if (holder.getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
            listener.onPinClick(note, holder.getBindingAdapterPosition());
        }
    });

    holder.buttonDeleteNote.setOnClickListener(v -> {
        if (holder.getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
            showDeleteConfirmationDialog(note, holder.getBindingAdapterPosition());
        }
    });
}

    private void showDeleteConfirmationDialog(Note note, int position) {
        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_delete_note);

        Button buttonCancel = dialog.findViewById(R.id.buttonCancel);
        Button buttonDelete = dialog.findViewById(R.id.buttonDelete);

        buttonCancel.setOnClickListener(v -> dialog.dismiss());
        buttonDelete.setOnClickListener(v -> {
            if (position != RecyclerView.NO_POSITION) {
                listener.onDeleteClick(note, position);
            }
            dialog.dismiss();
        });

        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
    }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView textViewNoteTitle;
        ImageButton buttonPinNote;
        ImageButton buttonDeleteNote;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewNoteTitle = itemView.findViewById(R.id.textViewNoteTitle);
            buttonPinNote = itemView.findViewById(R.id.buttonPinNote);
            buttonDeleteNote = itemView.findViewById(R.id.buttonDeleteNote);
        }

        public void bind(final Note note, final OnNoteClickListener listener) {
            itemView.setOnClickListener(v -> {
                if (getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onNoteClick(note, getBindingAdapterPosition());
                }
            });
        }
    }
}
