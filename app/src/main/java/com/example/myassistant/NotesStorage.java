package com.example.myassistant;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class NotesStorage {

    private static final String NOTES_FILE_NAME = "user_notes.json";

    public static boolean saveNotes(Context context, List<Note> notes) {
        Gson gson = new Gson();
        String json = gson.toJson(notes);
        FileOutputStream fos = null;
        try {
            fos = context.openFileOutput(NOTES_FILE_NAME, Context.MODE_PRIVATE);
            fos.write(json.getBytes());
            fos.flush();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (IOException ignored) {}
            }
        }
    }

    public static List<Note> loadNotes(Context context) {
        List<Note> notes = new ArrayList<>();
        FileInputStream fis = null;
        BufferedReader reader = null;

        try {
            fis = context.openFileInput(NOTES_FILE_NAME);
            reader = new BufferedReader(new InputStreamReader(fis));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            Gson gson = new Gson();
            Type type = new TypeToken<ArrayList<Note>>() {}.getType();
            notes = gson.fromJson(builder.toString(), type);
        } catch (IOException e) {
            // no file yet - first run
        } finally {
            try {
                if (reader != null) reader.close();
                if (fis != null) fis.close();
            } catch (IOException ignored) {}
        }

        return notes;
    }
}
