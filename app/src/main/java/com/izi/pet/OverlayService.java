package com.izi.pet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OverlayService extends Service {
    private static final String CHANNEL_ID = "izi_pet_overlay";
    private static final int NOTIFICATION_ID = 4101;
    private static final String WORKER_URL =
            "https://wispy-pond-9c0b.zadeakber.workers.dev";

    private WindowManager windowManager;
    private WebView petWebView;
    private LinearLayout controlRoot;
    private LinearLayout chatPanel;
    private TextView chatText;
    private EditText messageInput;
    private WindowManager.LayoutParams petParams;
    private WindowManager.LayoutParams controlParams;
    private Handler mainHandler;
    private ExecutorService networkExecutor;
    private boolean chatOpen = false;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        networkExecutor = Executors.newSingleThreadExecutor();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        createPetWindow();
        createControlsWindow();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "iZi Pet", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Уведомление работающего питомца iZi");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder.setContentTitle("iZi рядом")
                .setContentText("Виртуальный питомец работает")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    // Full-screen character layer: it never consumes touches.
    private void createPetWindow() {
        petWebView = new WebView(this);
        petWebView.setBackgroundColor(Color.TRANSPARENT);
        petWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings settings = petWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkLoads(false);

        petWebView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        petParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        petParams.gravity = Gravity.TOP | Gravity.START;
        petParams.setTitle("iZi character");

        windowManager.addView(petWebView, petParams);
        petWebView.loadUrl("file:///android_asset/index.html");
    }

    // Small, separate, touchable native window. It can receive keyboard focus.
    private void createControlsWindow() {
        controlRoot = new LinearLayout(this);
        controlRoot.setOrientation(LinearLayout.VERTICAL);
        controlRoot.setPadding(dp(8), dp(7), dp(8), dp(7));
        controlRoot.setBackground(rounded(0xEE25253A, 18));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        Button chat = makeButton("💬");
        Button mic = makeButton("🎤");
        Button food = makeButton("🍎");
        Button play = makeButton("⚽");
        Button sleep = makeButton("💤");

        chat.setOnClickListener(v -> toggleChat());
        mic.setOnClickListener(v -> {
            openChat(false);
            setChatText("Слушаю... 🎤 Пока можно написать мне сообщение.");
            showPetMessage("Слушаю... 🎤");
        });
        food.setOnClickListener(v -> petAction("feed"));
        play.setOnClickListener(v -> petAction("play"));
        sleep.setOnClickListener(v -> petAction("sleep"));

        buttons.addView(chat, weightedButtonParams());
        buttons.addView(mic, weightedButtonParams());
        buttons.addView(food, weightedButtonParams());
        buttons.addView(play, weightedButtonParams());
        buttons.addView(sleep, weightedButtonParams());
        controlRoot.addView(buttons);

        chatPanel = new LinearLayout(this);
        chatPanel.setOrientation(LinearLayout.VERTICAL);
        chatPanel.setPadding(dp(8), dp(8), dp(8), dp(4));
        chatPanel.setVisibility(View.GONE);

        chatText = new TextView(this);
        chatText.setText("Привет! Я iZi 🐾");
        chatText.setTextColor(Color.WHITE);
        chatText.setTextSize(14);
        chatText.setPadding(dp(4), dp(4), dp(4), dp(8));
        chatPanel.addView(chatText);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);

        messageInput = new EditText(this);
        messageInput.setSingleLine(true);
        messageInput.setTextColor(Color.WHITE);
        messageInput.setHintTextColor(0xFFBBBBCC);
        messageInput.setHint("Напиши iZi...");
        messageInput.setTextSize(14);
        messageInput.setPadding(dp(10), 0, dp(10), 0);
        messageInput.setBackground(rounded(0xFF45455C, 10));
        inputRow.addView(messageInput, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button send = makeButton("➤");
        send.setOnClickListener(v -> sendCurrentMessage());
        LinearLayout.LayoutParams sendLp =
                new LinearLayout.LayoutParams(dp(58), dp(48));
        sendLp.leftMargin = dp(6);
        inputRow.addView(send, sendLp);
        chatPanel.addView(inputRow);

        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            sendCurrentMessage();
            return true;
        });

        controlRoot.addView(chatPanel);

        controlParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        controlParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        controlParams.y = dp(18);
        controlParams.softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
        controlParams.setTitle("iZi controls");

        windowManager.addView(controlRoot, controlParams);
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(0, dp(48), 1f);
        lp.leftMargin = dp(2);
        lp.rightMargin = dp(2);
        return lp;
    }

    private Button makeButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(18);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setBackground(rounded(0xFF555572, 13));
        return b;
    }

    private void toggleChat() {
        if (chatOpen) closeChat();
        else openChat(true);
    }

    private void openChat(boolean showKeyboard) {
        if (chatPanel == null || controlRoot == null) return;
        chatOpen = true;
        chatPanel.setVisibility(View.VISIBLE);
        controlParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        controlParams.softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        windowManager.updateViewLayout(controlRoot, controlParams);
        if (showKeyboard) {
            messageInput.requestFocus();
            messageInput.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager)
                        getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(
                        messageInput, InputMethodManager.SHOW_IMPLICIT);
            }, 250);
        }
    }

    private void closeChat() {
        if (chatPanel == null || controlRoot == null) return;
        chatOpen = false;
        InputMethodManager imm = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && messageInput != null) {
            imm.hideSoftInputFromWindow(messageInput.getWindowToken(), 0);
        }
        chatPanel.setVisibility(View.GONE);
        controlParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        controlParams.softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
        windowManager.updateViewLayout(controlRoot, controlParams);
    }

    private void setChatText(String text) {
        if (chatText != null) chatText.setText(text);
    }

    private void sendCurrentMessage() {
        if (messageInput == null) return;
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;
        messageInput.setText("");
        setChatText("iZi думает... 💭");
        // Invoke the requested JavaScript interface. JS calls AndroidBridge.sendMessage().
        if (petWebView != null) {
            String quoted = JSONObject.quote(text);
            petWebView.post(() -> petWebView.evaluateJavascript(
                    "window.AndroidBridge.sendMessage(" + quoted + ");", null));
        } else {
            sendMessageToWorker(text);
        }
    }

    private void petAction(String action) {
        if (petWebView == null) return;
        String quoted = JSONObject.quote(action);
        petWebView.post(() -> petWebView.evaluateJavascript(
                "window.petAction && window.petAction(" + quoted + ");", null));
    }

    private void showPetMessage(String message) {
        if (petWebView == null) return;
        String quoted = JSONObject.quote(message);
        petWebView.post(() -> petWebView.evaluateJavascript(
                "window.showPetMessage && window.showPetMessage(" + quoted + ");", null));
    }

    public final class AndroidBridge {
        @JavascriptInterface
        public void sendMessage(String text) {
            if (text == null || text.trim().isEmpty()) return;
            sendMessageToWorker(text.trim());
        }

        @JavascriptInterface
        public void action(String action) {
            if (action == null) return;
            mainHandler.post(() -> petAction(action));
        }
    }

    private void sendMessageToWorker(String message) {
        mainHandler.post(() -> setChatText("iZi думает... 💭"));
        networkExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(WORKER_URL).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(25000);
                connection.setDoOutput(true);
                connection.setRequestProperty(
                        "Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");

                JSONObject body = new JSONObject();
                // The supplied existing HTML used {"message": "..."}.
                body.put("message", message);
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(bytes);
                }

                int status = connection.getResponseCode();
                InputStream stream = (status >= 200 && status < 300)
                        ? connection.getInputStream() : connection.getErrorStream();
                StringBuilder response = new StringBuilder();
                if (stream != null) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) response.append(line);
                    }
                }
                if (status < 200 || status >= 300) {
                    throw new IllegalStateException(
                            "HTTP " + status + (response.length() > 0 ? ": " + response : ""));
                }

                JSONObject result = new JSONObject(response.toString());
                String reply = result.optString("reply", "Я получил сообщение, но ответа нет 🐾");
                deliverReply(reply);
            } catch (Exception e) {
                String messageText = "Не удалось связаться с сервером: " +
                        (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                deliverReply(messageText);
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void deliverReply(String reply) {
        mainHandler.post(() -> {
            setChatText(reply);
            if (petWebView != null) {
                String quoted = JSONObject.quote(reply);
                petWebView.evaluateJavascript(
                        "window.onServerReply && window.onServerReply(" + quoted + ");",
                        null);
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (networkExecutor != null) networkExecutor.shutdownNow();

        if (windowManager != null) {
            if (controlRoot != null) {
                try { windowManager.removeViewImmediate(controlRoot); }
                catch (Exception ignored) {}
                controlRoot = null;
            }
            if (petWebView != null) {
                try { windowManager.removeViewImmediate(petWebView); }
                catch (Exception ignored) {}
                petWebView.stopLoading();
                petWebView.removeJavascriptInterface("AndroidBridge");
                petWebView.destroy();
                petWebView = null;
            }
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
