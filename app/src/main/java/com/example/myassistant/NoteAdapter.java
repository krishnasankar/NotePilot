package com.example.myassistant;

import android.app.AlertDialog;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {

    private final List<Note> notes;
    private final Context context;
    private final OnNoteClickListener listener;

    public interface OnNoteClickListener {
        void onNoteClick(Note note, int position);
    }

    public NoteAdapter(List<Note> notes, Context context, OnNoteClickListener listener) {
        this.notes = notes;
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        Note note = notes.get(position);
        holder.bind(note, listener);

        String displayTitle = !TextUtils.isEmpty(note.getTitle())
                ? note.getTitle()
                : getFirstLine(note.getContent());
        holder.textViewNoteTitle.setText(displayTitle);
        holder.itemView.setBackgroundColor(note.getColor());

        holder.buttonDeleteNote.setOnClickListener(v -> new AlertDialog.Builder(context)
                .setTitle("Delete Note")
                .setMessage("Are you sure you want to delete this note?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    int currentPosition = holder.getAdapterPosition();
                    if (currentPosition != RecyclerView.NO_POSITION) {
                        notes.remove(currentPosition);
                        notifyItemRemoved(currentPosition);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show());
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    private String getFirstLine(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        int firstNewLine = text.indexOf('\n');
        return firstNewLine == -1 ? text : text.substring(0, firstNewLine);
    }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView textViewNoteTitle;
        ImageButton buttonDeleteNote;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewNoteTitle = itemView.findViewById(R.id.textViewNoteTitle);
            buttonDeleteNote = itemView.findViewById(R.id.buttonDeleteNote);
        }

        public void bind(final Note note, final OnNoteClickListener listener) {
            itemView.setOnClickListener(v -> listener.onNoteClick(note, getAdapterPosition()));
        }
    }
}
