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
import com.google.android.material.button.MaterialButton;
import jp.wasabeef.richeditor.RichEditor;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class NoteDetailActivity extends AppCompatActivity {

    private EditText editTextNoteTitle;
    private RichEditor richEditor;
    private Note note;
    private boolean isNewNote;
    private Note originalNote;

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
        richEditor = findViewById(R.id.richEditor);
        FloatingActionButton fabDeleteNote = findViewById(R.id.fabDeleteNote);

        // Configure Rich Editor
        richEditor.setPlaceholder("Content");
        richEditor.setEditorFontColor(ContextCompat.getColor(this, R.color.textColorPrimary));
        richEditor.setEditorFontSize(16);
        richEditor.setPadding(0, 8, 0, 0);

        // Toolbar actions
        findViewById(R.id.btnUndo).setOnClickListener(v -> richEditor.undo());
        findViewById(R.id.btnRedo).setOnClickListener(v -> richEditor.redo());
        findViewById(R.id.btnBold).setOnClickListener(v -> richEditor.setBold());
        findViewById(R.id.btnItalic).setOnClickListener(v -> richEditor.setItalic());
        findViewById(R.id.btnUnderline).setOnClickListener(v -> richEditor.setUnderline());
        findViewById(R.id.btnStrike).setOnClickListener(v -> richEditor.setStrikeThrough());
        findViewById(R.id.btnH1).setOnClickListener(v -> richEditor.setHeading(1));
        findViewById(R.id.btnH2).setOnClickListener(v -> richEditor.setHeading(2));
        findViewById(R.id.btnBullets).setOnClickListener(v -> richEditor.setBullets());
        findViewById(R.id.btnNumbers).setOnClickListener(v -> richEditor.setNumbers());
        findViewById(R.id.btnQuote).setOnClickListener(v -> richEditor.setBlockquote());
        findViewById(R.id.btnLink).setOnClickListener(v -> {
            // Simple default link insertion; could be replaced by a dialog for URL/text
            richEditor.insertLink("https://", "link");
        });

        note = (Note) getIntent().getSerializableExtra("note");
        int notePosition = getIntent().getIntExtra("notePosition", -1);
        isNewNote = notePosition == -1;

        if (note != null) {
            originalNote = new Note(note); // Make a copy for comparison
            editTextNoteTitle.setText(note.getTitle());
            richEditor.setHtml(note.getContent());

            if (isNewNote) {
                // New note: focus on content and show keyboard
                richEditor.focusEditor();
                new Handler().postDelayed(() -> {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(richEditor, InputMethodManager.SHOW_IMPLICIT);
                }, 100);
            } else {
                // Existing note: optionally focus editor
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
        String content = richEditor.getHtml();
        if (content == null) content = "";

        if (isNewNote && TextUtils.isEmpty(title) && TextUtils.isEmpty(content)) {
            setResult(Activity.RESULT_CANCELED);
            return;
        }

        if (!isNewNote && originalNote != null &&
                title.equals(originalNote.getTitle()) &&
                content.equals(originalNote.getContent())) {
            setResult(Activity.RESULT_CANCELED);
            return;
        }

        note.setTitle(title);
        note.setContent(content);

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
