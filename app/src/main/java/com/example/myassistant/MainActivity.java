package com.example.myassistant;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private final FragmentManager fm = getSupportFragmentManager();
    private Fragment notesFragment;
    private Fragment chatFragment;
    private Fragment activeFragment;
    private ImageButton buttonSettings;
    private View keyStatusDot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonSettings = findViewById(R.id.buttonSettings);
        keyStatusDot = findViewById(R.id.keyStatusDot);

        BottomNavigationView navigation = findViewById(R.id.bottom_navigation);
        navigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.navigation_notes) {
                fm.beginTransaction().hide(activeFragment).show(notesFragment).commit();
                activeFragment = notesFragment;
                return true;
            } else if (item.getItemId() == R.id.navigation_chat) {
                fm.beginTransaction().hide(activeFragment).show(chatFragment).commit();
                activeFragment = chatFragment;
                return true;
            }
            return false;
        });

        if (savedInstanceState == null) {
            notesFragment = new NotesFragment();
            chatFragment = new ChatFragment();
            fm.beginTransaction()
                    .add(R.id.fragment_container, chatFragment, "chat").hide(chatFragment)
                    .add(R.id.fragment_container, notesFragment, "notes")
                    .commit();
            activeFragment = notesFragment;
            navigation.setSelectedItemId(R.id.navigation_notes);
        } else {
            notesFragment = fm.findFragmentByTag("notes");
            chatFragment = fm.findFragmentByTag("chat");
            // find the visible fragment
            if (notesFragment != null && notesFragment.isVisible()) {
                activeFragment = notesFragment;
            } else {
                activeFragment = chatFragment;
            }
        }

        buttonSettings.setOnClickListener(v -> showApiKeyDialog());
        updateKeyStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateKeyStatus();
    }

    private void updateKeyStatus() {
        String apiKey = ApiKeyStore.getKey(this);
        boolean hasKey = apiKey != null && !apiKey.trim().isEmpty();
        buttonSettings.setAlpha(hasKey ? 1.0f : 0.6f);
        keyStatusDot.setVisibility(View.VISIBLE);
        int color = hasKey
                ? ContextCompat.getColor(this, R.color.key_present_green)
                : ContextCompat.getColor(this, R.color.key_missing_red);
        keyStatusDot.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    private void showApiKeyDialog() {
        android.view.LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_api_key, null);

        TextView textHelpDescription = view.findViewById(R.id.textHelpDescription);
        TextView btnGetKey = view.findViewById(R.id.btnGetKey);
        android.widget.EditText input = view.findViewById(R.id.editApiKey);
        Spinner spinnerModel = view.findViewById(R.id.spinnerModel);
        android.widget.EditText editCustomModel = view.findViewById(R.id.editCustomModel);
        Switch editPermissionSwitch = view.findViewById(R.id.editPermissionSwitch);
        Switch locationPermissionSwitch = view.findViewById(R.id.locationPermissionSwitch);
        android.widget.Button btnSave = view.findViewById(R.id.btnSave);
        android.widget.Button btnCancel = view.findViewById(R.id.btnCancel);

        textHelpDescription.setText(AiSettingsStore.getHelpDescription());
        btnGetKey.setText("Get Free Gemini Key ↗");
        btnGetKey.setOnClickListener(v -> {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(AiSettingsStore.HELP_URL_GEMINI));
                startActivity(browserIntent);
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Could not open browser: " + AiSettingsStore.HELP_URL_GEMINI, Toast.LENGTH_SHORT).show();
            }
        });

        input.setHint("AIza... or AQ....");
        String existingKey = ApiKeyStore.getKey(this);
        if (existingKey != null && !existingKey.isEmpty()) {
            String masked = existingKey.length() > 8 ? "****" + existingKey.substring(existingKey.length() - 8) : "****";
            input.setText(masked);
        } else {
            input.setText("");
        }

        String currentModel = AiSettingsStore.getModel(this);
        int selectedModelIndex = 0;
        boolean isPreset = false;
        for (int i = 0; i < AiSettingsStore.GEMINI_MODEL_VALUES.length - 1; i++) {
            if (AiSettingsStore.GEMINI_MODEL_VALUES[i].equalsIgnoreCase(currentModel)) {
                selectedModelIndex = i;
                isPreset = true;
                break;
            }
        }
        if (!isPreset && currentModel != null && !currentModel.trim().isEmpty()) {
            selectedModelIndex = AiSettingsStore.GEMINI_DISPLAY_NAMES.length - 1;
            editCustomModel.setText(currentModel);
            editCustomModel.setVisibility(View.VISIBLE);
        } else {
            editCustomModel.setVisibility(View.GONE);
        }

        ArrayAdapter<String> modelAdapter = new ArrayAdapter<>(this, R.layout.item_model_spinner, AiSettingsStore.GEMINI_DISPLAY_NAMES);
        modelAdapter.setDropDownViewResource(R.layout.item_model_spinner_dropdown);
        spinnerModel.setAdapter(modelAdapter);
        spinnerModel.setSelection(selectedModelIndex);

        spinnerModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                if (position == AiSettingsStore.GEMINI_DISPLAY_NAMES.length - 1) {
                    editCustomModel.setVisibility(View.VISIBLE);
                    editCustomModel.requestFocus();
                } else {
                    editCustomModel.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        editPermissionSwitch.setChecked(PermissionStore.getEditPermission(this));
        locationPermissionSwitch.setChecked(PermissionStore.getLocationPermission(this));

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setView(view)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnSave.setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            boolean keyChanged = false;
            if (!value.isEmpty() && !value.startsWith("****")) {
                keyChanged = ApiKeyStore.saveKey(MainActivity.this, value);
            }

            int selModel = spinnerModel.getSelectedItemPosition();
            String chosenModel;
            if (selModel == AiSettingsStore.GEMINI_DISPLAY_NAMES.length - 1) {
                String custom = editCustomModel.getText().toString().trim();
                chosenModel = custom.isEmpty() ? AiSettingsStore.DEFAULT_GEMINI_MODEL : custom;
            } else if (selModel >= 0 && selModel < AiSettingsStore.GEMINI_MODEL_VALUES.length - 1) {
                chosenModel = AiSettingsStore.GEMINI_MODEL_VALUES[selModel];
            } else {
                chosenModel = AiSettingsStore.DEFAULT_GEMINI_MODEL;
            }

            boolean modelChanged = !chosenModel.equals(AiSettingsStore.getModel(MainActivity.this));
            if (modelChanged) {
                AiSettingsStore.setModel(MainActivity.this, chosenModel);
            }

            boolean permissionChanged = editPermissionSwitch.isChecked() != PermissionStore.getEditPermission(MainActivity.this);
            if (permissionChanged) {
                PermissionStore.setEditPermission(MainActivity.this, editPermissionSwitch.isChecked());
            }

            boolean locationChanged = locationPermissionSwitch.isChecked() != PermissionStore.getLocationPermission(MainActivity.this);
            if (locationChanged) {
                PermissionStore.setLocationPermission(MainActivity.this, locationPermissionSwitch.isChecked());
            }

            if (keyChanged || modelChanged || permissionChanged || locationChanged) {
                Toast.makeText(MainActivity.this, "Settings saved", Toast.LENGTH_SHORT).show();
                updateKeyStatus();
            } else {
                Toast.makeText(MainActivity.this, "No changes saved", Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    public void showAiChangeConfirmationDialog(String actionBadgeText, String title, String content, Runnable onAllowed, Runnable onCancelled) {
        android.view.LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_ai_change_confirmation, null);

        TextView badgeAction = view.findViewById(R.id.badgeAction);
        TextView targetNoteTitle = view.findViewById(R.id.targetNoteTitle);
        TextView contentPreview = view.findViewById(R.id.contentPreview);
        android.widget.Button btnAllow = view.findViewById(R.id.buttonAllow);
        android.widget.Button btnCancel = view.findViewById(R.id.buttonCancel);

        if (badgeAction != null) {
            badgeAction.setText(actionBadgeText != null ? actionBadgeText : "UPDATE NOTE");
            if ("DELETE NOTE".equalsIgnoreCase(actionBadgeText)) {
                badgeAction.setBackgroundColor(Color.parseColor("#E53935"));
            } else if ("PIN NOTE".equalsIgnoreCase(actionBadgeText) || "UNPIN NOTE".equalsIgnoreCase(actionBadgeText)) {
                badgeAction.setBackgroundColor(Color.parseColor("#FF9800"));
            }
        }
        if (targetNoteTitle != null) {
            targetNoteTitle.setText(title != null && !title.trim().isEmpty() ? title : "Untitled Note");
        }
        if (contentPreview != null) {
            contentPreview.setText(content != null ? content : "");
        }

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnAllow.setOnClickListener(v -> {
            dialog.dismiss();
            if (onAllowed != null) onAllowed.run();
        });

        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
            if (onCancelled != null) onCancelled.run();
        });

        dialog.setOnCancelListener(d -> {
            if (onCancelled != null) onCancelled.run();
        });

        dialog.show();
    }

    public void showAiChangeConfirmationDialog(String title, String content, boolean isNewNote, Runnable onAllowed, Runnable onCancelled) {
        showAiChangeConfirmationDialog(isNewNote ? "CREATE NOTE" : "UPDATE NOTE", title, content, onAllowed, onCancelled);
    }

    public void showAiChangeConfirmationDialog(String title, String content, Runnable onAllowed) {
        showAiChangeConfirmationDialog(title, content, false, onAllowed, null);
    }
}
