package com.casualabsent.riko;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteException;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String PREFS_NAME = "riko_settings";
    private static final String PREF_THEME = "selected_theme";
    private static final String PREF_ENDPOINT = "api_endpoint";
    private static final String PREF_MODEL = "api_model";
    private static final String PREF_SYSTEM_PROMPT = "system_prompt";

    private static final String DEFAULT_ENDPOINT =
            "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4.1-mini";
    // Preserved from the original desktop project's character_config.yaml.
    private static final String DEFAULT_SYSTEM_PROMPT =
            "You are a helpful assistant named Riko.\n"
            + "You speak like a snarky anime girl.\n"
            + "Always refer to the user as \"senpai\".";

    private static final String[] THEME_KEYS = {"light", "dark", "red", "green"};
    private static final String[] THEME_LABELS = {"Light", "Dark", "Red", "Green"};

    private Palette palette;
    private String selectedTheme;

    private LinearLayout messageList;
    private ScrollView conversationScroll;
    private EditText messageInput;
    private TextView connectionStatus;
    private TextView sendButton;

    private SharedPreferences preferences;
    private SecureCredentialStore credentialStore;
    private ChatDatabase chatDatabase;
    private ExecutorService networkExecutor;
    private boolean requestInFlight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        credentialStore = new SecureCredentialStore(this);
        chatDatabase = new ChatDatabase(this);
        networkExecutor = Executors.newSingleThreadExecutor();

        selectedTheme = preferences.getString(PREF_THEME, "dark");
        palette = paletteFor(selectedTheme);

        Window window = getWindow();
        window.setStatusBarColor(palette.background);
        window.setNavigationBarColor(palette.background);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        int systemUiVisibility = palette.lightSystemBars
                ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                : 0;
        window.getDecorView().setSystemUiVisibility(systemUiVisibility);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);
        installWindowInsets(root);

        root.addView(createHeader(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        conversationScroll = new ScrollView(this);
        conversationScroll.setFillViewport(true);
        conversationScroll.setClipToPadding(false);
        conversationScroll.setVerticalScrollBarEnabled(false);

        messageList = new LinearLayout(this);
        messageList.setOrientation(LinearLayout.VERTICAL);
        messageList.setPadding(dp(20), dp(22), dp(20), dp(22));
        conversationScroll.addView(messageList, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(conversationScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(createComposer(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        restoreConversation();
        updateConnectionStatus();
    }

    private Palette paletteFor(String theme) {
        if ("light".equalsIgnoreCase(theme)) {
            return new Palette(
                    Color.rgb(245, 243, 249),
                    Color.rgb(255, 255, 255),
                    Color.rgb(232, 224, 249),
                    Color.rgb(38, 34, 48),
                    Color.rgb(112, 106, 126),
                    Color.rgb(117, 82, 199),
                    Color.rgb(222, 217, 233),
                    Color.WHITE,
                    true);
        }
        if ("red".equalsIgnoreCase(theme)) {
            return new Palette(
                    Color.rgb(22, 15, 18),
                    Color.rgb(37, 23, 29),
                    Color.rgb(72, 33, 43),
                    Color.rgb(255, 240, 242),
                    Color.rgb(198, 165, 173),
                    Color.rgb(255, 116, 140),
                    Color.rgb(73, 48, 58),
                    Color.rgb(36, 16, 22),
                    false);
        }
        if ("green".equalsIgnoreCase(theme)) {
            return new Palette(
                    Color.rgb(13, 22, 18),
                    Color.rgb(24, 38, 31),
                    Color.rgb(33, 67, 51),
                    Color.rgb(239, 250, 242),
                    Color.rgb(165, 189, 175),
                    Color.rgb(131, 215, 167),
                    Color.rgb(48, 71, 58),
                    Color.rgb(16, 32, 23),
                    false);
        }
        return new Palette(
                Color.rgb(13, 15, 22),
                Color.rgb(24, 28, 39),
                Color.rgb(49, 40, 74),
                Color.rgb(242, 239, 250),
                Color.rgb(164, 161, 180),
                Color.rgb(187, 165, 255),
                Color.rgb(48, 51, 66),
                Color.rgb(21, 18, 31),
                false);
    }

    private void restoreConversation() {
        List<ChatMessage> history = chatDatabase.loadRecentMessages();
        if (history.isEmpty()) {
            addMessage(
                    "RIKO · ANDROID",
                    "I'm ready when you are. Open API settings to add your compatible endpoint, model and API key. Your key is encrypted with Android Keystore.",
                    false);
            return;
        }

        for (ChatMessage message : history) {
            addMessage(
                    "user".equals(message.role) ? "YOU" : "RIKO",
                    message.content,
                    "user".equals(message.role));
        }
        scrollToBottom();
    }

    private View createHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(18), dp(20), dp(16));

        TextView logo = new TextView(this);
        logo.setText("R");
        logo.setTextColor(palette.onAccent);
        logo.setTextSize(22);
        logo.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(shape(palette.accent, dp(16), 0, 0));
        header.addView(logo, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout titleGroup = new LinearLayout(this);
        titleGroup.setOrientation(LinearLayout.VERTICAL);
        titleGroup.setPadding(dp(12), 0, 0, 0);

        TextView title = makeText("RIKO", 19, palette.text);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleGroup.addView(title);

        TextView subtitle = makeText("ANDROID COMPANION", 10, palette.muted);
        subtitle.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(3);
        titleGroup.addView(subtitle, subtitleParams);

        header.addView(titleGroup, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView settingsButton = makeHeaderButton("API", "Configure AI endpoint and key");
        settingsButton.setOnClickListener(view -> showSettingsDialog());
        header.addView(settingsButton);

        TextView themeButton = makeHeaderButton(
                selectedTheme.toUpperCase() + "  ▾",
                "Choose appearance theme. Current theme: " + selectedTheme);
        LinearLayout.LayoutParams themeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        themeParams.leftMargin = dp(6);
        themeButton.setOnClickListener(view -> showThemePicker());
        header.addView(themeButton, themeParams);

        return header;
    }

    private TextView makeHeaderButton(String label, String description) {
        TextView button = makeText(label, 10, palette.accent);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(9), dp(9), dp(9), dp(9));
        button.setBackground(shape(palette.surface, dp(20), palette.border, dp(1)));
        button.setContentDescription(description);
        button.setFocusable(true);
        button.setClickable(true);
        return button;
    }

    private void showThemePicker() {
        if (requestInFlight) {
            Toast.makeText(this, "Wait for Riko's reply before changing appearance.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        int selectedIndex = 1;
        for (int i = 0; i < THEME_KEYS.length; i++) {
            if (THEME_KEYS[i].equalsIgnoreCase(selectedTheme)) {
                selectedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Appearance")
                .setSingleChoiceItems(THEME_LABELS, selectedIndex, (dialog, which) -> {
                    selectedTheme = THEME_KEYS[which];
                    preferences.edit().putString(PREF_THEME, selectedTheme).apply();
                    dialog.dismiss();
                    recreate();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showSettingsDialog() {
        if (requestInFlight) {
            Toast.makeText(this, "Wait for the current reply before changing API settings.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(8));
        scroll.addView(form);

        EditText endpointField = addSettingsField(
                form,
                "Chat Completions URL",
                preferences.getString(PREF_ENDPOINT, DEFAULT_ENDPOINT),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI,
                false);

        EditText modelField = addSettingsField(
                form,
                "Model",
                preferences.getString(PREF_MODEL, DEFAULT_MODEL),
                InputType.TYPE_CLASS_TEXT,
                false);

        EditText keyField = addSettingsField(
                form,
                "API key",
                "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                false);
        keyField.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        keyField.setHint(credentialStore.hasSecret()
                ? "Saved securely — leave blank to keep it"
                : "Paste your provider API key");

        EditText promptField = addSettingsField(
                form,
                "System prompt",
                preferences.getString(PREF_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT),
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                true);

        TextView securityNote = makeText(
                "The API key is encrypted with a key held by Android Keystore. "
                        + "Use HTTPS. Put the key only in the API key field, not in the URL.",
                12,
                palette.muted);
        securityNote.setPadding(0, dp(10), 0, dp(8));
        form.addView(securityNote);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("AI connection")
                .setView(scroll)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null);

        if (credentialStore.hasSecret()) {
            builder.setNeutralButton("Remove key", null);
        }

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String endpoint = endpointField.getText().toString().trim();
                String model = modelField.getText().toString().trim();
                String newKey = keyField.getText().toString().trim();
                String systemPrompt = promptField.getText().toString().trim();

                String validationError = validateEndpoint(endpoint);
                if (validationError != null) {
                    endpointField.setError(validationError);
                    return;
                }
                if (model.isEmpty()) {
                    modelField.setError("Enter a model name.");
                    return;
                }
                if (systemPrompt.isEmpty()) {
                    promptField.setError("The system prompt cannot be empty.");
                    return;
                }

                try {
                    if (!newKey.isEmpty()) {
                        credentialStore.saveSecret(newKey);
                    }
                    preferences.edit()
                            .putString(PREF_ENDPOINT, endpoint)
                            .putString(PREF_MODEL, model)
                            .putString(PREF_SYSTEM_PROMPT, systemPrompt)
                            .apply();

                    updateConnectionStatus();
                    dialog.dismiss();
                    Toast.makeText(this, credentialStore.hasSecret()
                                    ? "AI settings saved. Send a message to connect."
                                    : "Settings saved. Add an API key to send messages.",
                            Toast.LENGTH_LONG).show();
                } catch (GeneralSecurityException | IOException | IllegalArgumentException error) {
                    Toast.makeText(this, "Could not save the API key: " + safeUiError(error),
                            Toast.LENGTH_LONG).show();
                }
            });

            if (credentialStore.hasSecret()) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                    credentialStore.clearSecret();
                    updateConnectionStatus();
                    keyField.setText("");
                    keyField.setHint("Paste your provider API key");
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
                    Toast.makeText(this, "Saved API key removed from this device.",
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
        dialog.show();
    }

    private EditText addSettingsField(
            LinearLayout parent,
            String label,
            String value,
            int inputType,
            boolean multiline) {
        TextView caption = makeText(label, 12, palette.muted);
        caption.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        captionParams.topMargin = dp(10);
        captionParams.bottomMargin = dp(5);
        parent.addView(caption, captionParams);

        EditText field = new EditText(this);
        field.setText(value);
        field.setTextColor(palette.text);
        field.setHintTextColor(palette.muted);
        field.setTextSize(14);
        field.setSingleLine(!multiline);
        field.setMaxLines(multiline ? 5 : 1);
        field.setInputType(inputType);
        field.setPadding(dp(12), dp(10), dp(12), dp(10));
        field.setBackground(shape(palette.background, dp(12), palette.border, dp(1)));
        parent.addView(field, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                multiline ? dp(112) : dp(48)));
        return field;
    }

    private String validateEndpoint(String endpoint) {
        if (endpoint.isEmpty()) {
            return "Enter the full Chat Completions URL.";
        }
        try {
            URL url = new URL(endpoint);
            if (!"https".equalsIgnoreCase(url.getProtocol())
                    || url.getHost() == null
                    || url.getHost().trim().isEmpty()) {
                return "Use a valid HTTPS Chat Completions URL.";
            }
            if (url.getUserInfo() != null) {
                return "Do not put credentials in the URL. Use the API key field.";
            }
        } catch (MalformedURLException exception) {
            return "Enter a valid full URL.";
        }
        return null;
    }

    private View createComposer() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(12), dp(18), dp(14));
        panel.setBackground(shape(palette.surface, dp(18), palette.border, dp(1)));

        connectionStatus = makeText("", 10, palette.muted);
        connectionStatus.setLetterSpacing(0.04f);
        connectionStatus.setMaxLines(2);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.bottomMargin = dp(10);
        panel.addView(connectionStatus, statusParams);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        messageInput = new EditText(this);
        messageInput.setTextColor(palette.text);
        messageInput.setHintTextColor(palette.muted);
        messageInput.setHint("Message Riko…");
        messageInput.setTextSize(15);
        messageInput.setSingleLine(false);
        messageInput.setMaxLines(4);
        messageInput.setMinHeight(dp(52));
        messageInput.setPadding(dp(15), dp(12), dp(15), dp(12));
        messageInput.setBackground(shape(palette.background, dp(14), palette.border, dp(1)));
        messageInput.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        messageInput.setImeOptions(EditorInfo.IME_ACTION_SEND);

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                0, dp(54), 1f);
        row.addView(messageInput, inputParams);

        sendButton = makeText("Send", 14, palette.onAccent);
        sendButton.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        sendButton.setGravity(Gravity.CENTER);
        sendButton.setBackground(shape(palette.accent, dp(14), 0, 0));
        sendButton.setContentDescription("Send message to Riko");
        sendButton.setFocusable(true);
        sendButton.setClickable(true);
        sendButton.setOnClickListener(view -> sendMessage());

        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(
                dp(72), dp(54));
        sendParams.leftMargin = dp(9);
        row.addView(sendButton, sendParams);

        panel.addView(row);

        messageInput.setOnEditorActionListener((view, actionId, event) -> {
            boolean sendAction = actionId == EditorInfo.IME_ACTION_SEND;
            boolean enterPressed = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (sendAction || enterPressed) {
                sendMessage();
                return true;
            }
            return false;
        });

        return panel;
    }

    private void sendMessage() {
        if (requestInFlight) {
            return;
        }

        String userText = messageInput.getText().toString().trim();
        if (userText.isEmpty()) {
            return;
        }

        if (!credentialStore.hasSecret()) {
            updateConnectionStatus();
            showSettingsDialog();
            Toast.makeText(this, "Add an API key before sending a message.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final String apiKey;
        try {
            apiKey = credentialStore.getSecret();
        } catch (GeneralSecurityException | IOException | IllegalArgumentException error) {
            setStatus("SECURE KEY ERROR · REMOVE AND RE-ENTER YOUR API KEY IN SETTINGS");
            Toast.makeText(this, "Could not unlock the saved API key. Remove it and save it again.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        final String endpoint = preferences.getString(PREF_ENDPOINT, DEFAULT_ENDPOINT);
        final String model = preferences.getString(PREF_MODEL, DEFAULT_MODEL);
        final String systemPrompt = preferences.getString(
                PREF_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT);

        String endpointError = validateEndpoint(endpoint);
        if (endpointError != null) {
            setStatus("INVALID API SETTINGS · OPEN API");
            Toast.makeText(this, endpointError, Toast.LENGTH_LONG).show();
            return;
        }

        requestInFlight = true;
        setComposerBusy(true);
        setStatus("RIKO IS THINKING · " + model);

        TextView userBubble = addMessage("YOU", userText, true);
        TextView assistantBubble = addMessage("RIKO", "…", false);
        boolean[] receivedText = {false};
        scrollToBottom();

        networkExecutor.execute(() -> {
            try {
                List<ChatMessage> history = chatDatabase.loadRecentMessages();
                String reply = OpenAiCompatibleClient.streamReply(
                        endpoint,
                        model,
                        apiKey,
                        systemPrompt,
                        history,
                        userText,
                        piece -> runOnUiThread(() -> {
                            if (isFinishing() || assistantBubble.getParent() == null) {
                                return;
                            }
                            if (!receivedText[0]) {
                                assistantBubble.setText("");
                                receivedText[0] = true;
                            }
                            assistantBubble.append(piece);
                            scrollToBottom();
                        }));

                boolean historySaved = true;
                try {
                    chatDatabase.saveTurn(userText, reply);
                } catch (SQLiteException databaseError) {
                    historySaved = false;
                }

                boolean saved = historySaved;
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    assistantBubble.setText(reply);
                    requestInFlight = false;
                    setComposerBusy(false);
                    setStatus(saved
                            ? "READY · " + model + " · CHAT HISTORY SAVED ON THIS DEVICE"
                            : "REPLY RECEIVED · CHAT HISTORY COULD NOT BE SAVED");
                    scrollToBottom();
                });
            } catch (Exception error) {
                String message = safeUiError(error);
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    removeMessageBubble(userBubble);
                    removeMessageBubble(assistantBubble);
                    messageInput.setText(userText);
                    requestInFlight = false;
                    setComposerBusy(false);
                    setStatus("REQUEST FAILED · " + message);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    messageInput.requestFocus();
                });
            }
        });
    }

    private void setComposerBusy(boolean busy) {
        messageInput.setEnabled(!busy);
        sendButton.setEnabled(!busy);
        sendButton.setText(busy ? "Wait…" : "Send");
        sendButton.setAlpha(busy ? 0.65f : 1.0f);
    }

    private void updateConnectionStatus() {
        String model = preferences.getString(PREF_MODEL, DEFAULT_MODEL);
        String endpoint = preferences.getString(PREF_ENDPOINT, DEFAULT_ENDPOINT);
        if (!credentialStore.hasSecret()) {
            setStatus("NOT CONFIGURED · TAP API TO ADD A KEY");
        } else if (validateEndpoint(endpoint) != null) {
            setStatus("INVALID API ENDPOINT · TAP API TO FIX SETTINGS");
        } else {
            setStatus("CONFIGURED · " + model + " · SEND A MESSAGE TO CONNECT");
        }
    }

    private void setStatus(String text) {
        if (connectionStatus != null) {
            connectionStatus.setText(text);
        }
    }

    private String safeUiError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Unexpected error. Check the endpoint and try again.";
        }
        message = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (message.length() > 180) {
            message = message.substring(0, 180) + "…";
        }
        return message;
    }

    private TextView addMessage(String label, String text, boolean fromUser) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setGravity(fromUser ? Gravity.END : Gravity.START);

        TextView role = makeText(label, 10, fromUser ? palette.accent : palette.muted);
        role.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        role.setLetterSpacing(0.07f);
        group.addView(role);

        TextView body = makeText(text, 15, palette.text);
        body.setTextIsSelectable(true);
        body.setLineSpacing(dp(3), 1.0f);
        body.setPadding(dp(15), dp(12), dp(15), dp(12));
        body.setBackground(shape(
                fromUser ? palette.userBubble : palette.surface,
                dp(17),
                fromUser ? 0 : palette.border,
                fromUser ? 0 : dp(1)));
        body.setMaxWidth(getResources().getDisplayMetrics().widthPixels - dp(76));

        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(6);
        group.addView(body, bodyParams);

        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        groupParams.bottomMargin = dp(18);
        messageList.addView(group, groupParams);

        scrollToBottom();
        return body;
    }

    private void removeMessageBubble(TextView bubble) {
        if (bubble == null || !(bubble.getParent() instanceof LinearLayout)) {
            return;
        }
        LinearLayout group = (LinearLayout) bubble.getParent();
        if (group.getParent() == messageList) {
            messageList.removeView(group);
        }
    }

    private void scrollToBottom() {
        if (conversationScroll != null) {
            conversationScroll.post(() ->
                    conversationScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    private TextView makeText(String value, float sizeSp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable shape(
            int fillColor,
            float radiusPx,
            int strokeColor,
            float strokeWidthPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radiusPx);
        if (strokeWidthPx > 0) {
            drawable.setStroke(Math.max(1, Math.round(strokeWidthPx)), strokeColor);
        }
        return drawable;
    }

    private void installWindowInsets(View root) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left;
            int top;
            int right;
            int bottom;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets systemBars = insets.getInsets(
                        WindowInsets.Type.systemBars()
                                | WindowInsets.Type.displayCutout());
                Insets ime = insets.getInsets(WindowInsets.Type.ime());
                left = systemBars.left;
                top = systemBars.top;
                right = systemBars.right;
                bottom = Math.max(systemBars.bottom, ime.bottom);
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }

            view.setPadding(left, top, right, bottom);
            return insets;
        });
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (networkExecutor != null) {
            networkExecutor.shutdownNow();
        }
        super.onDestroy();
    }

    private static final class Palette {
        final int background;
        final int surface;
        final int userBubble;
        final int text;
        final int muted;
        final int accent;
        final int border;
        final int onAccent;
        final boolean lightSystemBars;

        Palette(
                int background,
                int surface,
                int userBubble,
                int text,
                int muted,
                int accent,
                int border,
                int onAccent,
                boolean lightSystemBars) {
            this.background = background;
            this.surface = surface;
            this.userBubble = userBubble;
            this.text = text;
            this.muted = muted;
            this.accent = accent;
            this.border = border;
            this.onAccent = onAccent;
            this.lightSystemBars = lightSystemBars;
        }
    }
}
