package com.example.myassistant;

import static android.app.Activity.RESULT_OK;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class NotesFragment extends Fragment {

    private RecyclerView recyclerViewNotes;
    private NoteAdapter noteAdapter;
    private final List<Note> notes = new ArrayList<>(); // The single source of truth.
    private int[] noteColors;

    private final ActivityResultLauncher<Intent> noteDetailLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    loadNotesAndDisplay();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notes, container, false);

        recyclerViewNotes = view.findViewById(R.id.recyclerViewNotes);
        FloatingActionButton fabAddNote = view.findViewById(R.id.fabAddNote);
        noteColors = getResources().getIntArray(R.array.note_colors);

        setupAdapter();

        fabAddNote.setOnClickListener(v -> {
            int randomColor = noteColors[new Random().nextInt(noteColors.length)];
            Note newNote = new Note("", "", randomColor);
            Intent intent = new Intent(getContext(), NoteDetailActivity.class);
            intent.putExtra("note", newNote);
            intent.putExtra("notePosition", -1);
            noteDetailLauncher.launch(intent);
        });

        return view;
    }

    private void setupAdapter() {
        noteAdapter = new NoteAdapter(getContext(), new NoteAdapter.OnNoteClickListener() {
            @Override
            public void onNoteClick(Note note, int position) {
                Intent intent = new Intent(getContext(), NoteDetailActivity.class);
                intent.putExtra("note", note);
                intent.putExtra("notePosition", position);
                noteDetailLauncher.launch(intent);
            }

            @Override
            public void onPinClick(Note note, int position) {
                int noteIndex = -1;
                for (int i = 0; i < notes.size(); i++) {
                    if (notes.get(i).getId() == note.getId()) {
                        noteIndex = i;
                        break;
                    }
                }

                if (noteIndex != -1) {
                    Note oldNote = notes.get(noteIndex);
                    Note newNote = new Note(oldNote);
                    newNote.setPinned(!oldNote.isPinned());
                    notes.set(noteIndex, newNote);

                    NotesStorage.saveNotes(getContext(), notes);
                    sortAndDisplay();
                }
            }

            @Override
            public void onDeleteClick(Note note, int position) {
                Note noteToRemove = null;
                for (Note noteInList : notes) {
                    if (noteInList.getId() == note.getId()) {
                        noteToRemove = noteInList;
                        break;
                    }
                }
                if (noteToRemove != null) {
                    notes.remove(noteToRemove);
                    NotesStorage.saveNotes(getContext(), notes);
                    sortAndDisplay();
                }
            }
        });
        recyclerViewNotes.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewNotes.setAdapter(noteAdapter);
    }

    private void sortAndDisplay() {
        Collections.sort(notes, (n1, n2) -> {
            if (n1.isPinned() && !n2.isPinned()) return -1;
            if (!n1.isPinned() && n2.isPinned()) return 1;
            if (n1.isPinned() && n2.isPinned()) return Long.compare(n2.getPinnedTimestamp(), n1.getPinnedTimestamp());
            return Long.compare(n2.getLastModified(), n1.getLastModified());
        });
        noteAdapter.submitList(new ArrayList<>(notes));
    }

    private void loadNotesAndDisplay() {
        if (getContext() == null) return;

        List<Note> loadedNotes = NotesStorage.loadNotes(getContext());
        if (loadedNotes == null) {
            loadedNotes = new ArrayList<>();
        }

        if (loadedNotes.isEmpty()) {
            if (noteColors != null && noteColors.length > 0) {
                loadedNotes.add(new Note("Welcome to Notes!", "This is a sample note.", noteColors[0]));
                NotesStorage.saveNotes(getContext(), loadedNotes);
            }
        }

        this.notes.clear();
        this.notes.addAll(loadedNotes);

        sortAndDisplay();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadNotesAndDisplay();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            loadNotesAndDisplay();
        }
    }
}
