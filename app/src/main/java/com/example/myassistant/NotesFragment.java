package com.example.myassistant;

import static android.app.Activity.RESULT_OK;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class NotesFragment extends Fragment {

    private RecyclerView recyclerViewNotes;
    private NoteAdapter noteAdapter;
    private final List<Note> notes = new ArrayList<>();
    private final List<Note> filteredNotes = new ArrayList<>();
    private int[] noteColors;

    private EditText editSearchNotes;
    private ImageButton buttonClearSearch;
    private View layoutEmptyNotes;
    private TextView textEmptyTitle;
    private TextView textEmptyDesc;
    private String currentSearchQuery = "";

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
        editSearchNotes = view.findViewById(R.id.editSearchNotes);
        buttonClearSearch = view.findViewById(R.id.buttonClearSearch);
        layoutEmptyNotes = view.findViewById(R.id.layoutEmptyNotes);
        textEmptyTitle = view.findViewById(R.id.textEmptyTitle);
        textEmptyDesc = view.findViewById(R.id.textEmptyDesc);
        View btnEmptyAddNote = view.findViewById(R.id.btnEmptyAddNote);

        noteColors = getResources().getIntArray(R.array.note_colors);

        setupAdapter();
        setupSearch();

        View.OnClickListener createNoteListener = v -> {
            Note newNote = new Note("", "", 0);
            Intent intent = new Intent(getContext(), NoteDetailActivity.class);
            intent.putExtra("note", newNote);
            intent.putExtra("notePosition", -1);
            noteDetailLauncher.launch(intent);
        };

        fabAddNote.setOnClickListener(createNoteListener);
        if (btnEmptyAddNote != null) {
            btnEmptyAddNote.setOnClickListener(createNoteListener);
        }

        return view;
    }

    private void setupSearch() {
        if (editSearchNotes == null) return;

        editSearchNotes.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s != null ? s.toString().trim() : "";
                if (buttonClearSearch != null) {
                    buttonClearSearch.setVisibility(currentSearchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                }
                filterAndDisplay();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        if (buttonClearSearch != null) {
            buttonClearSearch.setOnClickListener(v -> {
                editSearchNotes.setText("");
                currentSearchQuery = "";
                filterAndDisplay();
            });
        }
    }

    private void setupAdapter() {
        noteAdapter = new NoteAdapter(requireContext(), new NoteAdapter.OnNoteClickListener() {
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
                    sortAndDisplay(() -> {
                        if (newNote.isPinned() && recyclerViewNotes != null) {
                            recyclerViewNotes.scrollToPosition(0);
                        }
                    });
                }
            }

            @Override
            public void onDeleteClick(Note note, int position) {
                showDeleteConfirmationDialog(note);
            }
        });

        recyclerViewNotes.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewNotes.setAdapter(noteAdapter);
    }

    private void showDeleteConfirmationDialog(Note note) {
        if (getContext() == null || note == null) return;
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_delete_note, null);

        TextView textNoteTitlePreview = view.findViewById(R.id.textNoteTitlePreview);
        TextView textNoteSnippetPreview = view.findViewById(R.id.textNoteSnippetPreview);
        Button buttonCancel = view.findViewById(R.id.buttonCancel);
        Button buttonDelete = view.findViewById(R.id.buttonDelete);

        String title = note.getTitle();
        if (textNoteTitlePreview != null) {
            textNoteTitlePreview.setText(title != null && !title.trim().isEmpty() ? title : "Untitled Note");
        }

        String snippet = note.getContent();
        if (snippet == null || snippet.trim().isEmpty()) {
            if (note.isChecklist() && note.getChecklist() != null && !note.getChecklist().isEmpty()) {
                snippet = note.getChecklist().get(0).text;
            }
        }
        if (textNoteSnippetPreview != null) {
            if (snippet != null && !snippet.trim().isEmpty()) {
                textNoteSnippetPreview.setText(snippet);
                textNoteSnippetPreview.setVisibility(View.VISIBLE);
            } else {
                textNoteSnippetPreview.setVisibility(View.GONE);
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(view)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        buttonCancel.setOnClickListener(v -> dialog.dismiss());

        buttonDelete.setOnClickListener(v -> {
            dialog.dismiss();
            Note toRemove = null;
            for (Note n : notes) {
                if (n.getId() == note.getId()) {
                    toRemove = n;
                    break;
                }
            }
            if (toRemove != null) {
                notes.remove(toRemove);
                NotesStorage.saveNotes(getContext(), notes);
                filterAndDisplay();
            }
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88f);
            dialog.getWindow().setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void sortAndDisplay() {
        sortAndDisplay(null);
    }

    private void sortAndDisplay(Runnable callback) {
        Collections.sort(notes, (n1, n2) -> {
            if (n1.isPinned() && !n2.isPinned()) return -1;
            if (!n1.isPinned() && n2.isPinned()) return 1;
            if (n1.isPinned() && n2.isPinned()) return Long.compare(n2.getPinnedTimestamp(), n1.getPinnedTimestamp());
            return Long.compare(n2.getLastModified(), n1.getLastModified());
        });
        filterAndDisplay(callback);
    }

    private void filterAndDisplay() {
        filterAndDisplay(null);
    }

    private void filterAndDisplay(Runnable callback) {
        filteredNotes.clear();
        if (TextUtils.isEmpty(currentSearchQuery)) {
            filteredNotes.addAll(notes);
        } else {
            String query = currentSearchQuery.toLowerCase(Locale.getDefault());
            for (Note n : notes) {
                boolean match = false;
                if (n.getTitle() != null && n.getTitle().toLowerCase(Locale.getDefault()).contains(query)) {
                    match = true;
                }
                if (!match && !n.isChecklist() && n.getContent() != null && n.getContent().toLowerCase(Locale.getDefault()).contains(query)) {
                    match = true;
                }
                if (!match && n.isChecklist() && n.getChecklist() != null) {
                    for (ChecklistItem item : n.getChecklist()) {
                        if (item.text != null && item.text.toLowerCase(Locale.getDefault()).contains(query)) {
                            match = true;
                            break;
                        }
                    }
                }
                if (match) {
                    filteredNotes.add(n);
                }
            }
        }

        updateEmptyState();
        noteAdapter.submitList(new ArrayList<>(filteredNotes), callback);
    }

    private void updateEmptyState() {
        if (layoutEmptyNotes == null) return;

        if (notes.isEmpty()) {
            layoutEmptyNotes.setVisibility(View.VISIBLE);
            if (textEmptyTitle != null) textEmptyTitle.setText(R.string.empty_notes_title);
            if (textEmptyDesc != null) textEmptyDesc.setText(R.string.empty_notes_desc);
        } else if (filteredNotes.isEmpty() && !TextUtils.isEmpty(currentSearchQuery)) {
            layoutEmptyNotes.setVisibility(View.VISIBLE);
            if (textEmptyTitle != null) textEmptyTitle.setText(R.string.empty_search_title);
            if (textEmptyDesc != null) textEmptyDesc.setText(R.string.empty_search_desc);
        } else {
            layoutEmptyNotes.setVisibility(View.GONE);
        }
    }

    private void loadNotesAndDisplay() {
        if (getContext() == null) return;

        List<Note> loadedNotes = NotesStorage.loadNotes(getContext());
        if (loadedNotes == null) {
            loadedNotes = new ArrayList<>();
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
