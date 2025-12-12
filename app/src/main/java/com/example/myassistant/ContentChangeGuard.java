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

        StringBuilder msg = new StringBuilder();
        String src = (sourceTag == null || sourceTag.trim().isEmpty()) ? "This action" : sourceTag;
        msg.append(src).append(" is about to modify this note.\n\n");

        boolean titleChanged = !safe(oldTitle).equals(safe(newTitle));
        boolean bodyChanged = !safe(oldContent).equals(safe(newContent));

        if (titleChanged) {
            msg.append("Title:\n")
               .append("• Before: ").append(clip(oldTitle)).append("\n")
               .append("• After:  ").append(clip(newTitle)).append("\n\n");
        }
        if (bodyChanged) {
            msg.append("Body:\n")
               .append("• Before: ").append(clipMulti(oldContent)).append("\n")
               .append("• After:  ").append(clipMulti(newContent)).append("\n\n");
        }

        new androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle("Confirm content change")
                .setMessage(msg.toString())
                .setPositiveButton("Save changes", (d, w) -> {
                    if (callback != null) callback.onDecision(true);
                })
                .setNegativeButton("Don't save", (d, w) -> {
                    if (callback != null) callback.onDecision(false);
                })
                .setCancelable(true)
                .show();
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
