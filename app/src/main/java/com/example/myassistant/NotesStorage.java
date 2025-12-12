package com.example.myassistant;

import android.content.Context;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;

public class NotesStorage {

    private static final String NOTES_FILE_NAME = "user_notes.txt";

    public static boolean saveNotes(Context context, String text) {
        FileOutputStream fos = null;
        try {
            fos = context.openFileOutput(NOTES_FILE_NAME, Context.MODE_PRIVATE);
            fos.write(text.getBytes());
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

    public static String loadNotes(Context context) {
        StringBuilder builder = new StringBuilder();
        FileInputStream fis = null;
        BufferedReader reader = null;

        try {
            fis = context.openFileInput(NOTES_FILE_NAME);
            reader = new BufferedReader(new InputStreamReader(fis));
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        } catch (IOException e) {
            // no file yet - first run
        } finally {
            try {
                if (reader != null) reader.close();
                if (fis != null) fis.close();
            } catch (IOException ignored) {}
        }

        return builder.toString();
    }
}
