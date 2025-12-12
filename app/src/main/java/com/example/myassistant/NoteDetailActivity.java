package com.example.myassistant;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

public class NoteDetailActivity extends AppCompatActivity {

    private EditText editTextNoteTitle;
    private EditText editTextNoteContent;
    private Note note;
    private boolean isNewNote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_detail);

        editTextNoteTitle = findViewById(R.id.editTextNoteTitle);
        editTextNoteContent = findViewById(R.id.editTextNoteContent);

        note = (Note) getIntent().getSerializableExtra("note");
        isNewNote = getIntent().getIntExtra("notePosition", -1) == -1;

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

        // Handle back press
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                saveNote();
                // Disable the callback to avoid a loop, and trigger the default back action
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
    }

    private void saveNote() {
        String title = editTextNoteTitle.getText().toString();
        String content = editTextNoteContent.getText().toString();

        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(content)) {
            setResult(Activity.RESULT_CANCELED);
        } else {
            if (note == null) {
                note = new Note(title, content, 0); 
            }
            note.setTitle(title);
            note.setContent(content);

            Intent resultIntent = new Intent();
            resultIntent.putExtra("note", note);
            resultIntent.putExtra("notePosition", getIntent().getIntExtra("notePosition", -1));
            setResult(Activity.RESULT_OK, resultIntent);
        }
    }
}
