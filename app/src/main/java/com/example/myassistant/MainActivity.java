package com.example.myassistant;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Switch;
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

        android.widget.EditText input = view.findViewById(R.id.editApiKey);
        Switch editPermissionSwitch = view.findViewById(R.id.editPermissionSwitch);
        android.widget.Button btnSave = view.findViewById(R.id.btnSave);
        android.widget.Button btnCancel = view.findViewById(R.id.btnCancel);

        String existing = ApiKeyStore.getKey(this);
        if (existing != null && !existing.isEmpty()) {
            String masked = existing.length() > 8 ? "****" + existing.substring(existing.length() - 8) : "****";
            input.setText(masked);
        }

        editPermissionSwitch.setChecked(PermissionStore.getEditPermission(this));

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

            boolean permissionChanged = editPermissionSwitch.isChecked() != PermissionStore.getEditPermission(MainActivity.this);
            if (permissionChanged) {
                PermissionStore.setEditPermission(MainActivity.this, editPermissionSwitch.isChecked());
            }

            if (keyChanged || permissionChanged) {
                Toast.makeText(MainActivity.this, "Settings saved", Toast.LENGTH_SHORT).show();
                if (keyChanged) updateKeyStatus();
            } else {
                Toast.makeText(MainActivity.this, "No changes saved", Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    public void showAiChangeConfirmationDialog(String title, String content, Runnable onAllowed) {
        android.view.LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_ai_change_confirmation, null);

        android.widget.Button btnAllow = view.findViewById(R.id.buttonAllow);
        android.widget.Button btnCancel = view.findViewById(R.id.buttonCancel);

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setView(view)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnAllow.setOnClickListener(v -> {
            onAllowed.run();
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
