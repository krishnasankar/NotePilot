package com.example.myassistant;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class NotesActivity extends AppCompatActivity {

    private RecyclerView recyclerViewNotes;
    private NoteAdapter noteAdapter;
    private List<Note> notes;
    private int[] noteColors;

    private final ActivityResultLauncher<Intent> noteDetailLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Note returnedNote = (Note) result.getData().getSerializableExtra("note");
                    int position = result.getData().getIntExtra("notePosition", -1);

                    if (position == -1) { // New note
                        notes.add(returnedNote);
                    } else { // Existing note
                        notes.set(position, returnedNote);
                    }
                    sortAndRefreshNotes();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);

        recyclerViewNotes = findViewById(R.id.recyclerViewNotes);
        FloatingActionButton fabAddNote = findViewById(R.id.fabAddNote);

        noteColors = getResources().getIntArray(R.array.note_colors);
        notes = NotesStorage.loadNotes(this);

        if (notes == null) {
            notes = new ArrayList<>();
        }

        if (notes.isEmpty()) {
            notes.add(new Note("Welcome to Notes!", "This is a sample note.", noteColors[0]));
        }

        noteAdapter = new NoteAdapter(notes, this, new NoteAdapter.OnNoteClickListener() {
            @Override
            public void onNoteClick(Note note, int position) {
                Intent intent = new Intent(NotesActivity.this, NoteDetailActivity.class);
                intent.putExtra("note", note);
                intent.putExtra("notePosition", position);
                noteDetailLauncher.launch(intent);
            }

            @Override
            public void onPinClick(Note note, int position) {
                note.setPinned(!note.isPinned());
                sortAndRefreshNotes();
            }
        });

        recyclerViewNotes.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewNotes.setAdapter(noteAdapter);

        sortAndRefreshNotes(); // Sort and refresh after the adapter is set

        fabAddNote.setOnClickListener(v -> {
            int randomColor = noteColors[new Random().nextInt(noteColors.length)];
            Note newNote = new Note("", "", randomColor);
            Intent intent = new Intent(NotesActivity.this, NoteDetailActivity.class);
            intent.putExtra("note", newNote);
            intent.putExtra("notePosition", -1);
            noteDetailLauncher.launch(intent);
        });
    }

    private void sortAndRefreshNotes() {
        if (notes == null) return;
        List<Note> sortedList = new ArrayList<>(notes);
        Collections.sort(sortedList, (n1, n2) -> {
            if (n1.isPinned() && !n2.isPinned()) {
                return -1;
            } else if (!n1.isPinned() && n2.isPinned()) {
                return 1;
            } else if (n1.isPinned() && n2.isPinned()) {
                return Long.compare(n2.getPinnedTimestamp(), n1.getPinnedTimestamp());
            } else {
                return Long.compare(n2.getLastModified(), n1.getLastModified());
            }
        });

        for (int i = 0; i < sortedList.size(); i++) {
            int oldPosition = notes.indexOf(sortedList.get(i));
            if (oldPosition != i) {
                notes.remove(oldPosition);
                notes.add(i, sortedList.get(i));
                noteAdapter.notifyItemMoved(oldPosition, i);
            }
        }
        noteAdapter.notifyItemRangeChanged(0, notes.size());
    }

    @Override
    protected void onPause() {
        super.onPause();
        NotesStorage.saveNotes(this, notes);
    }
}
