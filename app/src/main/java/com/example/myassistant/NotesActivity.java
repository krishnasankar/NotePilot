package com.example.myassistant;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.EditText;

public class NotesActivity extends AppCompatActivity {

    private EditText editTextNotes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);

        editTextNotes = findViewById(R.id.editTextNotes);

        // Load existing notes
        String notes = NotesStorage.loadNotes(this);
        editTextNotes.setText(notes);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Auto-save when leaving the screen (back, home, etc.)
        String text = editTextNotes.getText().toString();
        NotesStorage.saveNotes(this, text);
    }
}