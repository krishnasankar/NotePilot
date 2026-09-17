package com.example.myassistant;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ChatHistoryStorageTest {

    @Test
    public void testNullContextHandling() {
        assertFalse(ChatHistoryStorage.saveChatHistory(null, new ArrayList<>()));
        assertFalse(ChatHistoryStorage.saveChatHistory(null, null));
        assertNotNull(ChatHistoryStorage.loadChatHistory(null));
        assertTrue(ChatHistoryStorage.loadChatHistory(null).isEmpty());
        assertFalse(ChatHistoryStorage.clearChatHistory(null));
    }

    @Test
    public void testChatMessageSerializationAndDeserialization() {
        List<ChatMessage> list = new ArrayList<>();
        list.add(new ChatMessage("Hello AI", ChatMessage.Author.USER));
        list.add(new ChatMessage("Hello! How can I help you today?", ChatMessage.Author.MODEL));

        Gson gson = new Gson();
        String json = gson.toJson(list);
        assertNotNull(json);
        assertTrue(json.contains("Hello AI"));
        assertTrue(json.contains("USER"));
        assertTrue(json.contains("MODEL"));

        Type type = new TypeToken<ArrayList<ChatMessage>>() {}.getType();
        List<ChatMessage> restored = gson.fromJson(json, type);
        assertNotNull(restored);
        assertEquals(2, restored.size());
        assertEquals("Hello AI", restored.get(0).getContent());
        assertEquals(ChatMessage.Author.USER, restored.get(0).getAuthor());
        assertEquals("Hello! How can I help you today?", restored.get(1).getContent());
        assertEquals(ChatMessage.Author.MODEL, restored.get(1).getAuthor());
    }
}
