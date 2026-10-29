package com.example.izi;

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

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        webView = new WebView(this);
        webView.setBackgroundColor(0x00000000); // Прозрачный фон
        
        // Включаем все нужные настройки для загрузки локальных файлов и картинок
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        
        // Разрешаем загрузку картинок (для наших SVG)
        settings.setBlockNetworkImage(false);
        
        // Загружаем наш index.html
        webView.loadUrl("file:///android_asset/index.html");

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        // Размер окна (WRAP_CONTENT, чтобы подстроилось под экран)
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
}
