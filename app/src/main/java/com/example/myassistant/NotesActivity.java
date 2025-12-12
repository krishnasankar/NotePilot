package com.example.myassistant;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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
                        noteAdapter.notifyItemInserted(notes.size() - 1);
                    } else { // Existing note
                        notes.set(position, returnedNote);
                        noteAdapter.notifyItemChanged(position);
                    }
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

        noteAdapter = new NoteAdapter(notes, this, (note, position) -> {
            Intent intent = new Intent(NotesActivity.this, NoteDetailActivity.class);
            intent.putExtra("note", note);
            intent.putExtra("notePosition", position);
            noteDetailLauncher.launch(intent);
        });
        recyclerViewNotes.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewNotes.setAdapter(noteAdapter);

        fabAddNote.setOnClickListener(v -> {
            int randomColor = noteColors[new Random().nextInt(noteColors.length)];
            Note newNote = new Note("", "", randomColor);
            Intent intent = new Intent(NotesActivity.this, NoteDetailActivity.class);
            intent.putExtra("note", newNote);
            intent.putExtra("notePosition", -1);
            noteDetailLauncher.launch(intent);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        NotesStorage.saveNotes(this, notes);
    }
}
