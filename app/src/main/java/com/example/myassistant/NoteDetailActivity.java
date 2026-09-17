package com.example.myassistant;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class NoteDetailActivity extends AppCompatActivity {

    private EditText editTextNoteTitle;
    private EditText editTextNoteContent;
    private RecyclerView recyclerViewChecklist;
    private ChecklistAdapter checklistAdapter;
    private MaterialButton buttonToggleMode;
    private TextView textViewMetaStats;
    private View viewColorAccentBar;

    private Note note;
    private Note originalNote;
    private boolean isNewNote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_detail);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> {
            saveNote();
            finish();
        });

        editTextNoteTitle = findViewById(R.id.editTextNoteTitle);
        editTextNoteContent = findViewById(R.id.editTextNoteContent);
        recyclerViewChecklist = findViewById(R.id.recyclerViewChecklist);
        buttonToggleMode = findViewById(R.id.buttonToggleMode);
        textViewMetaStats = findViewById(R.id.textViewMetaStats);
        viewColorAccentBar = findViewById(R.id.viewColorAccentBar);

        // Window insets handling for IME keyboard and navigation bars
        View root = findViewById(R.id.rootNoteDetail);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            int top = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottomIme = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int bottomNav = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            int bottom = Math.max(bottomIme, bottomNav);
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bottom);
            return windowInsets;
        });

        note = (Note) getIntent().getSerializableExtra("note");
        int notePosition = getIntent().getIntExtra("notePosition", -1);
        isNewNote = (notePosition == -1);

        if (note == null) {
            note = new Note("", "", 0);
        }
        originalNote = new Note(note);

        updateAccentBar(note.getColor());

        editTextNoteTitle.setText(note.getTitle());

        if (note.isChecklist()) {
            setupChecklistMode();
        } else {
            setupTextMode();
        }

        buttonToggleMode.setOnClickListener(v -> toggleMode());

        if (isNewNote) {
            editTextNoteTitle.requestFocus();
            new Handler().postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(editTextNoteTitle, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 150);

            editTextNoteTitle.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT ||
                        (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                    if (note.isChecklist()) {
                        if (note.getChecklist().isEmpty()) {
                            checklistAdapter.addItemAndFocus(new ChecklistItem("", false), recyclerViewChecklist);
                        }
                    } else {
                        editTextNoteContent.requestFocus();
                        editTextNoteContent.setSelection(editTextNoteContent.getText().length());
                    }
                    return true;
                }
                return false;
            });
        } else {
            if (!note.isChecklist() && !TextUtils.isEmpty(note.getContent())) {
                editTextNoteContent.setSelection(note.getContent().length());
            }
        }

        updateStats();

        // Handle back button
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                saveNote();
                finish();
            }
        });
    }

    private void updateAccentBar(int color) {
        if (viewColorAccentBar != null) {
            if (color == 0 || color == Color.TRANSPARENT) {
                viewColorAccentBar.setVisibility(View.GONE);
                viewColorAccentBar.setBackgroundColor(Color.TRANSPARENT);
            } else {
                viewColorAccentBar.setVisibility(View.VISIBLE);
                viewColorAccentBar.setBackgroundColor(color);
            }
        }
    }

    private void setupTextMode() {
        editTextNoteContent.setText(note.getContent());
        editTextNoteContent.setVisibility(View.VISIBLE);
        recyclerViewChecklist.setVisibility(View.GONE);
        buttonToggleMode.setText(R.string.mode_checklist);
        buttonToggleMode.setIconResource(R.drawable.ic_format_list_bulleted_24);
        updateStats();
    }

    private void setupChecklistMode() {
        if (note.getChecklist() == null) {
            note.setChecklist(new ArrayList<>());
        }
        checklistAdapter = new ChecklistAdapter(note.getChecklist(), () -> {
            checklistAdapter.addItemAndFocus(new ChecklistItem("", false), recyclerViewChecklist);
        });
        checklistAdapter.setOnChecklistChangeListener(this::updateStats);

        recyclerViewChecklist.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewChecklist.setAdapter(checklistAdapter);
        recyclerViewChecklist.setVisibility(View.VISIBLE);
        editTextNoteContent.setVisibility(View.GONE);
        buttonToggleMode.setText(R.string.mode_text);
        buttonToggleMode.setIconResource(R.drawable.ic_text_fields_24);
        updateStats();
    }

    private void toggleMode() {
        if (note.isChecklist()) {
            // Switch to text mode
            StringBuilder sb = new StringBuilder();
            if (note.getChecklist() != null) {
                for (ChecklistItem item : note.getChecklist()) {
                    if (item.text != null && !item.text.trim().isEmpty()) {
                        sb.append(item.text).append("\n");
                    }
                }
            }
            note.setContent(sb.toString().trim());
            note.setChecklist(new ArrayList<>());
            note.setChecklist(false);
            setupTextMode();
        } else {
            // Switch to checklist mode
            String content = editTextNoteContent.getText().toString();
            List<ChecklistItem> items = new ArrayList<>();
            if (!TextUtils.isEmpty(content)) {
                String[] lines = content.split("\n");
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        items.add(new ChecklistItem(line.trim(), false));
                    }
                }
            }
            if (items.isEmpty()) {
                items.add(new ChecklistItem("", false));
            }
            note.setChecklist(items);
            note.setContent("");
            note.setChecklist(true);
            setupChecklistMode();

            // Auto-focus first item if empty
            if (TextUtils.isEmpty(content)) {
                recyclerViewChecklist.post(() -> {
                    RecyclerView.ViewHolder holder = recyclerViewChecklist.findViewHolderForAdapterPosition(0);
                    if (holder instanceof ChecklistAdapter.ChecklistViewHolder) {
                        ChecklistAdapter.ChecklistViewHolder cvh = (ChecklistAdapter.ChecklistViewHolder) holder;
                        cvh.editTextItem.requestFocus();
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.showSoftInput(cvh.editTextItem, InputMethodManager.SHOW_IMPLICIT);
                        }
                    }
                });
            }
        }
    }

    private void updateStats() {
        if (textViewMetaStats == null) return;
        if (note.isChecklist()) {
            textViewMetaStats.setVisibility(View.VISIBLE);
            List<ChecklistItem> items = note.getChecklist();
            int total = (items != null) ? items.size() : 0;
            int done = 0;
            if (items != null) {
                for (ChecklistItem item : items) {
                    if (item.checked) done++;
                }
            }
            textViewMetaStats.setText(getString(R.string.stat_items, done, total));
        } else {
            textViewMetaStats.setVisibility(View.GONE);
            textViewMetaStats.setText("");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_note_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_ask_ai) {
            askAiAboutThisNote();
            return true;
        } else if (id == R.id.action_color) {
            showColorPickerDialog();
            return true;
        } else if (id == R.id.action_delete) {
            showDeleteConfirmationDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void askAiAboutThisNote() {
        saveNote();
        String title = editTextNoteTitle.getText().toString().trim();
        String summary = getNoteTextForAi();

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("ACTION", "ASK_AI_ABOUT_NOTE");
        intent.putExtra("NOTE_TITLE", title.isEmpty() ? "Untitled Note" : title);
        intent.putExtra("NOTE_CONTENT", summary);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private String getNoteTextForAi() {
        if (note.isChecklist() && note.getChecklist() != null) {
            StringBuilder sb = new StringBuilder();
            for (ChecklistItem ci : note.getChecklist()) {
                sb.append(ci.checked ? "[x] " : "[ ] ").append(ci.text).append("\n");
            }
            return sb.toString().trim();
        } else {
            return editTextNoteContent.getText().toString().trim();
        }
    }

    private void showColorPickerDialog() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_color_picker);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        View def = dialog.findViewById(R.id.colorDefault);
        View coral = dialog.findViewById(R.id.colorCoral);
        View amber = dialog.findViewById(R.id.colorAmber);
        View mint = dialog.findViewById(R.id.colorMint);
        View cyan = dialog.findViewById(R.id.colorCyan);
        View lavender = dialog.findViewById(R.id.colorLavender);

        View.OnClickListener colorClickListener = v -> {
            int selectedColor;
            if (v.getId() == R.id.colorCoral) {
                selectedColor = ContextCompat.getColor(this, R.color.noteAccentRed);
            } else if (v.getId() == R.id.colorAmber) {
                selectedColor = ContextCompat.getColor(this, R.color.noteAccentAmber);
            } else if (v.getId() == R.id.colorMint) {
                selectedColor = ContextCompat.getColor(this, R.color.noteAccentGreen);
            } else if (v.getId() == R.id.colorCyan) {
                selectedColor = ContextCompat.getColor(this, R.color.noteAccentCyan);
            } else if (v.getId() == R.id.colorLavender) {
                selectedColor = ContextCompat.getColor(this, R.color.noteAccentPurple);
            } else {
                selectedColor = 0; // Default / transparent
            }

            note.setColor(selectedColor);
            updateAccentBar(selectedColor);
            dialog.dismiss();
        };

        if (def != null) def.setOnClickListener(colorClickListener);
        if (coral != null) coral.setOnClickListener(colorClickListener);
        if (amber != null) amber.setOnClickListener(colorClickListener);
        if (mint != null) mint.setOnClickListener(colorClickListener);
        if (cyan != null) cyan.setOnClickListener(colorClickListener);
        if (lavender != null) lavender.setOnClickListener(colorClickListener);

        dialog.show();
        if (dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88f);
            dialog.getWindow().setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showDeleteConfirmationDialog() {
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_delete_note, null);

        TextView textNoteTitlePreview = view.findViewById(R.id.textNoteTitlePreview);
        TextView textNoteSnippetPreview = view.findViewById(R.id.textNoteSnippetPreview);
        android.widget.Button buttonCancel = view.findViewById(R.id.buttonCancel);
        android.widget.Button buttonDelete = view.findViewById(R.id.buttonDelete);

        String title = editTextNoteTitle != null ? editTextNoteTitle.getText().toString().trim() : "";
        if (title.isEmpty() && note != null && note.getTitle() != null) {
            title = note.getTitle();
        }
        if (textNoteTitlePreview != null) {
            textNoteTitlePreview.setText(!title.isEmpty() ? title : "Untitled Note");
        }

        String snippet = getNoteTextForAi();
        if (textNoteSnippetPreview != null) {
            if (snippet != null && !snippet.trim().isEmpty()) {
                textNoteSnippetPreview.setText(snippet.trim());
                textNoteSnippetPreview.setVisibility(android.view.View.VISIBLE);
            } else {
                textNoteSnippetPreview.setVisibility(android.view.View.GONE);
            }
        }

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setView(view)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        buttonCancel.setOnClickListener(v -> dialog.dismiss());

        buttonDelete.setOnClickListener(v -> {
            dialog.dismiss();
            deleteNote();
            finish();
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88f);
            dialog.getWindow().setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
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

    private void saveNote() {
        String title = editTextNoteTitle.getText().toString();

        if (note.isChecklist()) {
            note.setContent("");
        } else {
            String content = editTextNoteContent.getText().toString();
            note.setContent(content);
            note.setChecklist(new ArrayList<>());
        }
        note.setTitle(title);

        if (isNewNote && TextUtils.isEmpty(title) &&
                (note.isChecklist() ? note.getChecklist().isEmpty() : TextUtils.isEmpty(note.getContent()))) {
            setResult(Activity.RESULT_CANCELED);
            return;
        }

        boolean hasChanged = isNewNote
                || !java.util.Objects.equals(note.getTitle(), originalNote.getTitle())
                || !java.util.Objects.equals(note.getContent(), originalNote.getContent())
                || note.isChecklist() != originalNote.isChecklist()
                || !java.util.Objects.equals(note.getChecklist(), originalNote.getChecklist())
                || note.getColor() != originalNote.getColor();

        if (!hasChanged) {
            setResult(Activity.RESULT_CANCELED);
            return;
        }

        note.setLastModified(System.currentTimeMillis());

        List<Note> notes = NotesStorage.loadNotes(this);
        if (notes == null) {
            notes = new ArrayList<>();
        }

        if (isNewNote) {
            notes.add(0, note);
        } else {
            boolean found = false;
            for (int i = 0; i < notes.size(); i++) {
                if (notes.get(i).getId() == note.getId()) {
                    notes.set(i, note);
                    found = true;
                    break;
                }
            }
            if (!found) {
                notes.add(0, note);
            }
        }
        NotesStorage.saveNotes(this, notes);
        setResult(Activity.RESULT_OK);
    }
}
