package com.example.myassistant;

import org.junit.Test;

import static org.junit.Assert.*;

public class AiSettingsStoreTest {

    @Test
    public void testGeminiConstants() {
        assertEquals("gemini-3.6-flash", AiSettingsStore.DEFAULT_GEMINI_MODEL);
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", AiSettingsStore.ENDPOINT_GEMINI);
        assertEquals("https://aistudio.google.com/app/apikey", AiSettingsStore.HELP_URL_GEMINI);
        assertEquals("https://aistudio.google.com/app/apikey", AiSettingsStore.getHelpUrl());
    }

    @Test
    public void testGeminiModelArrays() {
        assertNotNull(AiSettingsStore.GEMINI_DISPLAY_NAMES);
        assertNotNull(AiSettingsStore.GEMINI_MODEL_VALUES);
        assertEquals(AiSettingsStore.GEMINI_DISPLAY_NAMES.length, AiSettingsStore.GEMINI_MODEL_VALUES.length);
        assertEquals("gemini-3.6-flash", AiSettingsStore.GEMINI_MODEL_VALUES[0]);
    }

    @Test
    public void testEndpointWithoutContext() {
        assertEquals(AiSettingsStore.ENDPOINT_GEMINI, AiSettingsStore.getActiveEndpoint(null));
        assertEquals(AiSettingsStore.PROVIDER_GEMINI, AiSettingsStore.getProvider(null));
        assertEquals(AiSettingsStore.DEFAULT_GEMINI_MODEL, AiSettingsStore.getModel(null));
    }
}
