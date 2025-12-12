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

public class MainActivity extends AppCompatActivity {

    private static final String OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";
    private EditText editTextPrompt;
    private Button buttonSend;
    private ProgressBar progressBar;
    private TextView textViewResponse;
    private Button buttonNotes;
    private ImageButton buttonCopyResponse;
    private ImageButton buttonSettings;
    private View keyStatusDot;
    private FusedLocationProviderClient fusedLocationClient;
    private String latestLocationText = "";
    private View responseCard;
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
        if (buttonSettings == null || keyStatusDot == null || textViewResponse == null || buttonCopyResponse == null) {
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
        boolean hasText = textViewResponse.getText().toString().trim().length() > 0;
        boolean isLoading = false;
        buttonCopyResponse.setEnabled(!isLoading && hasText);
        buttonCopyResponse.setVisibility(hasText ? View.VISIBLE : View.GONE);
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
        textViewResponse = findViewById(R.id.textViewResponse);
        buttonNotes = findViewById(R.id.buttonNotes);
        buttonCopyResponse = findViewById(R.id.buttonCopyResponse);
        responseCard = findViewById(R.id.responseCard);
        ImageButton buttonClearInput = findViewById(R.id.buttonClearInput);
        buttonClearInput.setOnClickListener(v -> editTextPrompt.setText(""));

        buttonSend.setOnClickListener(v -> {
            hideKeyboardAndClearFocus();
            String prompt = editTextPrompt.getText().toString().trim();
            if (prompt.isEmpty()) {
                Toast.makeText(MainActivity.this, "Enter a question", Toast.LENGTH_SHORT).show();
                return;
            }
            callOpenRouterWithNotes(prompt);
        });
        buttonNotes.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, NotesActivity.class);
            startActivity(intent);
        });
        buttonCopyResponse.setOnClickListener(v -> {
            String text = textViewResponse.getText().toString().trim();
            if (!text.isEmpty()) {
                android.content.ClipboardManager clipboard =
                        (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                android.content.ClipData clip =
                        android.content.ClipData.newPlainText("response", text);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(MainActivity.this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            }
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

    // Ask for user consent before AI/system modifies note content,
    // then apply the change only if approved.
    private void requestAiAddOrUpdateNoteWithConsent(String title, String content, String sourceTag) {
        runOnUiThread(() -> {
            List<Note> notes = NotesStorage.loadNotes(MainActivity.this);
            if (notes == null) notes = new java.util.ArrayList<>();

            Note target = notes.stream().filter(n -> n.getTitle() != null && n.getTitle().equalsIgnoreCase(title)).findFirst().orElse(null);

            String oldTitle = title;
            String oldContent = (target != null && target.getContent() != null) ? target.getContent() : "";
            String newTitle = title;
            String newContent = content;

            List<Note> finalNotes = notes;
            ContentChangeGuard.confirmContentChange(
                    MainActivity.this,
                    oldTitle,
                    oldContent,
                    newTitle,
                    newContent,
                    sourceTag,
                    approved -> {
                        if (approved) {
                            boolean created = false;
                            if (target != null) {
                                target.setContent(newContent);
                            } else {
                                int[] noteColors = getResources().getIntArray(R.array.note_colors);
                                int randomColor = noteColors[new Random().nextInt(noteColors.length)];
                                finalNotes.add(new Note(newTitle, newContent, randomColor));
                                created = true;
                            }
                            NotesStorage.saveNotes(MainActivity.this, finalNotes);

                            String msg = created ? "Created note '" + newTitle + "'." : "Updated note '" + newTitle + "'.";
                            Object prevTag = textViewResponse.getTag();
                            String prev = prevTag instanceof String ? (String) prevTag : textViewResponse.getText().toString();
                            String combined = (prev == null || prev.trim().isEmpty()) ? msg : (prev + "\n" + msg);
                            textViewResponse.setTag(combined);
                            renderMarkdownToTextView(combined);
                            Toast.makeText(MainActivity.this, "Applied change: " + newTitle, Toast.LENGTH_SHORT).show();
                        } else {
                            String msg = "Declined change for '" + newTitle + "'.";
                            Object prevTag = textViewResponse.getTag();
                            String prev = prevTag instanceof String ? (String) prevTag : textViewResponse.getText().toString();
                            String combined = (prev == null || prev.trim().isEmpty()) ? msg : (prev + "\n" + msg);
                            textViewResponse.setTag(combined);
                            renderMarkdownToTextView(combined);
                        }
                    }
            );
        });
    }

    private void setLoading(boolean loading) {
        runOnUiThread(() -> {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            buttonSend.setEnabled(!loading);
            if (buttonNotes != null) buttonNotes.setEnabled(!loading);
            boolean hasText = textViewResponse.getText().toString().trim().length() > 0;
            buttonCopyResponse.setEnabled(!loading && hasText);
            if (loading) {
                startResponseCardAnimation();
            } else {
                stopResponseCardAnimation();
            }
        });
    }

    private void startResponseCardAnimation() {
        if (responseCard == null) return;
        if (loadingAnimation == null) {
            loadingAnimation = new AlphaAnimation(0.3f, 1.0f);
            loadingAnimation.setDuration(800);
            loadingAnimation.setRepeatMode(Animation.REVERSE);
            loadingAnimation.setRepeatCount(Animation.INFINITE);
        }
        responseCard.startAnimation(loadingAnimation);
    }

    private void stopResponseCardAnimation() {
        if (responseCard == null) return;
        responseCard.clearAnimation();
        responseCard.setAlpha(1.0f);
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
                        "You are a helpful assistant. The user has these personal notes. " +
                                "Use them as context where relevant:\n\n" + notes + "\n\n" + contextSnippet);
                messagesArray.put(systemMsg);
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
                    textViewResponse.setTag(msg);
                    renderMarkdownToTextView(msg);
                    buttonCopyResponse.setVisibility(
                            msg.trim().isEmpty() ? View.GONE : View.VISIBLE
                    );
                    setLoading(false);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    final String err = "❌ Error:\n" + responseBody;
                    runOnUiThread(() -> {
                        textViewResponse.setText(err);
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
                                runOnUiThread(() -> {
                                    String preface = "AI requested note changes.";
                                    Object prevTag = textViewResponse.getTag();
                                    String prev = prevTag instanceof String ? (String) prevTag : textViewResponse.getText().toString();
                                    String combined = (prev == null || prev.trim().isEmpty()) ? preface : (prev + "\n\n" + preface);
                                    textViewResponse.setTag(combined);
                                    renderMarkdownToTextView(combined);
                                });
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
                                                requestAiAddOrUpdateNoteWithConsent(title, content, "AI Assistant");
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
                                    textViewResponse.setTag(finalReply);
                                    renderMarkdownToTextView(finalReply);
                                    if (finalReply.trim().isEmpty()) {
                                        buttonCopyResponse.setVisibility(View.GONE);
                                    } else {
                                        buttonCopyResponse.setVisibility(View.VISIBLE);
                                    }
                                });
                            }
                        }
                    }
                } catch (JSONException e) {
                    final String errorReply = "Response parse failed:\n" + responseBody;
                    runOnUiThread(() -> {
                        textViewResponse.setText(errorReply);
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

    private String markdownToHtmlString(String md) {
        if (md == null) return "";
        String s = md.replace("\r\n", "\n").replace("\r", "\n");
        s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        Pattern linkPattern = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");
        Matcher mlink = linkPattern.matcher(s);
        StringBuffer sbLinks = new StringBuffer();
        while (mlink.find()) {
            String text = mlink.group(1);
            String url = mlink.group(2);
            String repl = "<a href=\"" + url + "\">" + text + "</a>";
            mlink.appendReplacement(sbLinks, repl);
        }
        mlink.appendTail(sbLinks);
        s = sbLinks.toString();
        for (int i = 6; i >= 1; i--) {
            String hashes = new String(new char[i]).replace("\0", "#");
            s = s.replaceAll("(?m)^" + Pattern.quote(hashes) + "\\s*(.+)$", "<h" + i + ">$1</h" + i + ">");
        }
        String[] lines = s.split("\n");
        StringBuilder out = new StringBuilder();
        boolean inList = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                if (!inList) {
                    inList = true;
                    out.append("<ul>");
                }
                String item = trimmed.substring(2).trim();
                out.append("<li>").append(item).append("</li>");
            } else {
                if (inList) {
                    out.append("</ul>");
                    inList = false;
                }
                if (trimmed.isEmpty()) {
                    out.append("\n\n");
                } else {
                    out.append(line).append("\n");
                }
            }
        }
        if (inList) out.append("</ul>");
        s = out.toString();
        s = s.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        s = s.replaceAll("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)", "<i>$1</i>");
        String[] paras = s.split("\n{2,}");
        StringBuilder html = new StringBuilder();
        for (String p : paras) {
            String trimmed = p.trim();
            if (trimmed.startsWith("<h") || trimmed.startsWith("<ul") || trimmed.isEmpty()) {
                html.append(trimmed).append("\n\n");
            } else {
                html.append("<p>").append(trimmed).append("</p>\n\n");
            }
        }
        return html.toString().trim();
    }

    private void renderMarkdownToTextView(String rawMarkdown) {
        String html = markdownToHtmlString(rawMarkdown);
        Spanned sp;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            sp = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        } else {
            sp = Html.fromHtml(html);
        }
        textViewResponse.setText(sp);
        textViewResponse.setMovementMethod(LinkMovementMethod.getInstance());
        textViewResponse.setLineSpacing(6f, 1.05f);
    }
}
