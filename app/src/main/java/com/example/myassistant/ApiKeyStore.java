package com.example.myassistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class ApiKeyStore {

    private static final String TAG = "ApiKeyStore";
    private static final String PREFS_NAME = "api_key_prefs";
    private static final String KEY_GEMINI = "gemini_api_key";
    private static final String KEY_LEGACY_API = "openrouter_api_key";

    public static boolean saveKey(Context context, String apiKey) {
        return savePrefValue(context, KEY_GEMINI, apiKey != null ? apiKey.trim() : "");
    }

    public static String getKey(Context context) {
        String val = getPrefValue(context, KEY_GEMINI);
        if (val != null && !val.trim().isEmpty()) {
            return val;
        }
        // Fallback to legacy key slot in case an existing key was stored previously
        String legacy = getPrefValue(context, KEY_LEGACY_API);
        if (legacy != null && !legacy.trim().isEmpty()) {
            return legacy;
        }
        return null;
    }

    public static boolean hasKey(Context context) {
        String key = getKey(context);
        return key != null && !key.trim().isEmpty();
    }

    public static boolean clearKey(Context context) {
        boolean r1 = removePrefValue(context, KEY_GEMINI);
        boolean r2 = removePrefValue(context, KEY_LEGACY_API);
        return r1 || r2;
    }

    // Compatibility overloads for existing code
    public static boolean saveKey(Context context, String provider, String apiKey) {
        return saveKey(context, apiKey);
    }

    public static String getKey(Context context, String provider) {
        return getKey(context);
    }

    public static boolean clearKey(Context context, String provider) {
        return clearKey(context);
    }

    private static boolean savePrefValue(Context context, String key, String value) {
        try {
            SharedPreferences prefs = getSecurePrefs(context);
            prefs.edit().putString(key, value).apply();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "EncryptedSharedPreferences failed, fallback to normal prefs: " + e.getMessage());
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit().putString(key, value).apply();
                return true;
            } catch (Exception ex) {
                Log.e(TAG, "Failed to save API key: " + ex.getMessage());
                return false;
            }
        }
    }

    private static String getPrefValue(Context context, String key) {
        try {
            SharedPreferences prefs = getSecurePrefs(context);
            return prefs.getString(key, null);
        } catch (Exception e) {
            Log.w(TAG, "EncryptedSharedPreferences failed, fallback to normal prefs: " + e.getMessage());
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getString(key, null);
        }
    }

    private static boolean removePrefValue(Context context, String key) {
        try {
            SharedPreferences prefs = getSecurePrefs(context);
            prefs.edit().remove(key).apply();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "EncryptedSharedPreferences failed, fallback to normal prefs: " + e.getMessage());
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit().remove(key).apply();
                return true;
            } catch (Exception ex) {
                Log.e(TAG, "Failed to clear API key: " + ex.getMessage());
                return false;
            }
        }
    }

    private static SharedPreferences getSecurePrefs(Context context) throws GeneralSecurityException, IOException {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();

        return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }
}
