package com.casualabsent.riko;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.content.SharedPreferences;
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

public final class MainActivity extends Activity {
    private static final String PREFS_NAME = "riko_settings";
    private static final String PREF_THEME = "selected_theme";

    private static final String[] THEME_KEYS = {"light", "dark", "red", "green"};
    private static final String[] THEME_LABELS = {"Light", "Dark", "Red", "Green"};

    private Palette palette;
    private String selectedTheme;

    private LinearLayout messageList;
    private ScrollView conversationScroll;
    private EditText messageInput;
    private TextView previewStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
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

        addMessage(
                "RIKO · ANDROID FOUNDATION",
                "The Android foundation is running. This build is a local interface preview: AI replies, voice input/output, conversation memory and the 3D avatar are not connected yet.",
                false);
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

    private void showThemePicker() {
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
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                            .edit()
                            .putString(PREF_THEME, selectedTheme)
                            .apply();
                    dialog.dismiss();
                    recreate();
                })
                .setNegativeButton("Close", null)
                .show();
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

        TextView themeButton = makeText(selectedTheme.toUpperCase() + "  ▾", 10, palette.accent);
        themeButton.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        themeButton.setGravity(Gravity.CENTER);
        themeButton.setPadding(dp(10), dp(9), dp(10), dp(9));
        themeButton.setBackground(shape(palette.surface, dp(20), palette.border, dp(1)));
        themeButton.setContentDescription("Choose appearance theme. Current theme: " + selectedTheme);
        themeButton.setFocusable(true);
        themeButton.setClickable(true);
        themeButton.setOnClickListener(view -> showThemePicker());
        header.addView(themeButton);

        return header;
    }

    private View createComposer() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(12), dp(18), dp(14));
        panel.setBackground(shape(palette.surface, dp(18), palette.border, dp(1)));

        previewStatus = makeText(
                "LOCAL PREVIEW · NO AI REQUESTS ARE MADE",
                10,
                palette.muted);
        previewStatus.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.bottomMargin = dp(10);
        panel.addView(previewStatus, statusParams);

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

        TextView sendButton = makeText("Send", 14, palette.onAccent);
        sendButton.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        sendButton.setGravity(Gravity.CENTER);
        sendButton.setBackground(shape(palette.accent, dp(14), 0, 0));
        sendButton.setContentDescription("Send message");
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
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }

        addMessage("YOU", text, true);
        messageInput.setText("");
        previewStatus.setText("PREVIEW ONLY · THIS MESSAGE WAS NOT SENT TO AN AI SERVICE");

        Toast.makeText(
                this,
                "Preview only — no AI request was made.",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void addMessage(String label, String text, boolean fromUser) {
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

        conversationScroll.post(() ->
                conversationScroll.fullScroll(View.FOCUS_DOWN));
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
