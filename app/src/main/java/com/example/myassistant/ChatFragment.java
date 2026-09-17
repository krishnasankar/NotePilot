package com.example.myassistant;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
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
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.widget.Button;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.LinkedHashSet;
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
        ImageButton buttonVoice = view.findViewById(R.id.buttonVoice);
        buttonClearInput.setOnClickListener(v -> editTextPrompt.setText(""));
        ImageButton buttonClearContext = view.findViewById(R.id.buttonClearContext);
        buttonClearContext.setOnClickListener(v2 -> {
            if (progressBar != null && progressBar.getVisibility() == View.VISIBLE) {
                Toast.makeText(getContext(), "Please wait for the current request to finish", Toast.LENGTH_SHORT).show();
                return;
            }
            Dialog dialog = new Dialog(requireContext());
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setContentView(R.layout.dialog_clear_context);
            Button buttonCancel = dialog.findViewById(R.id.buttonCancel);
            Button buttonClear = dialog.findViewById(R.id.buttonClear);
            buttonCancel.setOnClickListener(v -> dialog.dismiss());
            buttonClear.setOnClickListener(v -> {
                chatHistory.clear();
                chatAdapter.notifyDataSetChanged();
                Toast.makeText(getContext(), "Chat context cleared", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
            dialog.show();
        });

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
            callGeminiWithNotes(prompt);
        });

        buttonVoice.setOnClickListener(v -> {
            if (getContext() == null) return;
            if (!android.speech.SpeechRecognizer.isRecognitionAvailable(getContext())) {
                Toast.makeText(getContext(), "Speech recognition is not available on this device", Toast.LENGTH_SHORT).show();
                return;
            }
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            } else {
                startSpeechRecognition();
            }
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

    private boolean doesNoteExist(String title) {
        if (getContext() == null || title == null) return false;
        List<Note> notes = NotesStorage.loadNotes(getContext());
        for (Note note : notes) {
            if (note.getTitle() != null && title.trim().equalsIgnoreCase(note.getTitle().trim())) {
                return true;
            }
        }
        return false;
    }

    private String cleanChecklistItemText(String line) {
        if (line == null) return "";
        String t = line.trim();
        t = t.replaceAll("^[\\s*+-]*\\[[ xX]?\\]\\s*", "");
        t = t.replaceAll("^[\\s*+-]+\\s*", "");
        t = t.replaceAll("^\\d+[.)]\\s*", "");
        return t.trim();
    }

    private List<ChecklistItem> parseChecklistItems(String content) {
        List<ChecklistItem> items = new ArrayList<>();
        if (content == null) return items;
        String[] lines = content.split("\\R");
        for (String line : lines) {
            String clean = cleanChecklistItemText(line);
            if (!clean.isEmpty()) {
                items.add(new ChecklistItem(clean, false));
            }
        }
        return items;
    }

    private void mergeChecklistItems(Note note, String incomingContent) {
        List<ChecklistItem> currentList = note.getChecklist();
        if (currentList == null) {
            currentList = new ArrayList<>();
            note.setChecklist(currentList);
        }

        LinkedHashSet<String> existingNormalized = new LinkedHashSet<>();
        for (ChecklistItem item : currentList) {
            if (item.text != null && !item.text.trim().isEmpty()) {
                existingNormalized.add(normalizeForCompare(item.text));
            }
        }

        String[] lines = incomingContent.split("\\R");
        for (String line : lines) {
            String clean = cleanChecklistItemText(line);
            if (!clean.isEmpty()) {
                String norm = normalizeForCompare(clean);
                if (!existingNormalized.contains(norm)) {
                    existingNormalized.add(norm);
                    currentList.add(new ChecklistItem(clean, false));
                }
            }
        }
    }

    private boolean isLikelyChecklist(String title, String content) {
        String lowerTitle = (title != null ? title : "").toLowerCase(Locale.getDefault());
        if (lowerTitle.contains("list") || lowerTitle.contains("todo") || lowerTitle.contains("checklist") || lowerTitle.contains("tasks") || lowerTitle.contains("shopping") || lowerTitle.contains("groceries")) {
            return true;
        }
        if (content != null) {
            String[] lines = content.split("\\R");
            int markerCount = 0;
            for (String line : lines) {
                String t = line.trim();
                if (t.startsWith("- [ ]") || t.startsWith("- [x]") || t.startsWith("* [ ]") || t.startsWith("- ") || t.startsWith("* ")) {
                    markerCount++;
                }
            }
            return markerCount >= 2;
        }
        return false;
    }

    private boolean addOrUpdateNote(String title, String content, boolean isChecklistRequested) {
        if (getContext() == null) return false;
        if (title != null) title = title.trim();
        if (content != null) content = content.trim();
        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(content)) return false;

        List<Note> notes = NotesStorage.loadNotes(getContext());
        Note targetNote = null;
        for (Note note : notes) {
            if (note.getTitle() != null && title.equalsIgnoreCase(note.getTitle().trim())) {
                targetNote = note;
                break;
            }
        }

        boolean isNew = (targetNote == null);

        if (targetNote != null) {
            if (targetNote.isChecklist()) {
                mergeChecklistItems(targetNote, content);
            } else {
                String existingContent = targetNote.getContent();
                targetNote.setContent(mergeNoteContent(existingContent == null ? "" : existingContent, content));
            }
            targetNote.setLastModified(System.currentTimeMillis());
        } else {
            int[] noteColors = getResources().getIntArray(R.array.note_colors);
            int randomColor = noteColors[new Random().nextInt(noteColors.length)];

            boolean shouldBeChecklist = isChecklistRequested || isLikelyChecklist(title, content);
            if (shouldBeChecklist) {
                List<ChecklistItem> items = parseChecklistItems(content);
                targetNote = new Note(title, items, randomColor);
            } else {
                targetNote = new Note(title, content, randomColor);
            }
            notes.add(targetNote);
        }

        NotesStorage.saveNotes(getContext(), notes);
        return isNew;
    }

    private String mergeNoteContent(String existing, String incoming) {
        String existingNorm = normalizeForCompare(existing);
        String incomingNorm = normalizeForCompare(incoming);

        if (existingNorm.isEmpty()) return incoming.trim();
        if (incomingNorm.isEmpty()) return existing;

        if (existingNorm.equals(incomingNorm)) {
            return existing;
        }
        if (existingNorm.contains(incomingNorm)) {
            return existing;
        }
        if (incomingNorm.contains(existingNorm)) {
            return incoming.trim();
        }

        // Merge by unique lines, preserving order
        LinkedHashSet<String> normalizedSeen = new LinkedHashSet<>();
        LinkedHashSet<String> mergedLines = new LinkedHashSet<>();

        for (String line : existing.split("\\R")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            String key = normalizeForCompare(t);
            if (normalizedSeen.add(key)) {
                mergedLines.add(t);
            }
        }
        for (String line : incoming.split("\\R")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            String key = normalizeForCompare(t);
            if (normalizedSeen.add(key)) {
                mergedLines.add(t);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String l : mergedLines) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(l);
        }
        return sb.toString();
    }

    private String normalizeForCompare(String s) {
        if (s == null) return "";
        String out = s.replaceAll("[\\s\\u00A0]+", " ").trim();
        out = out.replaceAll("[\\p{Z}]+", " ");
        out = out.replaceAll("\\s*([,.;:!?])\\s*", "$1");
        out = out.toLowerCase(Locale.getDefault());
        return out;
    }

    private String buildNotesContext(List<Note> notesList, int maxChars) {
        if (notesList == null || notesList.isEmpty()) {
            return "No notes found.";
        }

        List<Note> sortedNotes = new ArrayList<>(notesList);
        Collections.sort(sortedNotes, (n1, n2) -> {
            if (n1.isPinned() && !n2.isPinned()) return -1;
            if (!n1.isPinned() && n2.isPinned()) return 1;
            if (n1.isPinned() && n2.isPinned()) return Long.compare(n2.getPinnedTimestamp(), n1.getPinnedTimestamp());
            return Long.compare(n2.getLastModified(), n1.getLastModified());
        });

        StringBuilder notesContent = new StringBuilder();
        boolean notesOmitted = false;

        for (Note note : sortedNotes) {
            boolean hasTitle = note.getTitle() != null && !note.getTitle().trim().isEmpty();
            StringBuilder noteBlock = new StringBuilder();
            noteBlock.append("--- Start of Note ---\n");
            noteBlock.append("Title: ").append(hasTitle ? note.getTitle() : "No Title").append("\n");
            if (note.isPinned()) {
                noteBlock.append("Pinned: Yes\n");
            }
            if (note.isChecklist()) {
                noteBlock.append("Type: Checklist\n");
                noteBlock.append("Items:\n");
                if (note.getChecklist() != null) {
                    for (ChecklistItem item : note.getChecklist()) {
                        noteBlock.append("- ").append(item.text).append(" (").append(item.checked ? "checked" : "unchecked").append(")\n");
                    }
                }
            } else if (note.getContent() != null && !note.getContent().trim().isEmpty()) {
                noteBlock.append("Type: Text\n");
                noteBlock.append("Content:\n").append(note.getContent()).append("\n");
            }
            noteBlock.append("--- End of Note ---\n\n");

            if (notesContent.length() + noteBlock.length() <= maxChars) {
                notesContent.append(noteBlock);
            } else {
                notesOmitted = true;
                break;
            }
        }

        if (notesOmitted) {
            notesContent.append("[Notice: Additional older notes were omitted from context to preserve token limits.]\n\n");
        }

        return notesContent.toString();
    }

    private void setLoading(boolean loading) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
                buttonSend.setEnabled(!loading);
            });
        }
    }

    private void handleNormalReply(JSONObject choice, JSONObject message) {
        String reply = message.optString("content", "").trim();
        if (reply.isEmpty() && message.has("reasoning")) {
            reply = message.optString("reasoning", "").trim();
        }
        if (reply.isEmpty() && choice != null && choice.has("reasoning")) {
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

    private String extractErrorMessage(String responseBody, int statusCode) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return "❌ Request failed (HTTP " + statusCode + ").";
        }
        try {
            String clean = responseBody.trim();
            JSONObject errObj = null;
            if (clean.startsWith("[")) {
                JSONArray arr = new JSONArray(clean);
                if (arr.length() > 0) {
                    JSONObject first = arr.getJSONObject(0);
                    errObj = first.optJSONObject("error");
                }
            } else if (clean.startsWith("{")) {
                JSONObject obj = new JSONObject(clean);
                errObj = obj.optJSONObject("error");
            }
            if (errObj != null && errObj.has("message")) {
                String msg = errObj.optString("message", "");
                if (!msg.isEmpty()) {
                    return "❌ Error (" + statusCode + "):\n" + msg;
                }
            }
        } catch (Exception ignored) {}
        return "❌ Error (" + statusCode + "):\n" + responseBody;
    }

    private void callGeminiWithNotes(String prompt) {
        if (getContext() == null) return;
        final String endpoint = AiSettingsStore.getActiveEndpoint(getContext());
        final String model = AiSettingsStore.getActiveModel(getContext());
        final String apiKey = ApiKeyStore.getKey(getContext());

        if (apiKey == null || apiKey.trim().isEmpty()) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(getContext(), "Please set your Google Gemini API key in Settings (get a free key at aistudio.google.com)", Toast.LENGTH_LONG).show();
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
        String notes = buildNotesContext(notesList, 15000);

        JSONObject jsonBody = new JSONObject();
        JSONArray messagesArray = new JSONArray();
        try {
            jsonBody.put("model", model);
            jsonBody.put("temperature", 0.3);
            jsonBody.put("max_tokens", 4096);

            JSONArray tools = new JSONArray();
            JSONObject noteTool = new JSONObject();
            noteTool.put("type", "function");
            JSONObject function = new JSONObject();
            function.put("name", "addOrUpdateNote");
            function.put("description", "Add or update a note ONLY when the user explicitly asks to add/create/edit/append/update a note, reminder, task, checklist, or birthday. Avoid duplicating existing content; append only new unique items.");
            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            JSONObject properties = new JSONObject();
            properties.put("title", new JSONObject().put("type", "string").put("description", "The title of the note. If the user asks to add a reminder, task, or birthday, this should be 'Reminders', 'Tasks', or 'Birthdays' respectively."));
            properties.put("content", new JSONObject().put("type", "string").put("description", "The content to be added to the note. For checklists or to-do lists, separate each item with a newline."));
            properties.put("isChecklist", new JSONObject().put("type", "boolean").put("description", "Set to true if this is a checklist, to-do list, grocery list, or task list."));
            parameters.put("properties", properties);
            parameters.put("required", new JSONArray().put("title").put("content"));
            function.put("parameters", parameters);
            noteTool.put("function", function);

            tools.put(noteTool);
            jsonBody.put("tools", tools);
            jsonBody.put("tool_choice", "auto");

            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "You are a helpful personal assistant for notes.\n\n" +
                    "Capabilities:\n" +
                    "- You can freely read, summarize, analyze, compare, extract, and plan using the user's notes below.\n" +
                    "- You can answer questions directly from the notes without using any tools.\n\n" +
                    "Tool usage policy (strict):\n" +
                    "- Only call the 'addOrUpdateNote' function when the user explicitly asks to add, create, edit, append, update, or modify a note, reminder, task, or birthday.\n" +
                    "- Never call tools for summarization, explanation, Q&A, brainstorming, or planning.\n" +
                    "- Do not claim that you cannot summarize or answer; you can and should answer directly using the notes context.\n" +
                    "- If the user both requests an update and also asks for an answer/summary, call the tool to update and also provide the requested answer in your normal assistant message.\n" +
                    "- When proposing content to append, avoid duplicating what's already in the note; only include new unique items.\n\n" +
                    "Response style:\n" +
                    "- When answering, explicitly mention that you used the notes.\n" +
                    "- If the relevant information is missing from notes, state that briefly and, if appropriate, ask a concise follow-up.\n\n" +
                    "Here are the user's notes:\n" + notes + "\n\n" + contextSnippet);
            messagesArray.put(systemMsg);

            for (ChatMessage message : chatHistory) {
                JSONObject chatMessage = new JSONObject();
                chatMessage.put("role", message.getAuthor() == ChatMessage.Author.USER ? "user" : "assistant");
                chatMessage.put("content", message.getContent());
                messagesArray.put(chatMessage);
            }

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
                .url(endpoint)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
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
                    final String err = extractErrorMessage(responseBody, response.code());
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
                                if (toolCalls.length() == 0) {
                                    handleNormalReply(choice, message);
                                    setLoading(false);
                                    return;
                                }

                                JSONObject toolCall = toolCalls.getJSONObject(0);
                                String functionName = "";
                                JSONObject arguments = new JSONObject();
                                String toolCallId = toolCall.optString("id", "call_1");
                                if ("function".equals(toolCall.getString("type"))) {
                                    JSONObject functionCall = toolCall.getJSONObject("function");
                                    functionName = functionCall.getString("name");
                                    arguments = new JSONObject(functionCall.getString("arguments"));
                                }

                                if (!"addOrUpdateNote".equals(functionName)) {
                                    handleNormalReply(choice, message);
                                    setLoading(false);
                                    return;
                                }

                                final String title = arguments.optString("title", "").trim();
                                final String content = arguments.optString("content", "").trim();
                                final boolean isChecklist = arguments.optBoolean("isChecklist", false);
                                final boolean isNewNote = !doesNoteExist(title);
                                final String finalToolCallId = toolCallId;
                                final String finalFunctionName = functionName;

                                if (TextUtils.isEmpty(title) || TextUtils.isEmpty(content)) {
                                    handleNormalReply(choice, message);
                                    setLoading(false);
                                    return;
                                }

                                final JSONObject assistantMsg = new JSONObject();
                                assistantMsg.put("role", "assistant");
                                if (message.has("content") && !message.isNull("content")) {
                                    assistantMsg.put("content", message.getString("content"));
                                } else {
                                    assistantMsg.put("content", JSONObject.NULL);
                                }
                                assistantMsg.put("tool_calls", toolCalls);

                                if (getContext() != null && PermissionStore.getEditPermission(getContext())) {
                                    boolean created = addOrUpdateNote(title, content, isChecklist);
                                    if (getActivity() != null) {
                                        getActivity().runOnUiThread(() -> {
                                            Toast.makeText(getContext(), (created ? "Note created" : "Note updated") + " by AI", Toast.LENGTH_SHORT).show();
                                        });
                                    }
                                    JSONObject toolResult = new JSONObject();
                                    toolResult.put("status", "success");
                                    toolResult.put("action", created ? "created" : "updated");
                                    toolResult.put("title", title);
                                    toolResult.put("message", "Note '" + title + "' was successfully " + (created ? "created" : "updated") + ".");
                                    sendToolFollowUp(apiKey, endpoint, model, messagesArray, assistantMsg, finalToolCallId, finalFunctionName, toolResult.toString());
                                } else {
                                    if (getActivity() instanceof MainActivity) {
                                        getActivity().runOnUiThread(() -> {
                                            ((MainActivity) getActivity()).showAiChangeConfirmationDialog(
                                                    title,
                                                    content,
                                                    isNewNote,
                                                    () -> {
                                                        new Thread(() -> {
                                                            boolean created = addOrUpdateNote(title, content, isChecklist);
                                                            if (getActivity() != null) {
                                                                getActivity().runOnUiThread(() -> {
                                                                    Toast.makeText(getContext(), (created ? "Note created" : "Note updated") + " by AI", Toast.LENGTH_SHORT).show();
                                                                });
                                                            }
                                                            try {
                                                                JSONObject toolResult = new JSONObject();
                                                                toolResult.put("status", "success");
                                                                toolResult.put("action", created ? "created" : "updated");
                                                                toolResult.put("title", title);
                                                                toolResult.put("message", "User approved the changes. Note '" + title + "' was successfully " + (created ? "created" : "updated") + ".");
                                                                sendToolFollowUp(apiKey, endpoint, model, messagesArray, assistantMsg, finalToolCallId, finalFunctionName, toolResult.toString());
                                                            } catch (JSONException ignored) {
                                                                setLoading(false);
                                                            }
                                                        }).start();
                                                    },
                                                    () -> {
                                                        new Thread(() -> {
                                                            try {
                                                                JSONObject toolResult = new JSONObject();
                                                                toolResult.put("status", "cancelled");
                                                                toolResult.put("message", "User declined to allow this note modification. The note was not changed.");
                                                                sendToolFollowUp(apiKey, endpoint, model, messagesArray, assistantMsg, finalToolCallId, finalFunctionName, toolResult.toString());
                                                            } catch (JSONException ignored) {
                                                                setLoading(false);
                                                            }
                                                        }).start();
                                                    }
                                            );
                                        });
                                    } else {
                                        setLoading(false);
                                    }
                                }
                            } else {
                                handleNormalReply(choice, message);
                                setLoading(false);
                            }
                        } else {
                            setLoading(false);
                        }
                    } else {
                        setLoading(false);
                    }
                } catch (JSONException e) {
                    final String errorReply = "Response parse failed:\n" + responseBody;
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            addToChatHistory(new ChatMessage(errorReply, ChatMessage.Author.MODEL));
                            setLoading(false);
                        });
                    }
                }
            }
        });
    }

    private void sendToolFollowUp(String apiKey, String endpoint, String model, JSONArray originalMessages, JSONObject assistantMsg, String toolCallId, String functionName, String toolResultJson) {
        try {
            JSONArray followUpMessages = new JSONArray();
            for (int i = 0; i < originalMessages.length(); i++) {
                followUpMessages.put(originalMessages.getJSONObject(i));
            }
            followUpMessages.put(assistantMsg);

            JSONObject toolMsg = new JSONObject();
            toolMsg.put("role", "tool");
            toolMsg.put("tool_call_id", toolCallId);
            toolMsg.put("name", functionName);
            toolMsg.put("content", toolResultJson);
            followUpMessages.put(toolMsg);

            JSONObject followUpBody = new JSONObject();
            followUpBody.put("model", model);
            followUpBody.put("temperature", 0.3);
            followUpBody.put("max_tokens", 4096);
            followUpBody.put("messages", followUpMessages);

            RequestBody body = RequestBody.create(followUpBody.toString(), JSON);
            Request request = new Request.Builder()
                    .url(endpoint)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            addToChatHistory(new ChatMessage("Note operation completed, but follow-up response timed out.", ChatMessage.Author.MODEL));
                            setLoading(false);
                        });
                    }
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    try {
                        if (!response.isSuccessful()) {
                            final String err = extractErrorMessage(responseBody, response.code());
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    addToChatHistory(new ChatMessage(err, ChatMessage.Author.MODEL));
                                });
                            }
                            return;
                        }
                        JSONObject json = new JSONObject(responseBody);
                        JSONArray choices = json.optJSONArray("choices");
                        if (choices != null && choices.length() > 0) {
                            JSONObject choice = choices.getJSONObject(0);
                            JSONObject msg = choice.optJSONObject("message");
                            if (msg != null) {
                                handleNormalReply(choice, msg);
                            }
                        }
                    } catch (JSONException e) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                addToChatHistory(new ChatMessage("Note operation completed successfully.", ChatMessage.Author.MODEL));
                            });
                        }
                    } finally {
                        setLoading(false);
                    }
                }
            });
        } catch (JSONException e) {
            setLoading(false);
        }
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

    private final ActivityResultLauncher<String> audioPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startSpeechRecognition();
                } else {
                    Toast.makeText(getContext(), "Audio permission is required for voice input", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Intent> speechRecognitionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                    ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches != null && !matches.isEmpty()) {
                        String spokenText = matches.get(0);
                        editTextPrompt.setText(spokenText);
                        editTextPrompt.setSelection(spokenText.length());
                    }
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

    private void startSpeechRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message");
        try {
            speechRecognitionLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Speech recognition failed to start", Toast.LENGTH_SHORT).show();
        }
    }
}
