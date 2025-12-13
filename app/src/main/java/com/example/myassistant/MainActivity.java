package com.example.myassistant;import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.content.res.ColorStateList;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.text.TextUtils;
import android.content.Context;
import android.view.inputmethod.InputMethodManager;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.util.Random;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";
    private EditText editTextPrompt;
    private ImageButton buttonSend;
    private ProgressBar progressBar;
    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> chatHistory = new ArrayList<>();
    private ImageButton buttonNotes;
    private ImageButton buttonSettings;
    private View keyStatusDot;
    private FusedLocationProviderClient fusedLocationClient;
    private String latestLocationText = "";
    private OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private Animation loadingAnimation;

    private String getApiKey() {
        String k = ApiKeyStore.getKey(this);
        return (k == null) ? "" : k;
    }

    private void showApiKeyDialog() {
        android.view.LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_api_key, null);

        EditText input = view.findViewById(R.id.editApiKey);
        Button btnSave = view.findViewById(R.id.btnSave);
        Button btnClear = view.findViewById(R.id.btnClear);
        Button btnCancel = view.findViewById(R.id.btnCancel);

        String existing = ApiKeyStore.getKey(this);
        if (existing != null && !existing.isEmpty()) {
            String masked = existing.length() > 8 ? "****" + existing.substring(existing.length() - 8) : "****";
            input.setText(masked);
        }

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setView(view)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnSave.setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (value.isEmpty()) {
                Toast.makeText(MainActivity.this, "API key cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            if (value.startsWith("****")) {
                Toast.makeText(MainActivity.this, "No changes saved", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                return;
            }
            boolean ok = ApiKeyStore.saveKey(MainActivity.this, value);
            if (ok) {
                Toast.makeText(MainActivity.this, "API key saved", Toast.LENGTH_SHORT).show();
                updateKeyStatus();
            } else {
                Toast.makeText(MainActivity.this, "Failed to save API key", Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });

        btnClear.setOnClickListener(v -> {
            boolean ok = ApiKeyStore.clearKey(MainActivity.this);
            if (ok) {
                Toast.makeText(MainActivity.this, "API key cleared", Toast.LENGTH_SHORT).show();
                updateKeyStatus();
            } else {
                Toast.makeText(MainActivity.this, "Failed to clear API key", Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateKeyStatus();
    }

    private void updateKeyStatus() {
        if (buttonSettings == null || keyStatusDot == null || chatRecyclerView == null) {
            return;
        }
        String apiKey = ApiKeyStore.getKey(this);
        boolean hasKey = apiKey != null && !apiKey.trim().isEmpty();
        buttonSettings.setAlpha(hasKey ? 1.0f : 0.6f);
        keyStatusDot.setVisibility(View.VISIBLE);
        int color = hasKey
                ? ContextCompat.getColor(this, R.color.key_present_green)
                : ContextCompat.getColor(this, R.color.key_missing_red);
        keyStatusDot.setBackgroundTintList(ColorStateList.valueOf(color));
        boolean hasText = chatHistory.size() > 0;
        boolean isLoading = false;
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
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SplashScreen.installSplashScreen(this);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.rootLayout);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            int statusBarHeight = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int imeHeight = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            v.setPadding(v.getPaddingLeft(), statusBarHeight, v.getPaddingRight(), imeHeight);
            return windowInsets;
        });

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        requestLocationPermissionsIfNeeded();
        buttonSettings = findViewById(R.id.buttonSettings);
        keyStatusDot = findViewById(R.id.keyStatusDot);
        editTextPrompt = findViewById(R.id.editTextPrompt);
        buttonSend = findViewById(R.id.buttonSend);
        progressBar = findViewById(R.id.progressBar);
        chatRecyclerView = findViewById(R.id.chat_recycler_view);
        buttonNotes = findViewById(R.id.buttonNotes);
        ImageButton buttonClearInput = findViewById(R.id.buttonClearInput);
        buttonClearInput.setOnClickListener(v -> editTextPrompt.setText(""));

        chatAdapter = new ChatAdapter(chatHistory);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatRecyclerView.setAdapter(chatAdapter);

        buttonSend.setOnClickListener(v -> {
            hideKeyboardAndClearFocus();
            String prompt = editTextPrompt.getText().toString().trim();
            if (prompt.isEmpty()) {
                Toast.makeText(MainActivity.this, "Enter a question", Toast.LENGTH_SHORT).show();
                return;
            }
            editTextPrompt.setText("");
            addToChatHistory(new ChatMessage(prompt, ChatMessage.Author.USER));
            callOpenRouterWithNotes(prompt);
        });
        buttonNotes.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, NotesActivity.class);
            startActivity(intent);
        });
        buttonSettings.setOnClickListener(v -> showApiKeyDialog());
        updateKeyStatus();

        // Request focus and show keyboard
        editTextPrompt.requestFocus();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(editTextPrompt, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 200);
    }

    private void addToChatHistory(ChatMessage message) {
        chatHistory.add(message);
        chatAdapter.notifyItemInserted(chatHistory.size() - 1);
        chatRecyclerView.scrollToPosition(chatHistory.size() - 1);
    }

    private boolean addOrUpdateNote(String title, String content) {
        List<Note> notes = NotesStorage.loadNotes(this);
        boolean noteExists = false;
        for (Note note : notes) {
            if (note.getTitle().equalsIgnoreCase(title)) {
                note.setContent(content);
                noteExists = true;
                break;
            }
        }
        if (!noteExists) {
            int[] noteColors = getResources().getIntArray(R.array.note_colors);
            int randomColor = noteColors[new Random().nextInt(noteColors.length)];
            notes.add(new Note(title, content, randomColor));
        }
        NotesStorage.saveNotes(this, notes);
        return !noteExists;
    }

    private void setLoading(boolean loading) {
        runOnUiThread(() -> {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            buttonSend.setEnabled(!loading);
            if (buttonNotes != null) buttonNotes.setEnabled(!loading);
            boolean hasText = chatHistory.size() > 0;
        });
    }

    private void callOpenRouterWithNotes(String prompt) {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            runOnUiThread(() -> {
                setLoading(false);
                Toast.makeText(MainActivity.this, "Please set your OpenRouter API key (Settings)", Toast.LENGTH_LONG).show();
            });
            return;
        }
        setLoading(true);
        String dateTimeInfo = getLocalDateTimeAndZone();
        String locationInfo = latestLocationText;
        String contextSnippet = "Device context: " + dateTimeInfo;
        if (locationInfo != null && !locationInfo.isEmpty()) {
            contextSnippet += "\nLocation (approx): " + locationInfo;
        }
        List<Note> notesList = NotesStorage.loadNotes(this);
        StringBuilder notesContent = new StringBuilder();
        if (notesList != null && !notesList.isEmpty()) {
            for (Note note : notesList) {
                boolean hasTitle = note.getTitle() != null && !note.getTitle().trim().isEmpty();
                boolean hasContent = note.getContent() != null && !note.getContent().trim().isEmpty();
                if (hasTitle || hasContent) {
                    notesContent.append("--- Start of Note ---\n");
                    if (hasTitle) {
                        notesContent.append("Title: ").append(note.getTitle()).append("\n");
                    }
                    if (hasContent) {
                        notesContent.append("Content:\n").append(note.getContent()).append("\n");
                    }
                    notesContent.append("--- End of Note ---\n\n");
                }
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
            function.put("description", "Add or update a note");
            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            JSONObject properties = new JSONObject();
            properties.put("title", new JSONObject().put("type", "string").put("description", "The title of the note"));
            properties.put("content", new JSONObject().put("type", "string").put("description", "The content of the note"));
            parameters.put("properties", properties);
            function.put("parameters", parameters);
            noteTool.put("function", function);
            tools.put(noteTool);
            jsonBody.put("tools", tools);
            JSONArray messagesArray = new JSONArray();
            if (!notes.isEmpty()) {
                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content",
                        "You are a helpful personal assistant. The user's notes are provided below, enclosed in '--- Start of Note ---' and '--- End of Note ---'. When the user asks a question, your primary task is to **thoroughly search the provided notes** to find the answer.\n" +
                        "- If you find relevant information, use it to directly answer the question.\n" +
                        "- If the user asks about reminders, tasks, or anything that might be in their notes, search for keywords like 'reminder', 'task', 'todo', etc.\n" +
                        "- If you cannot find an answer in the notes, you must explicitly state that you could not find any relevant information in the user's notes.\n" +
                        "- Do not make up information. Your knowledge is limited to the notes provided.\n" +
                        "- You also have the ability to add or update notes using the 'addOrUpdateNote' tool if the user explicitly asks you to.\n\n" + notes + "\n\n" + contextSnippet);
                messagesArray.put(systemMsg);
            }

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
            Toast.makeText(this, "JSON build failed", Toast.LENGTH_SHORT).show();
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
                runOnUiThread(() -> {
                    addToChatHistory(new ChatMessage(msg, ChatMessage.Author.MODEL));
                    setLoading(false);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    final String err = "❌ Error:\n" + responseBody;
                    runOnUiThread(() -> {
                        addToChatHistory(new ChatMessage(err, ChatMessage.Author.MODEL));
                        setLoading(false);
                    });
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
                                            String title = arguments.optString("title", null);
                                            String content = arguments.optString("content", null);
                                            if (title != null && content != null) {
                                                runOnUiThread(() -> {
                                                    boolean created = addOrUpdateNote(title, content);
                                                    String summary = (created ? "I have created a new note titled '" : "I have updated the note titled '") + title + "'.";
                                                    addToChatHistory(new ChatMessage(summary, ChatMessage.Author.MODEL));
                                                    Toast.makeText(MainActivity.this, "Note updated by AI", Toast.LENGTH_SHORT).show();
                                                });
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
                                runOnUiThread(() -> {
                                    addToChatHistory(new ChatMessage(finalReply, ChatMessage.Author.MODEL));
                                });
                            }
                        }
                    }
                } catch (JSONException e) {
                    final String errorReply = "Response parse failed:\n" + responseBody;
                    runOnUiThread(() -> {
                        addToChatHistory(new ChatMessage(errorReply, ChatMessage.Author.MODEL));
                    });
                } finally {
                    runOnUiThread(() -> {
                        setLoading(false);
                    });
                }
            }
        });
    }

    private void hideKeyboardAndClearFocus() {
        View view = this.getCurrentFocus();
        if (view == null) {
            view = new View(this);
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
        if (editTextPrompt != null) {
            editTextPrompt.clearFocus();
        }
    }
}
