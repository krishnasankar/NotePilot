package com.example.myassistant;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
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
import android.os.Looper;
import android.text.TextUtils;
import android.content.Context;
import android.view.inputmethod.InputMethodManager;
import android.view.View;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest; // deprecated in older libs, the code below uses simplified getLastLocation
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import java.util.concurrent.TimeUnit;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

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

public class MainActivity extends AppCompatActivity {

    private static final String OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";

    private EditText editTextPrompt;
    private Button buttonSend;
    private ProgressBar progressBar;
    private TextView textViewResponse;
    private FloatingActionButton fabNotes;
    private ImageButton buttonCopyResponse;
    private ImageButton buttonSettings;
    private View keyStatusDot;
    private FusedLocationProviderClient fusedLocationClient;
    private String latestLocationText = "";

    private View responseCard;
    private OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)   // time to establish TCP connection
            .writeTimeout(20, TimeUnit.SECONDS)     // time to send request body
            .readTimeout(60, TimeUnit.SECONDS)      // time waiting for server to send response
            .callTimeout(90, TimeUnit.SECONDS)      // overall time for the call
            .retryOnConnectionFailure(true)
            .build();

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private Animation loadingAnimation;

    private String getApiKey() {
        String k = ApiKeyStore.getKey(this);
        return (k == null) ? "" : k;
    }


    private void showApiKeyDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("OpenRouter API Key");

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("sk-...");
        String existing = ApiKeyStore.getKey(this);
        if (existing != null && !existing.isEmpty()) {
            // show masked except last 4 characters
            String masked = existing.length() > 8
                    ? "****" + existing.substring(existing.length() - 8)
                    : "****";
            input.setText(masked);
        }

        // Put some padding in the dialog
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad/2, pad, pad/2);

        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String value = input.getText().toString().trim();
            if (value.isEmpty()) {
                Toast.makeText(MainActivity.this, "API key cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            // If user pasted masked form (starts with ****), assume they didn't change real key
            if (value.startsWith("****")) {
                Toast.makeText(MainActivity.this, "No changes saved", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean ok = ApiKeyStore.saveKey(MainActivity.this, value);
            if (ok) {
                Toast.makeText(MainActivity.this, "API key saved", Toast.LENGTH_SHORT).show();
                updateKeyStatus();
            } else {
                Toast.makeText(MainActivity.this, "Failed to save API key", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNeutralButton("Clear", (dialog, which) -> {
            boolean ok = ApiKeyStore.clearKey(MainActivity.this);
            if (ok) {
                Toast.makeText(MainActivity.this, "API key cleared", Toast.LENGTH_SHORT).show();
                updateKeyStatus();
            } else {
                Toast.makeText(MainActivity.this, "Failed to clear API key", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        android.app.AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateKeyStatus();
    }

    private void updateKeyStatus() {
        // If views are not yet initialized, do nothing (prevents NPE)
        if (buttonSettings == null || keyStatusDot == null || textViewResponse == null || buttonCopyResponse == null) {
            return;
        }

        String apiKey = ApiKeyStore.getKey(this);
        boolean hasKey = apiKey != null && !apiKey.trim().isEmpty();

        // Dim or brighten the settings icon
        buttonSettings.setAlpha(hasKey ? 1.0f : 0.6f);

        // Show the dot and tint it green if key exists, red otherwise
        keyStatusDot.setVisibility(View.VISIBLE);
        int color = hasKey
                ? ContextCompat.getColor(this, R.color.key_present_green)
                : ContextCompat.getColor(this, R.color.key_missing_red);
        keyStatusDot.setBackgroundTintList(ColorStateList.valueOf(color));

        // Enable/disable copy button based on presence of text and not loading
        boolean hasText = textViewResponse.getText().toString().trim().length() > 0;
        boolean isLoading = false; // if you track a loading flag, use it here; otherwise rely on view states
        buttonCopyResponse.setEnabled(!isLoading && hasText);
        buttonCopyResponse.setVisibility(hasText ? View.VISIBLE : View.GONE);
    }

    // Activity Result API launcher to request location permission(s)
    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fineGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarseGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                if (Boolean.TRUE.equals(fineGranted) || Boolean.TRUE.equals(coarseGranted)) {
                    fetchLastLocationOnce(); // permission granted -> fetch location
                } else {
                    // permission denied — keep latestLocationText empty
                    latestLocationText = "";
                }
            });

    private void requestLocationPermissionsIfNeeded() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) {
            // ask for both (user will see a single prompt)
            locationPermissionLauncher.launch(new String[]{ Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION });
        } else {
            // we already have at least coarse/fine
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
                            // If cached last location is null, you may attempt a single fresh request,
                            // but to keep it simple we'll leave it empty or optionally request updates.
                            latestLocationText = "";
                        }
                    })
                    .addOnFailureListener(e -> {
                        latestLocationText = "";
                    });
        } catch (SecurityException e) {
            // not allowed
            latestLocationText = "";
        }
    }

    // Helper to format lat/lon + approximate accuracy
    private String formatLocationHumanReadable(Location location) {
        if (location == null) return "";
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        float accuracy = location.getAccuracy();
        long ts = location.getTime(); // epoch millis of fix, if available

        // Format time of fix
        String fixTime = "";
        if (ts > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getDefault());
            fixTime = sdf.format(new Date(ts));
        }

        // Build a short human-readable string (you can extend to reverse geocoding if you want)
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

        String tz = TimeZone.getDefault().getID(); // e.g., "Asia/Kolkata"
        String tzDisplay = TimeZone.getDefault().getDisplayName(false, TimeZone.SHORT, Locale.getDefault());

        String locale = Locale.getDefault().toLanguageTag(); // e.g., "en-IN"

        return String.format(Locale.getDefault(),
                "local_time=%s, timezone=%s (%s), locale=%s",
                localTime, tz, tzDisplay, locale);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // install the splash screen and optionally keep it until we are ready
        SplashScreen.installSplashScreen(this);
        setContentView(R.layout.activity_main);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        requestLocationPermissionsIfNeeded();
        buttonSettings = findViewById(R.id.buttonSettings);
        keyStatusDot = findViewById(R.id.keyStatusDot);
        editTextPrompt = findViewById(R.id.editTextPrompt);
        buttonSend = findViewById(R.id.buttonSend);
        progressBar = findViewById(R.id.progressBar);
        textViewResponse = findViewById(R.id.textViewResponse);
        fabNotes = findViewById(R.id.fabNotes);
        buttonCopyResponse = findViewById(R.id.buttonCopyResponse);
        responseCard = findViewById(R.id.responseCard);
        View root = findViewById(R.id.rootLayout);
        ImageButton buttonClearInput = findViewById(R.id.buttonClearInput);
        buttonClearInput.setOnClickListener(v -> editTextPrompt.setText(""));

        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
                // get status bar inset (top)
                int statusBarHeight = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top;

                // Add the status bar height plus a little extra spacing (e.g., 8 or 12 dp)
                final float scale = v.getResources().getDisplayMetrics().density;
                int extraDp = (int) (8 * scale); // 8dp of extra breathing room
                v.setPadding(v.getPaddingLeft(),
                        statusBarHeight + extraDp,
                        v.getPaddingRight(),
                        v.getPaddingBottom());

                // return the unconsumed insets
                return windowInsets;
            });

            // request insets to be applied immediately
            root.requestApplyInsets();
        }
        buttonSend.setOnClickListener(v -> {
            // Hide keyboard immediately when Ask is tapped
            hideKeyboardAndClearFocus();

            String prompt = editTextPrompt.getText().toString().trim();
            if (prompt.isEmpty()) {
                Toast.makeText(MainActivity.this, "Enter a question", Toast.LENGTH_SHORT).show();
                return;
            }

            callOpenRouterWithNotes(prompt);
        });


        // Open notes editor
        fabNotes.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, NotesActivity.class);
            startActivity(intent);
        });

        // Clear response
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

        // Optional: initially disable Ask if you want until user types something
        // buttonSend.setEnabled(false);
        // editTextPrompt.addTextChangedListener(new SimpleTextWatcher(() -> {
        //     buttonSend.setEnabled(editTextPrompt.getText().toString().trim().length() > 0);
        // }));
    }

    private void setLoading(boolean loading) {
        runOnUiThread(() -> {

            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);

            buttonSend.setEnabled(!loading);
            fabNotes.setEnabled(!loading);

// Only enable copy button if there's text AND we're not loading
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
            loadingAnimation.setDuration(800);           // 0.8s fade
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
        String locationInfo = latestLocationText; // may be empty if no permission or no fix

        String contextSnippet = "Device context: " + dateTimeInfo;
        if (locationInfo != null && !locationInfo.isEmpty()) {
            contextSnippet += "\nLocation (approx): " + locationInfo;
        }

        // Load notes from file each time (so changes are always picked up)
        String notes = NotesStorage.loadNotes(this);
        if (notes == null) notes = "";
        int maxNotesChars = 4000; // avoid overly huge prompts
        if (notes.length() > maxNotesChars) {
            notes = notes.substring(notes.length() - maxNotesChars);
        }

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", "arcee-ai/trinity-mini:free");
            jsonBody.put("temperature", 1.0);
            jsonBody.put("max_tokens", 4096);

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

                // Update UI and then stop loading (animation stays until text is visible)
                runOnUiThread(() -> {
//                    textViewResponse.setText(msg);
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
                        setLoading(false); // stop animation after updating UI
                    });
                    return;
                }

                // Parse response on background thread
                String reply = "";
                try {
                    JSONObject json = new JSONObject(responseBody);
                    JSONArray choices = json.optJSONArray("choices");
                    if (choices != null && choices.length() > 0) {
                        JSONObject choice = choices.getJSONObject(0);
                        JSONObject message = choice.optJSONObject("message");
                        if (message != null) {
                            reply = message.optString("content", "").trim();
                            if (reply.isEmpty() && message.has("reasoning")) {
                                reply = message.optString("reasoning", "").trim();
                            }
                            if (reply.isEmpty() && choice.has("reasoning")) {
                                reply = choice.optString("reasoning", "").trim();
                            }
                        }
                    }

                    if (reply.isEmpty()) {
                        reply = "[Model returned empty content.]";
                    }
                } catch (JSONException e) {
                    reply = "Response parse failed:\n" + responseBody;
                }

                final String finalReply = reply;

                // Update UI and then stop loading, guaranteeing animation stays until text is placed
                runOnUiThread(() -> {
//                    textViewResponse.setText(finalReply);
                    textViewResponse.setTag(finalReply);
                    renderMarkdownToTextView(finalReply);


                    // Show copy button only if there's actual text
                    if (finalReply.trim().isEmpty()) {
                        buttonCopyResponse.setVisibility(View.GONE);
                    } else {
                        buttonCopyResponse.setVisibility(View.VISIBLE);
                    }

                    setLoading(false);
                });

            }
        });

    }

    // Call this to hide the soft keyboard and clear focus from the current input
    private void hideKeyboardAndClearFocus() {
        View view = this.getCurrentFocus();
        if (view == null) {
            // create a dummy view to get a window token if nothing has focus
            view = new View(this);
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
        // Also clear focus from the prompt so keyboard won't re-open
        if (editTextPrompt != null) {
            editTextPrompt.clearFocus();
        }
    }

    /**
     * Convert a small subset of Markdown to HTML:
     * - Headings (# .. ######)
     * - Bold **text**
     * - Italic *text*
     * - Unordered lists: lines starting with "-" or "*"
     * - Simple links [text](url)
     *
     * NOTE: Lightweight and dependency-free.
     */
    private String markdownToHtmlString(String md) {
        if (md == null) return "";

        // Normalize line endings
        String s = md.replace("\r\n", "\n").replace("\r", "\n");

        // Escape basic HTML chars
        s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");

        // Links [text](url)
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

        // Headings (###### ... #)
        for (int i = 6; i >= 1; i--) {
            String hashes = new String(new char[i]).replace("\0", "#");
            s = s.replaceAll("(?m)^" + Pattern.quote(hashes) + "\\s*(.+)$", "<h" + i + ">$1</h" + i + ">");
        }

        // Unordered lists: wrap consecutive "- " or "* " lines into <ul>
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

        // Bold **text** and italic *text*
        s = s.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        s = s.replaceAll("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)", "<i>$1</i>");

        // Paragraph blocks: split on 2+ newlines
        String[] paras = s.split("\\n{2,}");
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
