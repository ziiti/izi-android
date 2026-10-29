package com.example.izi;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebChromeClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class OverlayService extends Service {
    private WindowManager windowManager;
    private WebView webView;
    private static final String CHANNEL_ID = "izi_channel";
    private static final String WORKER_URL = "https://wispy-pond-9c0b.zadeakber.workers.dev";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        Notification notification = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("iZi гуляет")
                    .setContentText("Твой питомец на экране")
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .build();
        } else {
            notification = new Notification.Builder(this)
                    .setContentTitle("iZi гуляет")
                    .setContentText("Твой питомец на экране")
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .build();
        }
        startForeground(1, notification);

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        webView = new WebView(this);
        webView.setBackgroundColor(0x00000000); 
        
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        
        webView.setWebChromeClient(new WebChromeClient());
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        // Подключаем мост для чата
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        // Загружаем файл из assets
        webView.loadUrl("file:///android_asset/index.html");

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        // Окно на весь экран, но пропускает нажатия сквозь себя (FLAG_NOT_TOUCHABLE)
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(webView, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (webView != null) windowManager.removeView(webView);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID, "iZi Service Channel", NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(serviceChannel);
        }
    }

    // --- МОСТ ДЛЯ ЧАТА ---
    public class AndroidBridge {
        @JavascriptInterface
        public void sendMessage(final String message) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String reply = "Ошибка связи.";
                    try {
                        URL url = new URL(WORKER_URL);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setDoOutput(true);
                        
                        String jsonInputString = "{\"message\": \"" + message + "\"}";
                        try(OutputStream os = conn.getOutputStream()) {
                            byte[] input = jsonInputString.getBytes("utf-8");
                            os.write(input, 0, input.length);
                        }

                        try(BufferedReader br = new BufferedReader(
                                new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                            StringBuilder response = new StringBuilder();
                            String responseLine = null;
                            while ((responseLine = br.readLine()) != null) {
                                response.append(responseLine.trim());
                            }
                            String respStr = response.toString();
                            int start = respStr.indexOf("\"reply\":\"") + 9;
                            int end = respStr.lastIndexOf("\"");
                            if (start > 8 && end > start) {
                                reply = respStr.substring(start, end);
                            }
                        }
                    } catch (Exception e) {
                        reply = "Ошибка сети: " + e.getMessage();
                    }

                    final String finalReply = reply;
                    webView.post(new Runnable() {
                        @Override
                        public void run() {
                            webView.evaluateJavascript("document.getElementById('cloud-text').innerText = '" + finalReply.replace("'", "\\'") + "';", null);
                        }
                    });
                }
            }).start();
        }
    }
}
