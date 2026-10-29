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
import android.webkit.WebView;
import android.webkit.WebSettings;

public class OverlayService extends Service {
    private WindowManager windowManager;
    private WebView webView;
    private static final String CHANNEL_ID = "izi_channel";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // Обязательное уведомление для работы Foreground Service (Android 8+)
        Notification notification = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("iZi гуляет")
                    .setContentText("Твой питомец сейчас на экране")
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .build();
        } else {
            notification = new Notification.Builder(this)
                    .setContentTitle("iZi гуляет")
                    .setContentText("Твой питомец сейчас на экране")
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .build();
        }
        
        // Запускаем сервис в фоновом режиме с уведомлением
        startForeground(1, notification);

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        webView = new WebView(this);
        webView.setBackgroundColor(0x00000000); // Прозрачный фон
        
        // Настройки WebView для загрузки локальных файлов и картинок
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setBlockNetworkImage(false);
        
        // Загружаем нашего персонажа
        webView.loadUrl("file:///android_asset/index.html");

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, 
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE 
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.BOTTOM | Gravity.START;
        params.x = 50;
        params.y = 100;

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
                    CHANNEL_ID,
                    "iZi Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
