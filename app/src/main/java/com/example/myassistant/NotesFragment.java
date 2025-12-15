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
    private List<Note> notes;
    private int[] noteColors;

    private final ActivityResultLauncher<Intent> noteDetailLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    boolean deleteNote = result.getData().getBooleanExtra("deleteNote", false);
                    int position = result.getData().getIntExtra("notePosition", -1);

                    if (deleteNote && position != -1) {
                        notes.remove(position);
                        noteAdapter.notifyItemRemoved(position);
                        noteAdapter.notifyItemRangeChanged(position, notes.size());
                        NotesStorage.saveNotes(getContext(), notes);
                    } else {
                        Note returnedNote = (Note) result.getData().getSerializableExtra("note");
                        if (returnedNote != null) {
                            if (position == -1) { // New note
                                notes.add(returnedNote);
                            } else { // Existing note
                                notes.set(position, returnedNote);
                            }
                            sortAndRefreshNotes();
                        }
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notes, container, false);

        recyclerViewNotes = view.findViewById(R.id.recyclerViewNotes);
        FloatingActionButton fabAddNote = view.findViewById(R.id.fabAddNote);
        noteColors = getResources().getIntArray(R.array.note_colors);

        notes = NotesStorage.loadNotes(getContext());

        if (notes == null) {
            notes = new ArrayList<>();
        }

        if (notes.isEmpty()) {
            notes.add(new Note("Welcome to Notes!", "This is a sample note.", noteColors[0]));
        }

        noteAdapter = new NoteAdapter(notes, getContext(), new NoteAdapter.OnNoteClickListener() {
            @Override
            public void onNoteClick(Note note, int position) {
                Intent intent = new Intent(getContext(), NoteDetailActivity.class);
                intent.putExtra("note", note);
                intent.putExtra("notePosition", position);
                noteDetailLauncher.launch(intent);
            }

            @Override
            public void onPinClick(Note note, int position) {
                note.setPinned(!note.isPinned());
                sortAndRefreshNotes(); // Resort the current list
                if (getContext() != null) {
                    // Save the entire list to persist the pin change
                    NotesStorage.saveNotes(getContext(), notes);
                }
            }
        });

        recyclerViewNotes.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewNotes.setAdapter(noteAdapter);

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

    private void loadAndRefreshNotes() {
        if (getContext() == null || notes == null || noteAdapter == null) return;

        List<Note> reloadedNotes = NotesStorage.loadNotes(getContext());
        notes.clear();
        notes.addAll(reloadedNotes);
        sortAndRefreshNotes();
        NotesStorage.saveNotes(getContext(), notes);
    }

    @Override
    public void onResume() {
        super.onResume();
        NotesStorage.saveNotes(getContext(), notes);
        loadAndRefreshNotes();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            loadAndRefreshNotes();
        }
    }

}
