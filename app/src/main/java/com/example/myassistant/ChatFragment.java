package com.example.myassistant;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private View buttonSend;
    private ProgressBar progressBar;
    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> chatHistory = new ArrayList<>();
    private FusedLocationProviderClient fusedLocationClient;
    private String latestLocationText = "";
    private Markwon markwon;
    private TextToSpeech textToSpeech;
    private View layoutEmptyState;
    private View starterChipsScroll;
    private ImageButton buttonExportChat;
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
        initTextToSpeech();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        requestLocationPermissionsIfNeeded();
        editTextPrompt = view.findViewById(R.id.editTextPrompt);
        buttonSend = view.findViewById(R.id.buttonSend);
        progressBar = view.findViewById(R.id.progressBar);
        chatRecyclerView = view.findViewById(R.id.chat_recycler_view);
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState);
        starterChipsScroll = view.findViewById(R.id.starterChipsScroll);
        buttonExportChat = view.findViewById(R.id.buttonExportChat);
        ImageButton buttonClearInput = view.findViewById(R.id.buttonClearInput);
        ImageButton buttonVoice = view.findViewById(R.id.buttonVoice);

        buttonClearInput.setOnClickListener(v -> editTextPrompt.setText(""));
        if (buttonExportChat != null) {
            buttonExportChat.setOnClickListener(v -> exportChatHistory());
        }

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
                if (getContext() != null) {
                    ChatHistoryStorage.clearChatHistory(getContext());
                }
                if (textToSpeech != null) {
                    textToSpeech.stop();
                }
                chatAdapter.setCurrentlySpeakingPosition(-1);
                chatAdapter.notifyDataSetChanged();
                updateEmptyStateVisibility();
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

        chatHistory = ChatHistoryStorage.loadChatHistory(requireContext());
        chatAdapter = new ChatAdapter(chatHistory, markwon, new ChatAdapter.OnMessageActionListener() {
            @Override
            public void onSpeak(ChatMessage message, int position) {
                toggleSpeech(message, position);
            }

            @Override
            public void onCopy(ChatMessage message) {
                copyToClipboard(message.getContent());
            }

            @Override
            public void onSaveAsNote(ChatMessage message) {
                saveResponseAsNote(message.getContent());
            }
        });
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        chatRecyclerView.setAdapter(chatAdapter);
        if (!chatHistory.isEmpty()) {
            chatRecyclerView.scrollToPosition(chatHistory.size() - 1);
        }
        updateEmptyStateVisibility();

        setupStarterChips(view);

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

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(requireContext(), status -> {
            if (status == TextToSpeech.SUCCESS && textToSpeech != null) {
                textToSpeech.setLanguage(Locale.getDefault());
                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {}

                    @Override
                    public void onDone(String utteranceId) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (chatAdapter != null) {
                                    chatAdapter.setCurrentlySpeakingPosition(-1);
                                }
                            });
                        }
                    }

                    @Override
                    public void onError(String utteranceId) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (chatAdapter != null) {
                                    chatAdapter.setCurrentlySpeakingPosition(-1);
                                }
                            });
                        }
                    }
                });
            }
        });
    }

    private void setupStarterChips(View view) {
        TextView chipSummarize = view.findViewById(R.id.chipSummarize);
        TextView chipTasks = view.findViewById(R.id.chipTasks);
        TextView chipPlan = view.findViewById(R.id.chipPlan);
        TextView chipShopping = view.findViewById(R.id.chipShopping);

        View.OnClickListener chipListener = v -> {
            if (v instanceof TextView) {
                String chipText = ((TextView) v).getText().toString();
                String promptText = chipText.replaceFirst("^[\\p{So}\\p{Cn}\\s]+", "").trim();
                editTextPrompt.setText(promptText);
                buttonSend.performClick();
            }
        };
        if (chipSummarize != null) chipSummarize.setOnClickListener(chipListener);
        if (chipTasks != null) chipTasks.setOnClickListener(chipListener);
        if (chipPlan != null) chipPlan.setOnClickListener(chipListener);
        if (chipShopping != null) chipShopping.setOnClickListener(chipListener);
    }

    private void updateEmptyStateVisibility() {
        if (layoutEmptyState != null) {
            layoutEmptyState.setVisibility(chatHistory.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void toggleSpeech(ChatMessage message, int position) {
        if (textToSpeech == null || getContext() == null) {
            Toast.makeText(getContext(), "Text-to-Speech not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        if (chatAdapter.getCurrentlySpeakingPosition() == position) {
            textToSpeech.stop();
            chatAdapter.setCurrentlySpeakingPosition(-1);
            return;
        }

        textToSpeech.stop();
        chatAdapter.setCurrentlySpeakingPosition(position);

        String textToSpeak = stripMarkdownForSpeech(message.getContent());
        if (TextUtils.isEmpty(textToSpeak)) {
            chatAdapter.setCurrentlySpeakingPosition(-1);
            return;
        }

        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "chat_msg_" + position);
        textToSpeech.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, "chat_msg_" + position);
    }

    public static String stripMarkdownForSpeech(String md) {
        if (md == null) return "";
        String s = md;
        s = s.replaceAll("(?s)```.*?```", "");
        s = s.replaceAll("(?m)^#{1,6}\\s+", "");
        s = s.replaceAll("\\*\\*(.*?)\\*\\*", "$1");
        s = s.replaceAll("\\*(.*?)\\*", "$1");
        s = s.replaceAll("__(.*?)__", "$1");
        s = s.replaceAll("_(.*?)_", "$1");
        s = s.replaceAll("`{1,3}(.*?)`{1,3}", "$1");
        s = s.replaceAll("(?m)^\\s*[-*+]\\s+\\[[ xX]?\\]\\s*", "");
        s = s.replaceAll("(?m)^\\s*[-*+]\\s+", "");
        s = s.replaceAll("(?m)^\\s*\\d+\\.\\s+", "");
        s = s.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1");
        return s.trim();
    }

    private void copyToClipboard(String text) {
        if (getContext() == null || TextUtils.isEmpty(text)) return;
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("NotePilot AI", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveResponseAsNote(String content) {
        if (getContext() == null || TextUtils.isEmpty(content)) return;
        String title = deriveTitleFromContent(content);
        List<Note> notes = NotesStorage.loadNotes(getContext());
        if (notes == null) notes = new ArrayList<>();
        Note newNote = new Note(title, content, 0);
        notes.add(0, newNote);
        NotesStorage.saveNotes(getContext(), notes);
        Toast.makeText(getContext(), "Saved to Notes: " + title, Toast.LENGTH_SHORT).show();
    }

    private String deriveTitleFromContent(String content) {
        if (content == null || content.trim().isEmpty()) return "AI Note";
        String clean = stripMarkdownForSpeech(content).trim();
        String firstLine = clean.split("\\R")[0].trim();
        if (firstLine.length() > 35) {
            firstLine = firstLine.substring(0, 35) + "...";
        }
        if (firstLine.isEmpty()) {
            return "AI Note - " + new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(new Date());
        }
        return firstLine;
    }

    private void exportChatHistory() {
        if (chatHistory == null || chatHistory.isEmpty()) {
            Toast.makeText(getContext(), "No chat history to export", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("NotePilot AI Conversation\n");
        sb.append("Date: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date())).append("\n");
        sb.append("-------------------------------------------\n\n");

        for (ChatMessage msg : chatHistory) {
            String sender = msg.getAuthor() == ChatMessage.Author.USER ? "You" : "NotePilot AI";
            sb.append("[").append(sender).append("]:\n");
            sb.append(msg.getContent()).append("\n\n");
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "NotePilot AI Conversation");
        shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(shareIntent, "Share or Export Chat"));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getContext() != null && PermissionStore.getLocationPermission(getContext())) {
            requestLocationPermissionsIfNeeded();
        } else {
            latestLocationText = "";
        }
    }

    private void addToChatHistory(ChatMessage message) {
        chatHistory.add(message);
        if (getContext() != null) {
            ChatHistoryStorage.saveChatHistory(getContext(), chatHistory);
        }
        chatAdapter.notifyItemInserted(chatHistory.size() - 1);
        chatRecyclerView.scrollToPosition(chatHistory.size() - 1);
        updateEmptyStateVisibility();
    }

    private Note findNoteByTitle(String title) {
        if (getContext() == null || title == null) return null;
        String t = title.trim();
        List<Note> notes = NotesStorage.loadNotes(getContext());
        for (Note note : notes) {
            if (note.getTitle() != null && note.getTitle().trim().equalsIgnoreCase(t)) {
                return note;
            }
        }
        return null;
    }

    private boolean doesNoteExist(String title) {
        return findNoteByTitle(title) != null;
    }

    private boolean executeDeleteNote(String title) {
        if (getContext() == null || title == null) return false;
        String target = title.trim();
        List<Note> notes = NotesStorage.loadNotes(getContext());
        int removeIndex = -1;
        for (int i = 0; i < notes.size(); i++) {
            Note n = notes.get(i);
            if (n.getTitle() != null && n.getTitle().trim().equalsIgnoreCase(target)) {
                removeIndex = i;
                break;
            }
        }
        if (removeIndex != -1) {
            notes.remove(removeIndex);
            NotesStorage.saveNotes(getContext(), notes);
            return true;
        }
        return false;
    }

    private boolean executeSetNotePin(String title, boolean isPinned) {
        if (getContext() == null || title == null) return false;
        String target = title.trim();
        List<Note> notes = NotesStorage.loadNotes(getContext());
        for (Note n : notes) {
            if (n.getTitle() != null && n.getTitle().trim().equalsIgnoreCase(target)) {
                n.setPinned(isPinned);
                NotesStorage.saveNotes(getContext(), notes);
                return true;
            }
        }
        return false;
    }

    private String executeSearchNotes(String query) {
        if (getContext() == null || query == null) return "[]";
        String q = query.toLowerCase(Locale.getDefault()).trim();
        List<Note> notes = NotesStorage.loadNotes(getContext());
        JSONArray results = new JSONArray();
        for (Note note : notes) {
            boolean match = false;
            if (note.getTitle() != null && note.getTitle().toLowerCase(Locale.getDefault()).contains(q)) {
                match = true;
            }
            if (!match && !note.isChecklist() && note.getContent() != null && note.getContent().toLowerCase(Locale.getDefault()).contains(q)) {
                match = true;
            }
            if (!match && note.isChecklist() && note.getChecklist() != null) {
                for (ChecklistItem item : note.getChecklist()) {
                    if (item.text != null && item.text.toLowerCase(Locale.getDefault()).contains(q)) {
                        match = true;
                        break;
                    }
                }
            }
            if (match) {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("title", note.getTitle() != null ? note.getTitle() : "Untitled");
                    obj.put("isPinned", note.isPinned());
                    obj.put("isChecklist", note.isChecklist());
                    if (note.isChecklist() && note.getChecklist() != null) {
                        JSONArray itemsArray = new JSONArray();
                        for (ChecklistItem ci : note.getChecklist()) {
                            itemsArray.put((ci.checked ? "[x] " : "[ ] ") + (ci.text != null ? ci.text : ""));
                        }
                        obj.put("items", itemsArray);
                    } else {
                        String c = note.getContent() != null ? note.getContent() : "";
                        if (c.length() > 300) {
                            c = c.substring(0, 300) + "...";
                        }
                        obj.put("content", c);
                    }
                    results.put(obj);
                } catch (JSONException ignored) {}
            }
        }
        return results.toString();
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
            boolean shouldBeChecklist = isChecklistRequested || isLikelyChecklist(title, content);
            if (shouldBeChecklist) {
                List<ChecklistItem> items = parseChecklistItems(content);
                targetNote = new Note(title, items, 0);
            } else {
                targetNote = new Note(title, content, 0);
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
                if (progressBar != null) {
                    progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
                }
                if (buttonSend != null) {
                    buttonSend.setEnabled(!loading);
                }
                if (chatAdapter != null) {
                    chatAdapter.setLoading(loading);
                    if (loading && chatRecyclerView != null) {
                        chatRecyclerView.post(() -> chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1));
                    }
                }
            });
        }
    }

    public void onAskAiAboutNote(String title, String content) {
        String query = "Summarize my note \"" + (title != null && !title.trim().isEmpty() ? title : "Untitled") + "\"";
        if (editTextPrompt != null) {
            editTextPrompt.setText(query);
            editTextPrompt.setSelection(query.length());
            editTextPrompt.requestFocus();
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

    public static String formatFriendlyErrorMessage(String responseBody, int statusCode) {
        String baseMsg = "";
        try {
            if (responseBody != null && !responseBody.trim().isEmpty()) {
                String clean = responseBody.trim();
                Matcher matcher = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]+)\"").matcher(clean);
                if (matcher.find()) {
                    baseMsg = matcher.group(1).replace("\\\"", "\"").replace("\\n", " ").trim();
                } else {
                    JSONObject errObj = null;
                    if (clean.startsWith("[")) {
                        JSONArray arr = new JSONArray(clean);
                        if (arr.length() > 0) errObj = arr.getJSONObject(0).optJSONObject("error");
                    } else if (clean.startsWith("{")) {
                        JSONObject obj = new JSONObject(clean);
                        errObj = obj.optJSONObject("error");
                    }
                    if (errObj != null && errObj.has("message")) {
                        baseMsg = errObj.optString("message", "").trim();
                    }
                }
            }
        } catch (Exception ignored) {}

        if (statusCode == 401) {
            String details = baseMsg.isEmpty() ? "" : "\n\n*Server Details:* " + baseMsg;
            return "🔑 **Invalid API Key (HTTP 401)**\n\nYour Google Gemini API key was not recognized or has expired. Please open **Settings (⚙️)** to update your key.\n\n*Get a free API key at [aistudio.google.com](https://aistudio.google.com)*" + details;
        } else if (statusCode == 403) {
            String details = baseMsg.isEmpty() ? "" : "\n\n*Server Details:* " + baseMsg;
            return "🚫 **Access Denied (HTTP 403)**\n\nYour API key does not have permission for this model or region. Check your API key in Google AI Studio or update it in **Settings (⚙️)**." + details;
        } else if (statusCode == 404) {
            String details = baseMsg.isEmpty() ? "" : "\n\n*Server Details:* " + baseMsg;
            return "🔍 **Model Not Found (HTTP 404)**\n\nThe selected Gemini model is retired or unavailable. Please open **Settings (⚙️)** and select **Gemini 3.6 Flash**." + details;
        } else if (statusCode == 429) {
            String details = baseMsg.isEmpty() ? "" : "\n\n*Server Details:* " + baseMsg;
            return "⏳ **Rate Limit Exceeded (HTTP 429)**\n\nYou've reached Google's free-tier request rate limit. Please wait a minute and try your question again." + details;
        } else if (statusCode >= 500) {
            String details = baseMsg.isEmpty() ? "" : "\n\n*Server Details:* " + baseMsg;
            return "☁️ **Google AI Temporary Outage (HTTP " + statusCode + ")**\n\nGoogle's Gemini servers are temporarily busy or unreachable. Please try again in a few moments." + details;
        }

        if (!baseMsg.isEmpty()) {
            return "❌ **Request Error (HTTP " + statusCode + ")**\n\n" + baseMsg;
        }
        if (responseBody != null && !responseBody.trim().isEmpty()) {
            return "❌ **Request Failed (HTTP " + statusCode + ")**\n\n" + responseBody;
        }
        return "❌ **Request Failed (HTTP " + statusCode + ")**\n\nPlease check your network connection and Settings.";
    }

    private String extractErrorMessage(String responseBody, int statusCode) {
        return formatFriendlyErrorMessage(responseBody, statusCode);
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
        String locationInfo = "";
        if (getContext() != null && PermissionStore.getLocationPermission(getContext())) {
            locationInfo = latestLocationText;
        }
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

            // 1. addOrUpdateNote
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

            // 2. searchNotes
            JSONObject searchTool = new JSONObject();
            searchTool.put("type", "function");
            JSONObject searchFunc = new JSONObject();
            searchFunc.put("name", "searchNotes");
            searchFunc.put("description", "Search user's notes and checklists by keyword or query when the user asks to search or find specific notes.");
            JSONObject searchParams = new JSONObject();
            searchParams.put("type", "object");
            JSONObject searchProps = new JSONObject();
            searchProps.put("query", new JSONObject().put("type", "string").put("description", "The keyword or topic to search for in note titles and contents."));
            searchParams.put("properties", searchProps);
            searchParams.put("required", new JSONArray().put("query"));
            searchFunc.put("parameters", searchParams);
            searchTool.put("function", searchFunc);
            tools.put(searchTool);

            // 3. deleteNote
            JSONObject deleteTool = new JSONObject();
            deleteTool.put("type", "function");
            JSONObject deleteFunc = new JSONObject();
            deleteFunc.put("name", "deleteNote");
            deleteFunc.put("description", "Delete an existing note or checklist by its title when the user explicitly asks to delete or remove it.");
            JSONObject deleteParams = new JSONObject();
            deleteParams.put("type", "object");
            JSONObject deleteProps = new JSONObject();
            deleteProps.put("title", new JSONObject().put("type", "string").put("description", "The title of the note to delete."));
            deleteParams.put("properties", deleteProps);
            deleteParams.put("required", new JSONArray().put("title"));
            deleteFunc.put("parameters", deleteParams);
            deleteTool.put("function", deleteFunc);
            tools.put(deleteTool);

            // 4. setNotePin
            JSONObject pinTool = new JSONObject();
            pinTool.put("type", "function");
            JSONObject pinFunc = new JSONObject();
            pinFunc.put("name", "setNotePin");
            pinFunc.put("description", "Pin or unpin a note to/from the top of the notes list when the user asks to pin or unpin a note.");
            JSONObject pinParams = new JSONObject();
            pinParams.put("type", "object");
            JSONObject pinProps = new JSONObject();
            pinProps.put("title", new JSONObject().put("type", "string").put("description", "The title of the note to pin or unpin."));
            pinProps.put("isPinned", new JSONObject().put("type", "boolean").put("description", "True to pin the note to the top, false to unpin it."));
            pinParams.put("properties", pinProps);
            pinParams.put("required", new JSONArray().put("title").put("isPinned"));
            pinFunc.put("parameters", pinParams);
            pinTool.put("function", pinFunc);
            tools.put(pinTool);

            jsonBody.put("tools", tools);
            jsonBody.put("tool_choice", "auto");

            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "You are a helpful personal assistant for NotePilot.\n\n" +
                    "Capabilities & Tools:\n" +
                    "- You can freely read, summarize, analyze, compare, extract, and plan using the user's notes below.\n" +
                    "- You can answer questions directly from the notes context without using any tools.\n" +
                    "- 'searchNotes': Search notes by keyword or query.\n" +
                    "- 'addOrUpdateNote': Add or update note/checklist content when user asks to create, edit, or append.\n" +
                    "- 'deleteNote': Delete a note when user explicitly asks to remove/delete a note.\n" +
                    "- 'setNotePin': Pin or unpin a note when user asks to pin or unpin it.\n\n" +
                    "Tool usage policy:\n" +
                    "- Only call action tools ('addOrUpdateNote', 'deleteNote', 'setNotePin') when the user explicitly requests those actions.\n" +
                    "- Call 'searchNotes' if the user specifically asks to search or locate notes that might have more details.\n" +
                    "- Never call tools for simple summarization, explanation, Q&A, brainstorming, or general planning.\n" +
                    "- If the user requests an action and also asks for an answer/summary, call the tool and provide the answer in the follow-up.\n" +
                    "- When proposing content to append, avoid duplicating what's already in the note; only include new unique items.\n\n" +
                    "Response style:\n" +
                    "- When answering, explicitly mention that you used the notes.\n" +
                    "- If the relevant information is missing from notes, state that briefly.\n\n" +
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

                                final String finalToolCallId = toolCallId;
                                final String finalFunctionName = functionName;

                                final JSONObject assistantMsg = new JSONObject();
                                assistantMsg.put("role", "assistant");
                                if (message.has("content") && !message.isNull("content")) {
                                    assistantMsg.put("content", message.getString("content"));
                                } else {
                                    assistantMsg.put("content", JSONObject.NULL);
                                }
                                assistantMsg.put("tool_calls", toolCalls);

                                if ("searchNotes".equals(functionName)) {
                                    String query = arguments.optString("query", "");
                                    String searchResultJson = executeSearchNotes(query);
                                    sendToolFollowUp(apiKey, endpoint, model, messagesArray, assistantMsg, finalToolCallId, finalFunctionName, searchResultJson);
                                } else if ("deleteNote".equals(functionName)) {
                                    final String title = arguments.optString("title", "").trim();
                                    if (TextUtils.isEmpty(title)) {
                                        handleNormalReply(choice, message);
                                        setLoading(false);
                                        return;
                                    }
                                    final Note noteToDelete = findNoteByTitle(title);
                                    if (noteToDelete == null) {
                                        JSONObject errResult = new JSONObject();
                                        errResult.put("status", "error");
                                        errResult.put("message", "Note '" + title + "' was not found.");
                                        sendToolFollowUp(apiKey, endpoint, model, messagesArray, assistantMsg, finalToolCallId, finalFunctionName, errResult.toString());
                                        return;
                                    }

                                    if (getContext() != null && PermissionStore.getEditPermission(getContext())) {
                                        boolean deleted = executeDeleteNote(title);
                                        if (getActivity() != null) {
                                            getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Note '" + title + "' deleted by AI", Toast.LENGTH_SHORT).show());
                                        }
                                        JSONObject toolResult = new JSONObject();
                                        toolResult.put("status", deleted ? "success" : "error");
                                        toolResult.put("action", "deleted");
                                        toolResult.put("title", title);
                                        toolResult.put("message", deleted ? "Note '" + title + "' was successfully deleted." : "Failed to delete note '" + title + "'.");
                                        sendToolFollowUp(apiKey, endpoint, model, messagesArray, assistantMsg, finalToolCallId, finalFunctionName, toolResult.toString());
                                    } else {
                                        if (getActivity() instanceof MainActivity) {
                                            String preview = noteToDelete.isChecklist() ? "Checklist with " + (noteToDelete.getChecklist() != null ? noteToDelete.getChecklist().size() : 0) + " items" : (noteToDelete.getContent() != null ? noteToDelete.getContent() : "");
                                            getActivity().runOnUiThread(() -> {
                                                ((MainActivity) getActivity()).showAiChangeConfirmationDialog(
                                                        "DELETE NOTE",
                                                        title,
                                                        "Are you sure you want to delete this note?\n\n" + preview,
                                                        () -> {
                                                            new Thread(() -> {
                                                                boolean deleted = executeDeleteNote(title);
                                                                if (getActivity() != null) {
                                                                    getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Note '" + title + "' deleted by AI", Toast.LENGTH_SHORT).show());
                                                                }
                                                                try {
                                                                    JSONObject toolResult = new JSONObject();
                                                                    toolResult.put("status", deleted ? "success" : "error");
                                                                    toolResult.put("action", "deleted");
                                                                    toolResult.put("title", title);
                                                                    toolResult.put("message", "User approved deleting note '" + title + "'. Note was deleted.");
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
                                                                    toolResult.put("message", "User declined to delete note '" + title + "'. The note was not deleted.");
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
                                } else if ("setNotePin".equals(functionName)) {
                                    final String title = arguments.optString("title", "").trim();
                                    final boolean isPinned = arguments.optBoolean("isPinned", false);
                                    if (TextUtils.isEmpty(title)) {
                                        handleNormalReply(choice, message);
                                        setLoading(false);
                                        return;
                                    }
                                    final Note noteToPin = findNoteByTitle(title);
                                    if (noteToPin == null) {
                                        JSONObject errResult = new JSONObject();
                                        errResult.put("status", "error");
                                        errResult.put("message", "Note '" + title + "' was not found.");
                                        sendToolFollowUp(apiKey, endpoint, model, messagesArray, assistantMsg, finalToolCallId, finalFunctionName, errResult.toString());
                                        return;
                                    }

                                    if (getContext() != null && PermissionStore.getEditPermission(getContext())) {
                                        boolean updated = executeSetNotePin(title, isPinned);
                                        if (getActivity() != null) {
                                            getActivity().runOnUiThread(() -> Toast.makeText(getContext(), (isPinned ? "Note pinned" : "Note unpinned") + " by AI", Toast.LENGTH_SHORT).show());
                                        }
                                        JSONObject toolResult = new JSONObject();
                                        toolResult.put("status", updated ? "success" : "error");
                                        toolResult.put("action", isPinned ? "pinned" : "unpinned");
                                        toolResult.put("title", title);
                                        toolResult.put("message", "Note '" + title + "' was successfully " + (isPinned ? "pinned" : "unpinned") + ".");
                                        sendToolFollowUp(apiKey, endpoint, model, messagesArray, assistantMsg, finalToolCallId, finalFunctionName, toolResult.toString());
                                    } else {
                                        if (getActivity() instanceof MainActivity) {
                                            String badge = isPinned ? "PIN NOTE" : "UNPIN NOTE";
                                            String promptDesc = isPinned ? "Pin note '" + title + "' to top of the notes list?" : "Unpin note '" + title + "' from top of the notes list?";
                                            getActivity().runOnUiThread(() -> {
                                                ((MainActivity) getActivity()).showAiChangeConfirmationDialog(
                                                        badge,
                                                        title,
                                                        promptDesc,
                                                        () -> {
                                                            new Thread(() -> {
                                                                boolean updated = executeSetNotePin(title, isPinned);
                                                                if (getActivity() != null) {
                                                                    getActivity().runOnUiThread(() -> Toast.makeText(getContext(), (isPinned ? "Note pinned" : "Note unpinned") + " by AI", Toast.LENGTH_SHORT).show());
                                                                }
                                                                try {
                                                                    JSONObject toolResult = new JSONObject();
                                                                    toolResult.put("status", updated ? "success" : "error");
                                                                    toolResult.put("action", isPinned ? "pinned" : "unpinned");
                                                                    toolResult.put("title", title);
                                                                    toolResult.put("message", "User approved pin change. Note '" + title + "' is now " + (isPinned ? "pinned" : "unpinned") + ".");
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
                                                                    toolResult.put("message", "User declined to pin/unpin note '" + title + "'. Pin status was not changed.");
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
                                } else if ("addOrUpdateNote".equals(functionName)) {
                                    final String title = arguments.optString("title", "").trim();
                                    final String content = arguments.optString("content", "").trim();
                                    final boolean isChecklist = arguments.optBoolean("isChecklist", false);
                                    final boolean isNewNote = !doesNoteExist(title);

                                    if (TextUtils.isEmpty(title) || TextUtils.isEmpty(content)) {
                                        handleNormalReply(choice, message);
                                        setLoading(false);
                                        return;
                                    }

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
                                            final String actionBadge = isNewNote ? "CREATE NOTE" : "UPDATE NOTE";
                                            getActivity().runOnUiThread(() -> {
                                                ((MainActivity) getActivity()).showAiChangeConfirmationDialog(
                                                        actionBadge,
                                                        title,
                                                        content,
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
                            addToChatHistory(new ChatMessage("Operation completed, but follow-up response timed out.", ChatMessage.Author.MODEL));
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
                                addToChatHistory(new ChatMessage("Operation completed successfully.", ChatMessage.Author.MODEL));
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
        if (!PermissionStore.getLocationPermission(getContext())) {
            latestLocationText = "";
            return;
        }
        boolean fine = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) {
            locationPermissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        } else {
            fetchLastLocationOnce();
        }
    }

    private void fetchLastLocationOnce() {
        if (getContext() == null || !PermissionStore.getLocationPermission(getContext())) {
            latestLocationText = "";
            return;
        }
        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null && getContext() != null && PermissionStore.getLocationPermission(getContext())) {
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
