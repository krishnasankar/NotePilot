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
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {

    private final List<Note> notes;
    private final Context context;
    private final OnNoteClickListener listener;

    public interface OnNoteClickListener {
        void onNoteClick(Note note, int position);
        void onPinClick(Note note, int position);
    }

    public NoteAdapter(List<Note> notes, Context context, OnNoteClickListener listener) {
        this.notes = notes;
        this.context = context;
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return notes.get(position).hashCode();
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
                : note.getContent();
        holder.textViewNoteTitle.setText(displayTitle);
        holder.itemView.setBackgroundColor(note.getColor());

        holder.buttonPinNote.setImageResource(note.isPinned() ? R.drawable.ic_pin_on : R.drawable.ic_pin_off);

        holder.buttonPinNote.setOnClickListener(v -> {
            listener.onPinClick(note, holder.getAdapterPosition());
        });

        holder.buttonDeleteNote.setOnClickListener(v -> {
            showDeleteConfirmationDialog(holder.getAdapterPosition());
        });
    }

    private void showDeleteConfirmationDialog(int position) {
        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_delete_note);

        Button buttonCancel = dialog.findViewById(R.id.buttonCancel);
        Button buttonDelete = dialog.findViewById(R.id.buttonDelete);

        buttonCancel.setOnClickListener(v -> dialog.dismiss());
        buttonDelete.setOnClickListener(v -> {
            if (position != RecyclerView.NO_POSITION) {
                notes.remove(position);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, notes.size());
                NotesStorage.saveNotes(context, notes);
            }
            dialog.dismiss();
        });

        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
    }


    @Override
    public int getItemCount() {
        return notes.size();
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
            itemView.setOnClickListener(v -> listener.onNoteClick(note, getAdapterPosition()));
        }
    }
}
