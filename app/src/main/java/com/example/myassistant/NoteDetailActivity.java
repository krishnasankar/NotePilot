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

public class NoteDetailActivity extends AppCompatActivity {

    private EditText editTextNoteTitle;
    private EditText editTextNoteContent;
    private Note note;
    private boolean isNewNote;
    private int notePosition;

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

        note = (Note) getIntent().getSerializableExtra("note");
        notePosition = getIntent().getIntExtra("notePosition", -1);
        isNewNote = notePosition == -1;

        if (note != null) {
            editTextNoteTitle.setText(note.getTitle());
            editTextNoteContent.setText(note.getContent());

            if (isNewNote) {
                // New note: focus on content and show keyboard
                editTextNoteContent.requestFocus();
                new Handler().postDelayed(() -> {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(editTextNoteContent, InputMethodManager.SHOW_IMPLICIT);
                }, 100);
            } else {
                // Existing note: move cursor to the end of content
                if (!TextUtils.isEmpty(note.getContent())) {
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
                // Disable the callback to avoid a loop, and trigger the default back action
                finish();
            }
        });
    }

    private void saveNote() {
        String title = editTextNoteTitle.getText().toString();
        String content = editTextNoteContent.getText().toString();

        if (isNewNote && TextUtils.isEmpty(title) && TextUtils.isEmpty(content)) {
            setResult(Activity.RESULT_CANCELED);
        } else {
            if (note == null) {
                note = new Note(title, content, 0);
            }
            note.setTitle(title);
            note.setContent(content);

            Intent resultIntent = new Intent();
            resultIntent.putExtra("note", note);
            resultIntent.putExtra("notePosition", notePosition);
            setResult(Activity.RESULT_OK, resultIntent);
        }
    }

    private void deleteNote() {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("notePosition", notePosition);
        resultIntent.putExtra("deleteNote", true);
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
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
        });

        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
    }
}
