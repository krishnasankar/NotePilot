package com.example.myassistant;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class NoteDetailActivity extends AppCompatActivity {

private EditText editTextNoteTitle;
private EditText editTextNoteContent;
private Note note;
private boolean isNewNote;
private Note originalNote;

private com.google.android.material.floatingactionbutton.FloatingActionButton fabToggleChecklist;
private RecyclerView recyclerViewChecklist;
private com.google.android.material.floatingactionbutton.FloatingActionButton fabAddItem;
private ChecklistAdapter checklistAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_detail);

        View root = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            int statusBarHeight = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), statusBarHeight, v.getPaddingRight(), v.getPaddingBottom());
            return windowInsets;
        });

editTextNoteTitle = findViewById(R.id.editTextNoteTitle);
editTextNoteContent = findViewById(R.id.editTextNoteContent);
FloatingActionButton fabDeleteNote = findViewById(R.id.fabDeleteNote);
fabToggleChecklist = findViewById(R.id.fabToggleChecklist);
recyclerViewChecklist = findViewById(R.id.recyclerViewChecklist);
fabAddItem = findViewById(R.id.fabAddItem);

        note = (Note) getIntent().getSerializableExtra("note");
        int notePosition = getIntent().getIntExtra("notePosition", -1);
        isNewNote = notePosition == -1;

if (note != null) {
    originalNote = new Note(note); // Make a copy for comparison
    editTextNoteTitle.setText(note.getTitle());
    if (note.isChecklist()) {
        checklistAdapter = new ChecklistAdapter(note.getChecklist());
        recyclerViewChecklist.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewChecklist.setAdapter(checklistAdapter);
        recyclerViewChecklist.setVisibility(View.VISIBLE);
        editTextNoteContent.setVisibility(View.GONE);
        fabAddItem.setVisibility(View.VISIBLE);
        fabToggleChecklist.setImageResource(R.drawable.ic_checkbox_on);
    } else {
        editTextNoteContent.setText(note.getContent());
        editTextNoteContent.setVisibility(View.VISIBLE);
        recyclerViewChecklist.setVisibility(View.GONE);
        fabAddItem.setVisibility(View.GONE);
        fabToggleChecklist.setImageResource(R.drawable.ic_checkbox_off);
    }

    fabToggleChecklist.setOnClickListener(v -> toggleMode());

    fabAddItem.setOnClickListener(v -> {
        if (note.isChecklist()) {
            checklistAdapter.addItem(new ChecklistItem("", false));
        }
    });

    if (isNewNote) {
        // New note: focus on content and show keyboard
        if (note.isChecklist()) {
            // For checklist, add first item and focus
            if (note.getChecklist().isEmpty()) {
                checklistAdapter.addItem(new ChecklistItem("", false));
            }
            // Focus on first item would require more code, skip for now
        } else {
            editTextNoteContent.requestFocus();
            new Handler().postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(editTextNoteContent, InputMethodManager.SHOW_IMPLICIT);
            }, 100);
        }
    } else {
        // Existing note: move cursor to the end of content
        if (!note.isChecklist() && !TextUtils.isEmpty(note.getContent())) {
            editTextNoteContent.setSelection(note.getContent().length());
        }
    }
}

fabDeleteNote.setOnClickListener(v -> showDeleteConfirmationDialog());


        // Handle back press
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                saveNote();
                finish();
            }
        });
    }

private void saveNote() {
    String title = editTextNoteTitle.getText().toString();

    if (note.isChecklist()) {
        // Checklist mode, content is empty or ignored
        note.setContent("");
        // checklist is already updated via adapter
    } else {
        String content = editTextNoteContent.getText().toString();
        note.setContent(content);
        note.setChecklist(new ArrayList<>()); // Clear checklist
    }
    note.setTitle(title);

    if (isNewNote && TextUtils.isEmpty(title) && 
        (note.isChecklist() ? note.getChecklist().isEmpty() : TextUtils.isEmpty(note.getContent()))) {
        setResult(Activity.RESULT_CANCELED);
        return;
    }

    if (!isNewNote && note.equals(originalNote)) {
        setResult(Activity.RESULT_CANCELED);
        return;
    }

    List<Note> notes = NotesStorage.loadNotes(this);
    if (notes == null) {
        notes = new ArrayList<>();
    }

    if (isNewNote) {
        notes.add(note);
    } else {
        boolean found = false;
        for (int i = 0; i < notes.size(); i++) {
            if (notes.get(i).getId() == note.getId()) {
                notes.set(i, note);
                found = true;
                break;
            }
        }
        if (!found) { // Should not happen with correct logic
            notes.add(note);
        }
    }
    NotesStorage.saveNotes(this, notes);
    setResult(Activity.RESULT_OK);
}

private void toggleMode() {
    if (note.isChecklist()) {
        // Switch to text mode
        StringBuilder sb = new StringBuilder();
        for (ChecklistItem item : note.getChecklist()) {
            sb.append(item.text).append("\n");
        }
        note.setContent(sb.toString());
        note.setChecklist(new ArrayList<>());
        note.setChecklist(false); // set isChecklist false
        editTextNoteContent.setText(note.getContent());
        editTextNoteContent.setVisibility(View.VISIBLE);
        recyclerViewChecklist.setVisibility(View.GONE);
        fabAddItem.setVisibility(View.GONE);
        fabToggleChecklist.setImageResource(R.drawable.ic_checkbox_off);
    } else {
        // Switch to checklist mode
        String content = editTextNoteContent.getText().toString();
        List<ChecklistItem> newChecklist = new ArrayList<>();
        if (!TextUtils.isEmpty(content)) {
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    newChecklist.add(new ChecklistItem(line, false));
                }
            }
        }
        note.setChecklist(newChecklist);
        note.setContent("");
        note.setChecklist(true); // set isChecklist true
        checklistAdapter = new ChecklistAdapter(note.getChecklist());
        recyclerViewChecklist.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewChecklist.setAdapter(checklistAdapter);
        recyclerViewChecklist.setVisibility(View.VISIBLE);
        editTextNoteContent.setVisibility(View.GONE);
        fabAddItem.setVisibility(View.VISIBLE);
        fabToggleChecklist.setImageResource(R.drawable.ic_checkbox_on);
    }
}

private void deleteNote() {
    if (!isNewNote) {
        List<Note> notes = NotesStorage.loadNotes(this);
        if (notes != null) {
            for (int i = 0; i < notes.size(); i++) {
                if (notes.get(i).getId() == note.getId()) {
                    notes.remove(i);
                    break;
                }
            }
            NotesStorage.saveNotes(this, notes);
        }
    }
    setResult(Activity.RESULT_OK);
}

    private void showDeleteConfirmationDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_delete_note);

        Button buttonCancel = dialog.findViewById(R.id.buttonCancel);
        Button buttonDelete = dialog.findViewById(R.id.buttonDelete);

        buttonCancel.setOnClickListener(v -> dialog.dismiss());
        buttonDelete.setOnClickListener(v -> {
            deleteNote();
            dialog.dismiss();
            finish();
        });

        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
    }
}
