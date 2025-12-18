package com.example.myassistant;

import android.app.Activity;

public final class ContentChangeGuard {

    public interface ConsentCallback {
        void onDecision(boolean approved);
    }

    /**
     * Shows a confirmation dialog before committing content changes.
     * If the user approves, callback receives true; otherwise false.
     */
    public static void confirmContentChange(Activity activity,
                                            String oldTitle,
                                            String oldContent,
                                            String newTitle,
                                            String newContent,
                                            String sourceTag,
                                            ConsentCallback callback) {
        if (activity == null || activity.isFinishing()) {
            if (callback != null) callback.onDecision(false);
            return;
        }

        android.view.View dialogView = android.view.LayoutInflater.from(activity)
                .inflate(R.layout.dialog_ai_change_confirmation, null, false);

        // Set dynamic subtitle with sourceTag (default to "AI Assistant")
        String src = (sourceTag == null || sourceTag.trim().isEmpty()) ? "AI Assistant" : sourceTag.trim();
        android.widget.TextView subtitle = dialogView.findViewById(R.id.subtitleText);
        if (subtitle != null) {
            subtitle.setText("This action is initiated by: " + src);
        }

        final androidx.appcompat.app.AlertDialog alert = new androidx.appcompat.app.AlertDialog.Builder(activity)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        android.view.View btnCancel = dialogView.findViewById(R.id.buttonCancel);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> {
                if (callback != null) callback.onDecision(false);
                alert.dismiss();
            });
        }

        android.view.View btnAllow = dialogView.findViewById(R.id.buttonAllow);
        if (btnAllow != null) {
            btnAllow.setOnClickListener(v -> {
                if (callback != null) callback.onDecision(true);
                alert.dismiss();
            });
        }

        alert.show();
    }

    private static String clip(String s) {
        return ellipsize(s, 60);
    }

    private static String clipMulti(String s) {
        String v = safe(s).trim();
        if (v.length() <= 200) return v;
        return v.substring(0, 200) + "…";
    }

    private static String ellipsize(String s, int max) {
        String v = safe(s);
        if (v.length() <= max) return v;
        return v.substring(0, max) + "…";
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private ContentChangeGuard() {}
}
