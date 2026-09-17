package com.example.myassistant;

import android.content.Context;
import android.content.SharedPreferences;

public class PermissionStore {

    private static final String PREFS_NAME = "ai_permissions";
    private static final String EDIT_PERMISSION_KEY = "allow_ai_edit";
    private static final String LOCATION_PERMISSION_KEY = "share_location_with_ai";

    public static void setEditPermission(Context context, boolean allowed) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(EDIT_PERMISSION_KEY, allowed).apply();
    }

    public static boolean getEditPermission(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(EDIT_PERMISSION_KEY, false);
    }

    public static void setLocationPermission(Context context, boolean allowed) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(LOCATION_PERMISSION_KEY, allowed).apply();
    }

    public static boolean getLocationPermission(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(LOCATION_PERMISSION_KEY, false);
    }
}
