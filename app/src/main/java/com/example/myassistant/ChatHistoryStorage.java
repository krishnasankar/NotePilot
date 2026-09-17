package com.example.myassistant;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ChatHistoryStorage {

    private static final String CHAT_FILE_NAME = "chat_history.json";

    public static boolean saveChatHistory(Context context, List<ChatMessage> messages) {
        if (context == null || messages == null) return false;
        Gson gson = new Gson();
        String json = gson.toJson(messages);
        try (FileOutputStream fos = context.openFileOutput(CHAT_FILE_NAME, Context.MODE_PRIVATE)) {
            fos.write(json.getBytes());
            fos.flush();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static List<ChatMessage> loadChatHistory(Context context) {
        if (context == null) return new ArrayList<>();
        try (FileInputStream fis = context.openFileInput(CHAT_FILE_NAME);
             InputStreamReader isr = new InputStreamReader(fis);
             BufferedReader reader = new BufferedReader(isr)) {

            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            Gson gson = new Gson();
            Type type = new TypeToken<ArrayList<ChatMessage>>() {}.getType();
            List<ChatMessage> list = gson.fromJson(builder.toString(), type);
            if (list != null) {
                return list;
            }
        } catch (FileNotFoundException ignored) {
            // Normal when no chat history exists yet
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public static boolean clearChatHistory(Context context) {
        if (context == null) return false;
        try {
            File file = context.getFileStreamPath(CHAT_FILE_NAME);
            if (file != null && file.exists()) {
                return file.delete();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
