package com.example.myassistant;

import android.content.Context;
import android.content.SharedPreferences;

public class PermissionStore {

    private static final String PREFS_NAME = "ai_permissions";
    private static final String EDIT_PERMISSION_KEY = "allow_ai_edit";

    public static void setEditPermission(Context context, boolean allowed) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(EDIT_PERMISSION_KEY, allowed).apply();
    }

    public static boolean getEditPermission(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(EDIT_PERMISSION_KEY, false);
    }
}
