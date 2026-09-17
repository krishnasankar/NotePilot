package com.example.myassistant;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.List;

public class AiSettingsStore {

    private static final String PREFS_NAME = "ai_settings_prefs";
    private static final String KEY_MODEL = "selected_gemini_model";

    public static final String PROVIDER_GEMINI = "google_gemini";

    public static final String ENDPOINT_GEMINI = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
    public static final String DEFAULT_GEMINI_MODEL = "gemini-3.6-flash";
    public static final String HELP_URL_GEMINI = "https://aistudio.google.com/app/apikey";

    public static final String[] GEMINI_DISPLAY_NAMES = new String[]{
            "Gemini 3.6 Flash (Fastest, Recommended)",
            "Gemini 3.5 Flash (Balanced)",
            "Gemini 3.5 Flash Lite (Ultra Lightweight)",
            "Custom Model..."
    };

    public static final String[] GEMINI_MODEL_VALUES = new String[]{
            "gemini-3.6-flash",
            "gemini-3.5-flash",
            "gemini-3.5-flash-lite",
            ""
    };

    public static String getActiveEndpoint(Context context) {
        return ENDPOINT_GEMINI;
    }

    public static String getModel(Context context) {
        if (context == null) return DEFAULT_GEMINI_MODEL;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_MODEL, DEFAULT_GEMINI_MODEL);
        if ("gemini-2.0-flash".equalsIgnoreCase(saved)
                || "gemini-1.5-flash".equalsIgnoreCase(saved)
                || "gemini-1.5-pro".equalsIgnoreCase(saved)
                || "google/gemini-2.0-flash-exp:free".equalsIgnoreCase(saved)
                || "gemini-2.5-flash".equalsIgnoreCase(saved)
                || "gemini-2.5-pro".equalsIgnoreCase(saved)
                || "gemini-2.5-flash-lite".equalsIgnoreCase(saved)) {
            saved = DEFAULT_GEMINI_MODEL;
            prefs.edit().putString(KEY_MODEL, DEFAULT_GEMINI_MODEL).apply();
        }
        return saved;
    }

    public static void setModel(Context context, String model) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_MODEL, model != null ? model.trim() : DEFAULT_GEMINI_MODEL).apply();
    }

    // Compatibility methods for existing callers
    public static String getActiveModel(Context context) {
        return getModel(context);
    }

    public static String getModel(Context context, String provider) {
        return getModel(context);
    }

    public static void setModel(Context context, String provider, String model) {
        setModel(context, model);
    }

    public static String getProvider(Context context) {
        return PROVIDER_GEMINI;
    }

    public static void setProvider(Context context, String provider) {
        // No-op: Gemini is the dedicated provider
    }

    public static String getProviderHelpUrl(String provider) {
        return HELP_URL_GEMINI;
    }

    public static String getHelpUrl() {
        return HELP_URL_GEMINI;
    }

    public static String getProviderHelpDescription(String provider) {
        return getHelpDescription();
    }

    public static String getHelpDescription() {
        return "Get a 100% free Gemini API key from Google AI Studio. No credit card required. Free tier includes up to 15 requests/min (1,500/day).";
    }
}
