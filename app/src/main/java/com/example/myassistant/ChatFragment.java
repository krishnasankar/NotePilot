package com.example.myassistant;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import io.noties.markwon.Markwon;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatFragment extends Fragment {

    private static final String OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";
    private EditText editTextPrompt;
    private ImageButton buttonSend;
    private ProgressBar progressBar;
    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> chatHistory = new ArrayList<>();
    private FusedLocationProviderClient fusedLocationClient;
    private String latestLocationText = "";
    private Markwon markwon;
    private OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        markwon = Markwon.create(requireContext());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        requestLocationPermissionsIfNeeded();
        editTextPrompt = view.findViewById(R.id.editTextPrompt);
        buttonSend = view.findViewById(R.id.buttonSend);
        progressBar = view.findViewById(R.id.progressBar);
        chatRecyclerView = view.findViewById(R.id.chat_recycler_view);
        ImageButton buttonClearInput = view.findViewById(R.id.buttonClearInput);
        buttonClearInput.setOnClickListener(v -> editTextPrompt.setText(""));

        editTextPrompt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    buttonClearInput.setVisibility(View.VISIBLE);
                } else {
                    buttonClearInput.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        chatAdapter = new ChatAdapter(chatHistory, markwon);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        chatRecyclerView.setAdapter(chatAdapter);

        buttonSend.setOnClickListener(v -> {
            hideKeyboardAndClearFocus();
            String prompt = editTextPrompt.getText().toString().trim();
            if (prompt.isEmpty()) {
                Toast.makeText(getContext(), "Enter a question", Toast.LENGTH_SHORT).show();
                return;
            }
            editTextPrompt.setText("");
            addToChatHistory(new ChatMessage(prompt, ChatMessage.Author.USER));
            callOpenRouterWithNotes(prompt);
        });

        // Request focus and show keyboard
        editTextPrompt.requestFocus();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if(getContext() != null) {
                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(editTextPrompt, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 200);
        return view;
    }

    private void addToChatHistory(ChatMessage message) {
        chatHistory.add(message);
        chatAdapter.notifyItemInserted(chatHistory.size() - 1);
        chatRecyclerView.scrollToPosition(chatHistory.size() - 1);
    }

    private boolean addOrUpdateNote(String title, String content) {
        if (getContext() == null || TextUtils.isEmpty(title)) return false;
        List<Note> notes = NotesStorage.loadNotes(getContext());
        boolean noteExists = false;
        for (Note note : notes) {
            if (title.equalsIgnoreCase(note.getTitle())) {
                String existingContent = note.getContent();
                String newContent;
                if (existingContent != null && !existingContent.isEmpty()) {
                    if(content.contains(existingContent)) {
                        newContent = content;
                    } else {
                        newContent = existingContent + "\n" + content;
                    }
                } else {
                    newContent = content;
                }
                note.setContent(newContent);
                noteExists = true;
                break;
            }
        }
        if (!noteExists) {
            int[] noteColors = getResources().getIntArray(R.array.note_colors);
            int randomColor = noteColors[new Random().nextInt(noteColors.length)];
            notes.add(new Note(title, content, randomColor));
        }
        NotesStorage.saveNotes(getContext(), notes);
        return !noteExists;
    }

    private void setLoading(boolean loading) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
                buttonSend.setEnabled(!loading);
            });
        }
    }

    private void callOpenRouterWithNotes(String prompt) {
        if (getContext() == null) return;
        String apiKey = ApiKeyStore.getKey(getContext());
        if (apiKey == null || apiKey.trim().isEmpty()) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(getContext(), "Please set your OpenRouter API key (Settings)", Toast.LENGTH_LONG).show();
                });
            }
            return;
        }
        setLoading(true);
        String dateTimeInfo = getLocalDateTimeAndZone();
        String locationInfo = latestLocationText;
        String contextSnippet = "Device context: " + dateTimeInfo;
        if (locationInfo != null && !locationInfo.isEmpty()) {
            contextSnippet += "\nLocation (approx): " + locationInfo;
        }
        List<Note> notesList = NotesStorage.loadNotes(getContext());
        StringBuilder notesContent = new StringBuilder();
        if (notesList != null && !notesList.isEmpty()) {
            for (Note note : notesList) {
                boolean hasTitle = note.getTitle() != null && !note.getTitle().trim().isEmpty();
                notesContent.append("--- Start of Note ---\n");
                notesContent.append("Title: ").append(hasTitle ? note.getTitle(): "No Title").append("\n");
                if (note.isChecklist()) {
                    notesContent.append("Type: Checklist\n");
                    notesContent.append("Items:\n");
                    for (ChecklistItem item : note.getChecklist()) {
                        notesContent.append("- ").append(item.text).append(" (").append(item.checked ? "checked" : "unchecked").append(")\n");
                    }
                } else if (note.getContent() != null && !note.getContent().trim().isEmpty()) {
                    notesContent.append("Type: Text\n");
                    notesContent.append("Content:\n").append(note.getContent()).append("\n");
                }
                notesContent.append("--- End of Note ---\n\n");
            }
        }
        String notes = notesContent.toString();
        int maxNotesChars = 4000;
        if (notes.length() > maxNotesChars) {
            notes = notes.substring(notes.length() - maxNotesChars);
        }
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", "arcee-ai/trinity-mini:free");
            jsonBody.put("temperature", 1.0);
            jsonBody.put("max_tokens", 4096);
            JSONArray tools = new JSONArray();
            JSONObject noteTool = new JSONObject();
            noteTool.put("type", "function");
            JSONObject function = new JSONObject();
            function.put("name", "addOrUpdateNote");
            function.put("description", "Add new content to a note. If a note with the given title exists, the new content will be appended to it. If it doesn't exist, a new note will be created with the given title and content.");
            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            JSONObject properties = new JSONObject();
            properties.put("title", new JSONObject().put("type", "string").put("description", "The title of the note. If the user asks to add a reminder, task, or birthday, this should be 'Reminders', 'Tasks', or 'Birthdays' respectively."));
            properties.put("content", new JSONObject().put("type", "string").put("description", "The content to be added to the note. This will be appended if the note already exists."));
            parameters.put("properties", properties);
            function.put("parameters", parameters);
            noteTool.put("function", function);

            tools.put(noteTool);
            jsonBody.put("tools", tools);
            JSONArray messagesArray = new JSONArray();
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "You are a helpful personal assistant. Your primary role is to assist the user with their notes.\n\n" +
"When the user asks a question, use the content of their notes, provided below, to give a comprehensive answer. Announce that you are using the notes in your response.\n\n" +
"When the user asks you to add or update a note, you must use the 'addOrUpdateNote' function. Be intelligent about whether to append to an existing note or create a new one based on the title.\n\n" +
"Here are the user's notes:\n" + notes + "\n\n" + contextSnippet);
            messagesArray.put(systemMsg);


            for (ChatMessage message : chatHistory) {
                JSONObject chatMessage = new JSONObject();
                chatMessage.put("role", message.getAuthor() == ChatMessage.Author.USER ? "user" : "assistant");
                chatMessage.put("content", message.getContent());
                messagesArray.put(chatMessage);
            }

            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            messagesArray.put(userMessage);

            jsonBody.put("messages", messagesArray);
        } catch (JSONException e) {
            setLoading(false);
            e.printStackTrace();
            if (getContext() != null) {
                Toast.makeText(getContext(), "JSON build failed", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
        Request request = new Request.Builder()
                .url(OPENROUTER_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("HTTP-Referer", "https://your-app-or-domain.com")
                .header("X-Title", "Notes Agent Android")
                .post(body)
                .build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String msg;
                if (e instanceof java.net.SocketTimeoutException) {
                    msg = "The request timed out. Try again or reduce prompt/notes size.";
                } else {
                    msg = "Request failed:\n" + e.getMessage();
                }
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        addToChatHistory(new ChatMessage(msg, ChatMessage.Author.MODEL));
                        setLoading(false);
                    });
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    final String err = "❌ Error:\n" + responseBody;
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            addToChatHistory(new ChatMessage(err, ChatMessage.Author.MODEL));
                            setLoading(false);
                        });
                    }
                    return;
                }
                try {
                    JSONObject json = new JSONObject(responseBody);
                    JSONArray choices = json.optJSONArray("choices");
                    if (choices != null && choices.length() > 0) {
                        JSONObject choice = choices.getJSONObject(0);
                        JSONObject message = choice.optJSONObject("message");
                        if (message != null) {
                            if (message.has("tool_calls")) {
                                JSONArray toolCalls = message.getJSONArray("tool_calls");
                                for (int i = 0; i < toolCalls.length(); i++) {
                                    JSONObject toolCall = toolCalls.getJSONObject(i);
                                    if ("function".equals(toolCall.getString("type"))) {
                                        JSONObject functionCall = toolCall.getJSONObject("function");
                                        String functionName = functionCall.getString("name");
                                        if ("addOrUpdateNote".equals(functionName)) {
                                            JSONObject arguments = new JSONObject(functionCall.getString("arguments"));
                                            final String title = arguments.optString("title", null);
                                            final String content = arguments.optString("content", null);
                                            if (title != null && content != null) {
                                                if (getActivity() != null) {
                                                    getActivity().runOnUiThread(() -> {
                                                        if (getContext() != null && PermissionStore.getEditPermission(getContext())) {
                                                            boolean created = addOrUpdateNote(title, content);
                                                            String summary = (created ? "I have created a new note titled '" : "I have updated the note titled '") + title + "'.";
                                                            addToChatHistory(new ChatMessage(summary, ChatMessage.Author.MODEL));
                                                            Toast.makeText(getContext(), "Note updated by AI", Toast.LENGTH_SHORT).show();
                                                        } else {
                                                            if (getActivity() instanceof MainActivity) {
                                                                ((MainActivity) getActivity()).showAiChangeConfirmationDialog(title, content, () -> {
                                                                    boolean created = addOrUpdateNote(title, content);
                                                                    String summary = (created ? "I have created a new note titled '" : "I have updated the note titled '") + title + "'.";
                                                                    addToChatHistory(new ChatMessage(summary, ChatMessage.Author.MODEL));
                                                                    Toast.makeText(getContext(), "Note updated by AI", Toast.LENGTH_SHORT).show();
                                                                });
                                                            }
                                                        }
                                                    });
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                String reply = message.optString("content", "").trim();
                                if (reply.isEmpty() && message.has("reasoning")) {
                                    reply = message.optString("reasoning", "").trim();
                                }
                                if (reply.isEmpty() && choice.has("reasoning")) {
                                    reply = choice.optString("reasoning", "").trim();
                                }
                                if (reply.isEmpty()) {
                                    reply = "[Model returned empty content.]";
                                }
                                final String finalReply = reply;
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        addToChatHistory(new ChatMessage(finalReply, ChatMessage.Author.MODEL));
                                    });
                                }
                            }
                        }
                    }
                } catch (JSONException e) {
                    final String errorReply = "Response parse failed:\n" + responseBody;
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            addToChatHistory(new ChatMessage(errorReply, ChatMessage.Author.MODEL));
                        });
                    }
                } finally {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            setLoading(false);
                        });
                    }
                }
            }
        });
    }

    private void hideKeyboardAndClearFocus() {
        if (getContext() == null) return;
        View view = requireActivity().getCurrentFocus();
        if (view == null) {
            view = new View(getContext());
        }
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
        if (editTextPrompt != null) {
            editTextPrompt.clearFocus();
        }
    }

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fineGranted = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                Boolean coarseGranted = result.get(Manifest.permission.ACCESS_COARSE_LOCATION);

                if ((fineGranted != null && fineGranted) || (coarseGranted != null && coarseGranted)) {
                    fetchLastLocationOnce();
                } else {
                    latestLocationText = "";
                }
            });

    private void requestLocationPermissionsIfNeeded() {
        if (getContext() == null) return;
        boolean fine = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) {
            locationPermissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        } else {
            fetchLastLocationOnce();
        }
    }

    private void fetchLastLocationOnce() {
        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            latestLocationText = formatLocationHumanReadable(location);
                        } else {
                            latestLocationText = "";
                        }
                    })
                    .addOnFailureListener(e -> {
                        latestLocationText = "";
                    });
        } catch (SecurityException e) {
            latestLocationText = "";
        }
    }

    private String formatLocationHumanReadable(Location location) {
        if (location == null) return "";
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        float accuracy = location.getAccuracy();
        long ts = location.getTime();
        String fixTime = "";
        if (ts > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getDefault());
            fixTime = sdf.format(new Date(ts));
        }
        String loc = String.format(Locale.getDefault(),
                "lat=%.6f, lon=%.6f, acc=±%.0fm", lat, lon, (double) accuracy);
        if (!TextUtils.isEmpty(fixTime)) {
            loc += ", fix_time=" + fixTime;
        }
        return loc;
    }

    private String getLocalDateTimeAndZone() {
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getDefault());
        String localTime = sdf.format(now);
        String tz = TimeZone.getDefault().getID();
        String tzDisplay = TimeZone.getDefault().getDisplayName(false, TimeZone.SHORT, Locale.getDefault());
        String locale = Locale.getDefault().toLanguageTag();
        return String.format(Locale.getDefault(),
                "local_time=%s, timezone=%s (%s), locale=%s",
                localTime, tz, tzDisplay, locale);
    }
}
