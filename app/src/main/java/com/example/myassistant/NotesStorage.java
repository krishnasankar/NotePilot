package com.example.myassistant;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
        try (FileOutputStream fos = context.openFileOutput(NOTES_FILE_NAME, Context.MODE_PRIVATE)) {
            fos.write(json.getBytes());
            fos.flush();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static List<Note> loadNotes(Context context) {
        try (FileInputStream fis = context.openFileInput(NOTES_FILE_NAME);
             InputStreamReader isr = new InputStreamReader(fis);
             BufferedReader reader = new BufferedReader(isr)) {

            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            Gson gson = new Gson();
            Type type = new TypeToken<ArrayList<Note>>() {}.getType();
            List<Note> notes = gson.fromJson(builder.toString(), type);
            if (notes != null) {
                return notes;
            }
        } catch (FileNotFoundException e) {
            // This is normal on the first run when the file doesn't exist yet.
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Return an empty list if the file doesn't exist, is empty, or an error occurred.
        return new ArrayList<>();
    }
}
