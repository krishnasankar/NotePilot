package com.example.myassistant;

import org.junit.Test;

import static org.junit.Assert.*;

public class ChatPolishTest {

    @Test
    public void testStripMarkdownForSpeech() {
        String input = "# Header 1\n" +
                "This is **bold** text and *italic* text.\n" +
                "- [x] Completed item\n" +
                "- [ ] Pending item\n" +
                "* Bullet point\n" +
                "1. Numbered item\n" +
                "Here is `code` and a [link](https://example.com).\n" +
                "```python\nprint('hello')\n```";

        String stripped = ChatFragment.stripMarkdownForSpeech(input);

        assertFalse(stripped.contains("#"));
        assertFalse(stripped.contains("**"));
        assertFalse(stripped.contains("`code`"));
        assertFalse(stripped.contains("[x]"));
        assertFalse(stripped.contains("[ ]"));
        assertFalse(stripped.contains("print('hello')")); // Code block removed
        assertTrue(stripped.contains("bold"));
        assertTrue(stripped.contains("italic"));
        assertTrue(stripped.contains("Completed item"));
        assertTrue(stripped.contains("Pending item"));
        assertTrue(stripped.contains("Bullet point"));
        assertTrue(stripped.contains("Numbered item"));
        assertTrue(stripped.contains("link"));
    }

    @Test
    public void testFriendlyErrorMessageFormatting() {
        // 401 Invalid Key
        String err401 = ChatFragment.formatFriendlyErrorMessage("{\"error\":{\"message\":\"API key not valid\"}}", 401);
        assertTrue(err401.contains("Invalid API Key (HTTP 401)"));
        assertTrue(err401.contains("API key not valid"));
        assertTrue(err401.contains("aistudio.google.com"));

        // 404 Model Not Found
        String err404 = ChatFragment.formatFriendlyErrorMessage("{\"error\":{\"message\":\"Model deprecated\"}}", 404);
        assertTrue(err404.contains("Model Not Found (HTTP 404)"));
        assertTrue(err404.contains("Gemini 3.6 Flash"));

        // 429 Quota Exceeded
        String err429 = ChatFragment.formatFriendlyErrorMessage("{\"error\":{\"message\":\"Rate limit exceeded\"}}", 429);
        assertTrue(err429.contains("Rate Limit Exceeded (HTTP 429)"));

        // 500 Outage
        String err500 = ChatFragment.formatFriendlyErrorMessage("Internal Server Error", 500);
        assertTrue(err500.contains("Temporary Outage (HTTP 500)"));
    }

    @Test
    public void testStripMarkdownNullAndEmpty() {
        assertEquals("", ChatFragment.stripMarkdownForSpeech(null));
        assertEquals("", ChatFragment.stripMarkdownForSpeech(""));
        assertEquals("", ChatFragment.stripMarkdownForSpeech("   "));
    }
}
