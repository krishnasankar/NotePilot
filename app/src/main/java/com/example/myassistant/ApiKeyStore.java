package com.example.myassistant; // change to your package

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class ApiKeyStore {

    private static final String TAG = "ApiKeyStore";
    private static final String PREFS_NAME = "api_key_prefs";
    private static final String KEY_API = "openrouter_api_key";

    // Save key securely if possible, otherwise fallback to normal SharedPreferences
    public static boolean saveKey(Context context, String apiKey) {
        try {
            SharedPreferences prefs = getSecurePrefs(context);
            prefs.edit().putString(KEY_API, apiKey).apply();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "EncryptedSharedPreferences failed, fallback to normal prefs: " + e.getMessage());
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit().putString(KEY_API, apiKey).apply();
                return true;
            } catch (Exception ex) {
                Log.e(TAG, "Failed to save API key: " + ex.getMessage());
                return false;
            }
        }
    }

    public static String getKey(Context context) {
        try {
            SharedPreferences prefs = getSecurePrefs(context);
            return prefs.getString(KEY_API, null);
        } catch (Exception e) {
            Log.w(TAG, "EncryptedSharedPreferences failed, fallback to normal prefs: " + e.getMessage());
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getString(KEY_API, null);
        }
    }

    public static boolean clearKey(Context context) {
        try {
            SharedPreferences prefs = getSecurePrefs(context);
            prefs.edit().remove(KEY_API).apply();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "EncryptedSharedPreferences failed, fallback to normal prefs: " + e.getMessage());
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit().remove(KEY_API).apply();
                return true;
            } catch (Exception ex) {
                Log.e(TAG, "Failed to clear API key: " + ex.getMessage());
                return false;
            }
        }
    }

    private static SharedPreferences getSecurePrefs(Context context) throws GeneralSecurityException, IOException {
        // Use MasterKey to create or get the master key
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
