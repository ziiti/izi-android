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
    private static final String WORKER_URL = "https://wispy-pond-9c0b.zadeakber.workers.dev";

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

    private void createPetWindow() {
        petWebView = new WebView(this);
        petWebView.setBackgroundColor(Color.TRANSPARENT);
        petWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings settings = petWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        petWebView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        String htmlCode = "<!doctype html><html lang='ru'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'><title>iZi Pet</title><style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:transparent}body{position:relative}#stage{position:absolute;inset:0;overflow:hidden;pointer-events:none}#pet{position:absolute;width:min(52vw,220px);height:auto;aspect-ratio:400/520;bottom:10px;left:0;transform-origin:50% 100%;will-change:left,transform}#pet svg{width:100%;height:100%;overflow:visible;filter:drop-shadow(0 7px 7px rgba(0,0,0,.16))}#bubble{position:absolute;left:50%;bottom:calc(100% + 8px);transform:translateX(-50%);max-width:220px;min-width:90px;padding:8px 12px;border-radius:15px;background:rgba(255,255,255,.96);color:#263238;font:13px/1.3 sans-serif;text-align:center;box-shadow:0 3px 12px #0003;display:none;overflow-wrap:anywhere}#pet.walking #leg-left{animation:legL .42s infinite alternate ease-in-out;transform-box:view-box;transform-origin:176px 408px}#pet.walking #leg-right{animation:legR .42s infinite alternate ease-in-out;transform-box:view-box;transform-origin:224px 408px}#pet.walking #arm-left{animation:armL .42s infinite alternate ease-in-out;transform-box:view-box;transform-origin:142px 296px}#pet.walking #arm-right{animation:armR .42s infinite alternate ease-in-out;transform-box:view-box;transform-origin:258px 296px}@keyframes legL{to{transform:rotate(13deg)}}@keyframes legR{to{transform:rotate(-13deg)}}@keyframes armL{to{transform:rotate(-9deg)}}@keyframes armR{to{transform:rotate(9deg)}}#pet.jumping{animation:jump .65s ease-in-out}@keyframes jump{0%,100%{translate:0 0}45%{translate:0 -75px}}#pet.sleeping{animation:breathe 2.2s infinite ease-in-out}@keyframes breathe{50%{translate:0 3px}}#zzz{display:none;position:absolute;right:3%;top:0;color:white;font:bold 18px sans-serif;text-shadow:0 1px 4px #333}#pet.sleeping #zzz{display:block}</style></head><body><div id='stage'><div id='pet'><div id='bubble'></div><div id='zzz'>Z z z</div><svg id='character' viewBox='0 0 400 520' xmlns='http://www.w3.org/2000/svg'></svg></div></div><script>";

        htmlCode += "const manifest = {canvas:[400,520],parts:[{id:'01_head',file:'01_head.svg',size:[180,160],pivot:[90,80],place:[200,170]},{id:'02_hair',file:'02_hair.svg',size:[70,56],pivot:[35,50],place:[200,96]},{id:'03_eye_left',file:'03_eye_left.svg',size:[60,68],pivot:[30,34],place:[162,168]},{id:'04_eye_right',file:'04_eye_right.svg',size:[60,68],pivot:[30,34],place:[238,168]},{id:'05_pupil_left',file:'05_pupil_left.svg',size:[30,30],pivot:[15,15],place:[165,172]},{id:'06_pupil_right',file:'06_pupil_right.svg',size:[30,30],pivot:[15,15],place:[235,172]},{id:'07_lid_left',file:'07_lid_left.svg',size:[60,68],pivot:[30,34],place:[162,168]},{id:'08_lid_right',file:'08_lid_right.svg',size:[60,68],pivot:[30,34],place:[238,168]},{id:'09_headphones',file:'09_headphones.svg',size:[224,120],pivot:[112,70],place:[200,160]},{id:'10_brow_left',file:'10_brow_left.svg',size:[56,22],pivot:[28,11],place:[162,128]},{id:'11_brow_right',file:'11_brow_right.svg',size:[56,22],pivot:[28,11],place:[238,128]},{id:'12_mouth_neutral',file:'12_mouth_neutral.svg',size:[64,44],pivot:[32,22],place:[200,208]},{id:'13_mouth_smile',file:'13_mouth_smile.svg',size:[64,44],pivot:[32,22],place:[200,208]},{id:'14_mouth_open',file:'14_mouth_open.svg',size:[64,44],pivot:[32,22],place:[200,208]},{id:'15_mouth_sad',file:'15_mouth_sad.svg',size:[64,44],pivot:[32,22],place:[200,208]},{id:'16_mouth_surprise',file:'16_mouth_surprise.svg',size:[64,44],pivot:[32,22],place:[200,208]},{id:'17_body',file:'17_body.svg',size:[150,190],pivot:[75,95],place:[200,330]},{id:'18_vest',file:'18_vest.svg',size:[150,190],pivot:[75,95],place:[200,330]},{id:'19_backpack',file:'19_backpack.svg',size:[190,150],pivot:[95,75],place:[200,345]},{id:'20_shoulder_left',file:'20_shoulder_left.svg',size:[34,62],pivot:[17,14],place:[142,296]},{id:'21_forearm_left',file:'21_forearm_left.svg',size:[32,62],pivot:[16,14],place:[142,332]},{id:'22_hand_left',file:'22_hand_left.svg',size:[44,48],pivot:[22,12],place:[142,366]},{id:'23_shoulder_right',file:'23_shoulder_right.svg',size:[34,62],pivot:[17,14],place:[258,296]},{id:'24_forearm_right',file:'24_forearm_right.svg',size:[32,62],pivot:[16,14],place:[258,332]},{id:'25_hand_right',file:'25_hand_right.svg',size:[44,48],pivot:[22,12],place:[258,366]},{id:'26_thigh_left',file:'26_thigh_left.svg',size:[36,56],pivot:[18,14],place:[176,408]},{id:'27_shin_left',file:'27_shin_left.svg',size:[34,56],pivot:[17,14],place:[176,444]},{id:'28_foot_left',file:'28_foot_left.svg',size:[60,36],pivot:[30,8],place:[176,480]},{id:'29_thigh_right',file:'29_thigh_right.svg',size:[36,56],pivot:[18,14],place:[224,408]},{id:'30_shin_right',file:'30_shin_right.svg',size:[34,56],pivot:[17,14],place:[224,444]},{id:'31_foot_right',file:'31_foot_right.svg',size:[60,36],pivot:[30,8],place:[224,480]}]};";

        htmlCode += "const base64Images = {"
        + "\"01_head.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxODAiIGhlaWdodD0iMTYwIiB2aWV3Qm94PSIwIDAgMTgwIDE2MCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIj48cGF0aCBkPSJNOTAgNiBDMTUwIDYgMTc0IDQwIDE3NCA4NCBDMTc0IDEyOCAxNDAgMTU0IDkwIDE1NCBDNDAgMTU0IDYgMTI4IDYgODQgQzYgNDAgMzAgNiA5MCA2IFoiIGZpbGw9IiMzRkMxQjAiIHN0cm9rZT0iIzFDMkIyQiIgc3Ryb2tlLXdpZHRoPSI2Ii8+PC9zdmc+\","
        + "\"02_hair.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI3MCIgaGVpZ2h0PSI1NiIgdmlld0JveD0iMCAwIDcwIDU2IiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxwYXRoIGQ9Ik0yMCA1MiBRMTAgMzIgMjIgMTIiIGZpbGw9Im5vbmUiIHN0cm9rZT0iIzFDMkIyQiIgc3Ryb2tlLXdpZHRoPSI2Ii8+PHBhdGggZD0iTTM1IDUyIFEzNCAyOCAzOCA1IiBmaWxsPSJub25lIiBzdHJva2U9IiMxQzJCMkIiIHN0cm9rZS13aWR0aD0iNiIvPjxwYXRoIGQ9Ik01MCA1MiBRNjAgMzQgNTQgMTQiIGZpbGw9Im5vbmUiIHN0cm9rZT0iIzFDMkIyQiIgc3Ryb2tlLXdpZHRoPSI2Ii8+PC9zdmc+\","
        + "\"03_eye_left.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI2MCIgaGVpZ2h0PSI2OCIgdmlld0JveD0iMCAwIDYwIDY4IiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxlbGxpcHNlIGN4PSIzMCIgY3k9IjM0IiByeD0iMjUiIHJ5PSIzMCIgZmlsbD0iI0ZGRkZGRiIgc3Ryb2tlPSIjMUMyQjJCIiBzdHJva2Utd2lkdGg9IjQiLz48L3N2Zz4=\","
        + "\"04_eye_right.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI2MCIgaGVpZ2h0PSI2OCIgdmlld0JveD0iMCAwIDYwIDY4IiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxlbGxpcHNlIGN4PSIzMCIgY3k9IjM0IiByeD0iMjUiIHJ5PSIzMCIgZmlsbD0iI0ZGRkZGRiIgc3Ryb2tlPSIjMUMyQjJCIiBzdHJva2Utd2lkdGg9IjQiLz48L3N2Zz4=\","
        + "\"05_pupil_left.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMCIgaGVpZ2h0PSIzMCIgdmlld0JveD0iMCAwIDMwIDMwIiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxjaXJjbGUgY3g9IjE1IiBjeT0iMTUiIHI9IjEyIiBmaWxsPSIjM0IyMzE0Ii8+PGNpcmNsZSBjeD0iMTkuNSIgY3k9IjEwIiByPSI0IiBmaWxsPSIjRkZGRkZGIi8+PC9zdmc+\","
        + "\"06_pupil_right.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMCIgaGVpZ2h0PSIzMCIgdmlld0JveD0iMCAwIDMwIDMwIiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxjaXJjbGUgY3g9IjE1IiBjeT0iMTUiIHI9IjEyIiBmaWxsPSIjM0IyMzE0Ii8+PGNpcmNsZSBjeD0iMTkuNSIgY3k9IjEwIiByPSI0IiBmaWxsPSIjRkZGRkZGIi8+PC9zdmc+\","
        + "\"07_lid_left.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI2MCIgaGVpZ2h0PSI2OCIgdmlld0JveD0iMCAwIDYwIDY4IiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxwYXRoIGQ9Ik01IDM0IEEyNSAzMCAwIDAgMSA1NSAzNCBaIiBmaWxsPSIjM0ZDMUIwIiBzdHJva2U9IiMxQzJCMkIiIHN0cm9rZS13aWR0aD0iNCIvPjwvc3ZnPg==\","
        + "\"08_lid_right.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI2MCIgaGVpZ2h0PSI2OCIgdmlld0JveD0iMCAwIDYwIDY4IiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxwYXRoIGQ9Ik01IDM0IEEyNSAzMCAwIDAgMSA1NSAzNCBaIiBmaWxsPSIjM0ZDMUIwIiBzdHJva2U9IiMxQzJCMkIiIHN0cm9rZS13aWR0aD0iNCIvPjwvc3ZnPg==\","
        + "\"09_headphones.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyMjQiIGhlaWdodD0iMTIwIiB2aWV3Qm94PSIwIDAgMjI0IDEyMCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIj48cGF0aCBkPSJNMTYgNTAgQzE2IC02IDIwOCAtNiAyMDggNTAiIGZpbGw9Im5vbmUiIHN0cm9rZT0iIzFDMkIyQiIgc3Ryb2tlLXdpZHRoPSIxNiIvPjxwYXRoIGQ9Ik0xNiA1MCBDMTYgLTYgMjA4IC02IDIwOCA1MCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSIjRjI4QzI4IiBzdHJva2Utd2lkdGg9IjgiLz48ZWxsaXBzZSBjeD0iMTYiIGN5PSI3MCIgcng9IjE1IiByeT0iMjYiIGZpbGw9IiNEOTZGMEYiIHN0cm9rZT0iIzFDMkIyQiIgc3Ryb2tlLXdpZHRoPSI1Ii8+PGVsbGlwc2UgY3g9IjIwOCIgY3k9IjcwIiByeD0iMTUiIHJ5PSIyNiIgZmlsbD0iI0Q5NkYwRiIgc3Ryb2tlPSIjMUMyQjJCIiBzdHJva2Utd2lkdGg9IjUiLz48L3N2Zz4=\","
        + "\"10_brow_left.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI1NiIgaGVpZ2h0PSIyMiIgdmlld0JveD0iMCAwIDU2IDIyIiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxwYXRoIGQ9Ik02IDE0IFEyOCAyIDUwIDEyIiBmaWxsPSJub25lIiBzdHJva2U9IiMxQzJCMkIiIHN0cm9rZS13aWR0aD0iNiIvPjwvc3ZnPg==\","
        + "\"11_brow_right.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI1NiIgaGVpZ2h0PSIyMiIgdmlld0JveD0iMCAwIDU2IDIyIiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxwYXRoIGQ9Ik02IDEyIFEyOCAyIDUwIDE0IiBmaWxsPSJub25lIiBzdHJva2U9IiMxQzJCMkIiIHN0cm9rZS13aWR0aD0iNiIvPjwvc3ZnPg==\","
        + "\"12_mouth_neutral.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI2NCIgaGVpZ2h0PSI0NCIgdmlld0JveD0iMCAwIDY0IDQ0IiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxwYXRoIGQ9Ik0xOCAyNCBRMzIgMjggNDYgMjQiIGZpbGw9Im5vbmUiIHN0cm9rZT0iIzFDMkIyQiIgc3Ryb2tlLXdpZHRoPSI1Ii8+PC9zdmc+\","
        + "\"13_mouth_smile.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI2NCIgaGVpZ2h0PSI0NCIgdmlld0JveD0iMCAwIDY0IDQ0IiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxwYXRoIGQ9Ik0xMiAxNiBRMzIgMzYgNTIgMTYiIGZpbGw9Im5vbmUiIHN0cm9rZT0iIzFDMkIyQiIgc3Ryb2tlLXdpZHRoPSI1Ii8+PHBhdGggZD0iTTggMTIgTDEyIDE2IE01NiAxMiBMNTIgMTYiIGZpbGw9Im5vbmUiIHN0cm9rZT0iIzFDMkIyQiIgc3Ryb2tlLXdpZHRoPSIzLjUiLz48L3N2Zz4=\","
        + "\"14_mouth_open.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI2NCIgaGVpZ2h0PSI0NCIgdmlld0JveD0iMCAwIDY0IDQ0IiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxlbGxpcHNlIGN4PSIzMiIgY3k9IjIyIiByeD0iMTYiIHJ5PSIxNSIgZmlsbD0iIzVBMUUxRSIgc3Ryb2tlPSIjMUMyQjJCIiBzdHJva2Utd2lkdGg9IjQiLz48ZWxsaXBzZSBjeD0iMzIiIGN5PSIzMCIgcng9IjkiIHJ5PSI1LjUiIGZpbGw9IiNFODZBN0EiLz48L3N2Zz4=\","
        + "\"15_mouth_sad.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI2NCIgaGVpZ2h0PSI0NCIgdmlld0JveD0iMCAwIDY0IDQ0IiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxwYXRoIGQ9Ik0xNCAzMSBRMzIgMTIgNTAgMzEiIGZpbGw9Im5vbmUiIHN0cm9rZT0iIzFDMkIyQiIgc3Ryb2tlLXdpZHRoPSI1Ii8+PC9zdmc+\","
        + "\"16_mouth_surprise.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI2NCIgaGVpZ2h0PSI0NCIgdmlld0JveD0iMCAwIDY0IDQ0IiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxlbGxpcHNlIGN4PSIzMiIgY3k9IjIyIiByeD0iOSIgcnk9IjEyIiBmaWxsPSIjNUExRTFFIiBzdHJva2U9IiMxQzJCMkIiIHN0cm9rZS13aWR0aD0iNCIvPjwvc3ZnPg==\","
        + "\"17_body.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNTAiIGhlaWdodD0iMTkwIiB2aWV3Qm94PSIwIDAgMTUwIDE5MCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIj48cGF0aCBkPSJNNzUgOCBDMTIwIDggMTQyIDUwIDE0MiAxMDUgQzE0MiAxNjAgMTE1IDE4MiA3NSAxODIgQzM1IDE4MiA4IDE2MCA4IDEwNSBDOCA1MCAzMCA4IDc1IDggWiIgZmlsbD0iIzNGQzFCMCIgc3Ryb2tlPSIjMUMyQjJCIiBzdHJva2Utd2lkdGg9IjYiLz48ZWxsaXBzZSBjeD0iNzUiIGN5PSIxMTUiIHJ4PSIzOCIgcnk9IjUwIiBmaWxsPSIjOEZFM0Q2Ii8+PC9zdmc+\","
        + "\"18_vest.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNTAiIGhlaWdodD0iMTkwIiB2aWV3Qm94PSIwIDAgMTUwIDE5MCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIj48cGF0aCBkPSJNMTIgODAgTDUyIDY4IEw3NSAxMjAgTDk4IDY4IEwxMzggODAgQzE0MSA5NiAxNDIgMTAyIDE0MiAxMDggQzE0MiAxNjAgMTE1IDE4MiA3NSAxODIgQzM1IDE4MiA4IDE2MCA4IDEwOCBDOCAxMDIgOSA5NiAxMiA4MCBaIiBmaWxsPSIjNEI0RkEzIiBzdHJva2U9IiMxQzJCMkIiIHN0cm9rZS13aWR0aD0iNSIvPjxyZWN0IHg9IjIyIiB5PSIxMzIiIHdpZHRoPSIzNCIgaGVpZ2h0PSIyNiIgcng9IjYiIGZpbGw9Im5vbmUiIHN0cm9rZT0iIzFDMkIyQiIgc3Ryb2tlLXdpZHRoPSI0Ii8+PHJlY3QgeD0iOTQiIHk9IjEzMiIgd2lkdGg9IjM0IiBoZWlnaHQ9IjI2IiByeD0iNiIgZmlsbD0ibm9uZSIgc3Ryb2tlPSIjMUMyQjJCIiBzdHJva2Utd2lkdGg9IjQiLz48cGF0aCBkPSJNMTQgNTYgUTc1IDg2IDEzNiA1NiBMMTM5IDgwIFE3NSAxMTAgMTEgODAgWiIgZmlsbD0iI0YyNkI0QiIgc3Ryb2tlPSIjMUMyQjJCIiBzdHJva2Utd2lkdGg9IjUiLz48cGF0aCBkPSJNOTggOTQgTDExNiAxMzIgTDk2IDEzNiBMODYgMTAwIFoiIGZpbGw9IiNGMjZCNEIiIHN0cm9rZT0iIzFDMkIyQiIgc3Ryb2tlLXdpZHRoPSI1Ii8+PC9zdmc+\","
        + "\"19_backpack.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxOTAiIGhlaWdodD0iMTUwIiB2aWV3Qm94PSIwIDAgMTkwIDE1MCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIj48cmVjdCB4PSI4IiB5PSI4IiB3aWR0aD0iMTc0IiBoZWlnaHQ9IjEzNCIgcng9IjI2IiBmaWxsPSIjOEI1QTJCIiBzdHJva2U9IiMxQzJCMkIiIHN0cm9rZS13aWR0aD0iNiIvPjxwYXRoIGQ9Ik04IDYyIFE5NSA5NCAxODIgNjIiIGZpbGw9Im5vbmUiIHN0cm9rZT0iIzFDMkIyQiIgc3Ryb2tlLXdpZHRoPSI1Ii8+PHJlY3QgeD0iNjAiIHk9Ijk0IiB3aWR0aD0iNzAiIGhlaWdodD0iNDAiIHJ4PSIxMCIgZmlsbD0iIzZFNDYyMCIgc3Ryb2tlPSIjMUMyQjJCIiBzdHJva2Utd2lkdGg9IjQiLz48cmVjdCB4PSI4OCIgeT0iNjgiIHdpZHRoPSIxNCIgaGVpZ2h0PSIxNiIgcng9IjMiIGZpbGw9IiNEOUIyNEMiIHN0cm9rZT0iIzFDMkIyQiIgc3Ryb2tlLXdpZHRoPSIzIi8+PC9zdmc+\","
        + "\"20_shoulder_left.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzNCIgaGVpZ2h0PSI2MiIgdmlld0JveD0iMCAwIDM0IDYyIiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxyZWN0IHg9IjUiIHk9IjYiIHdpZHRoPSIyNCIgaGVpZ2h0PSI1MCIgcng9IjEyIiBmaWxsPSIjM0ZDMUIwIiBzdHJva2U9IiMxQzJCMkIiIHN0cm9rZS13aWR0aD0iNSIvPjwvc3ZnPg==\","
        + "\"21_forearm_left.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSI2MiIgdmlld0JveD0iMCAwIDMyIDYyIiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxyZWN0IHg9IjQiIHk9IjYiIHdpZHRoPSIyNCIgaGVpZ2h0PSI0OCIgcng9IjEyIiBmaWxsPSIjM0ZDMUIwIiBzdHJva2U9IiMxQzJCMkIiIHN0cm9rZS13aWR0aD0iNSIvPjwvc3ZnPg==\","
        + "\"22_hand_left.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI0NCIgaGVpZ2h0PSI0OCIgdmlld0JveD0iMCAwIDQ0IDQ4IiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxlbGxpcHNlIGN4PSIyMiIgY3k9IjI4IiByeD0iMTYiIHJ5PSIxOCIgZmlsbD0iIzFGMkE0NCIgc3Ryb2tlPSIjMUMyQjJCIiBzdHJva2Utd2lkdGg9IjUiLz48ZWxsaXBzZSBjeD0iMzUiIGN5PSIzMCIgcng9IjciIHJ5PSIxMCIgZmlsbD0iIzFGMkE0NCIgc3Ryb2tlPSIjMUMyQjJCIiBzdHJva2Utd2lkdGg9IjQiLz48L3N2Zz4=\","
        + "\"23_shoulder_right.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzNCIgaGVpZ2h0PSI2MiIgdmlld0JveD0iMCAwIDM0IDYyIiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxyZWN0IHg9IjUiIHk9IjYiIHdpZHRoPSIyNCIgaGVpZ2h0PSI1MCIgcng9IjEyIiBmaWxsPSIjM0ZDMUIwIiBzdHJva2U9IiMxQzJCMkIiIHN0cm9rZS13aWR0aD0iNSIvPjwvc3ZnPg==\","
        + "\"24_forearm_right.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzMiIgaGVpZ2h0PSI2MiIgdmlld0JveD0iMCAwIDMyIDYyIiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxyZWN0IHg9IjQiIHk9IjYiIHdpZHRoPSIyNCIgaGVpZ2h0PSI0OCIgcng9IjEyIiBmaWxsPSIjM0ZDMUIwIiBzdHJva2U9IiMxQzJCMkIiIHN0cm9rZS13aWR0aD0iNSIvPjwvc3ZnPg==\","
        + "\"25_hand_right.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI0NCIgaGVpZ2h0PSI0OCIgdmlld0JveD0iMCAwIDQ0IDQ4IiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxlbGxpcHNlIGN4PSIyMiIgY3k9IjI4IiByeD0iMTYiIHJ5PSIxOCIgZmlsbD0iIzFGMkE0NCIgc3Ryb2tlPSIjMUMyQjJCIiBzdHJva2Utd2lkdGg9IjUiLz48ZWxsaXBzZSBjeD0iOSIgY3k9IjMwIiByeD0iNyIgcnk9IjEwIiBmaWxsPSIjMUYyQTQ0IiBzdHJva2U9IiMxQzJCMkIiIHN0cm9rZS13aWR0aD0iNCIvPjwvc3ZnPg==\","
        + "\"26_thigh_left.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzNiIgaGVpZ2h0PSI1NiIgdmlld0JveD0iMCAwIDM2IDU2IiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxyZWN0IHg9IjYiIHk9IjYiIHdpZHRoPSIyNCIgaGVpZ2h0PSI0NCIgcng9IjEyIiBmaWxsPSIjM0ZDMUIwIiBzdHJva2U9IiMxQzJCMkIiIHN0cm9rZS13aWR0aD0iNSIvPjwvc3ZnPg==\","
        + "\"27_shin_left.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzNCIgaGVpZ2h0PSI1NiIgdmlld0JveD0iMCAwIDM0IDU2IiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxyZWN0IHg9IjUiIHk9IjYiIHdpZHRoPSIyNCIgaGVpZ2h0PSI0NCIgcng9IjEyIiBmaWxsPSIjM0ZDMUIwIiBzdHJva2U9IiMxQzJCMkIiIHN0cm9rZS13aWR0aD0iNSIvPjwvc3ZnPg==\","
        + "\"28_foot_left.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI2MCIgaGVpZ2h0PSIzNiIgdmlld0JveD0iMCAwIDYwIDM2IiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxyZWN0IHg9IjQiIHk9IjgiIHdpZHRoPSI1MiIgaGVpZ2h0PSIyNCIgcng9IjEyIiBmaWxsPSIjMUYyQTQ0IiBzdHJva2U9IiMxQzJCMkIiIHN0cm9rZS13aWR0aD0iNSIvPjxwYXRoIGQ9Ik0xMCAyNiBMNTAgMjYiIHN0cm9rZT0iIzNCNEE2QiIgc3Ryb2tlLXdpZHRoPSI0Ii8+PC9zdmc+\","
        + "\"29_thigh_right.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzNiIgaGVpZ2h0PSI1NiIgdmlld0JveD0iMCAwIDM2IDU2IiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxyZWN0IHg9IjYiIHk9IjYiIHdpZHRoPSIyNCIgaGVpZ2h0PSI0NCIgcng9IjEyIiBmaWxsPSIjM0ZDMUIwIiBzdHJva2U9IiMxQzJCMkIiIHN0cm9rZS13aWR0aD0iNSIvPjwvc3ZnPg==\","
        + "\"30_shin_right.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzNCIgaGVpZ2h0PSI1NiIgdmlld0JveD0iMCAwIDM0IDU2IiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxyZWN0IHg9IjUiIHk9IjYiIHdpZHRoPSIyNCIgaGVpZ2h0PSI0NCIgcng9IjEyIiBmaWxsPSIjM0ZDMUIwIiBzdHJva2U9IiMxQzJCMkIiIHN0cm9rZS13aWR0aD0iNSIvPjwvc3ZnPg==\","
        + "\"31_foot_right.svg\":\"data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI2MCIgaGVpZ2h0PSIzNiIgdmlld0JveD0iMCAwIDYwIDM2IiBzdHJva2UtbGluZWpvaW49InJvdW5kIiBzdHJva2UtbGluZWNhcD0icm91bmQiPjxyZWN0IHg9IjQiIHk9IjgiIHdpZHRoPSI1MiIgaGVpZ2h0PSIyNCIgcng9IjEyIiBmaWxsPSIjMUYyQTQ0IiBzdHJva2U9IiMxQzJCMkIiIHN0cm9rZS13aWR0aD0iNSIvPjxwYXRoIGQ9Ik0xMCAyNiBMNTAgMjYiIHN0cm9rZT0iIzNCNEE2QiIgc3Ryb2tlLXdpZHRoPSI0Ii8+PC9zdmc+\""
        + "};";

        htmlCode += "const svgNS='http://www.w3.org/2000/svg';const svg=document.getElementById('character');const pet=document.getElementById('pet');const bubble=document.getElementById('bubble');const partMap=Object.fromEntries(manifest.parts.map(p=>[p.id,p]));const hiddenLids=['07_lid_left','08_lid_right'];let sleeping=false,posX=12,direction=1,speed=0.75;let bubbleTimer=null;function addPart(parent,id,extraClass){const p=partMap[id];if(!p)return null;const g=document.createElementNS(svgNS,'g');g.setAttribute('data-part',id);if(extraClass)g.setAttribute('class',extraClass);const image=document.createElementNS(svgNS,'image');const base64Src=base64Images[p.file];if(base64Src){image.setAttribute('href',base64Src);}else{image.setAttribute('href',p.file);}image.setAttribute('x',-p.pivot[0]);image.setAttribute('y',-p.pivot[1]);image.setAttribute('width',p.size[0]);image.setAttribute('height',p.size[1]);image.setAttribute('preserveAspectRatio','none');g.appendChild(image);g.setAttribute('transform',`translate(${p.place[0]} ${p.place[1]})`);parent.appendChild(g);return g;}function groupAt(id,x,y,cls){const g=document.createElementNS(svgNS,'g');g.setAttribute('id',id);if(cls)g.setAttribute('class',cls);g.setAttribute('transform',`translate(${x} ${y})`);svg.appendChild(g);return g;}function addAt(parent,id,x,y){const p=partMap[id];if(!p)return;const g=document.createElementNS(svgNS,'g');g.setAttribute('data-part',id);g.setAttribute('transform',`translate(${x} ${y})`);const im=document.createElementNS(svgNS,'image');const base64Src=base64Images[p.file];if(base64Src){im.setAttribute('href',base64Src);}else{im.setAttribute('href',p.file);}im.setAttribute('x',-p.pivot[0]);im.setAttribute('y',-p.pivot[1]);im.setAttribute('width',p.size[0]);im.setAttribute('height',p.size[1]);im.setAttribute('preserveAspectRatio','none');g.appendChild(im);parent.appendChild(g);return g;}function buildPet(){svg.innerHTML='';addPart(svg,'19_backpack');const ll=groupAt('leg-left',176,408,'');addAt(ll,'26_thigh_left',0,0);addAt(ll,'27_shin_left',0,36);addAt(ll,'28_foot_left',0,72);const lr=groupAt('leg-right',224,408,'');addAt(lr,'29_thigh_right',0,0);addAt(lr,'30_shin_right',0,36);addAt(lr,'31_foot_right',0,72);addPart(svg,'17_body');addPart(svg,'18_vest');const al=groupAt('arm-left',142,296,'');addAt(al,'20_shoulder_left',0,0);addAt(al,'21_forearm_left',0,36);addAt(al,'22_hand_left',0,70);const ar=groupAt('arm-right',258,296,'');addAt(ar,'23_shoulder_right',0,0);addAt(ar,'24_forearm_right',0,36);addAt(ar,'25_hand_right',0,70);addPart(svg,'01_head');addPart(svg,'09_headphones');addPart(svg,'02_hair');addPart(svg,'10_brow_left');addPart(svg,'11_brow_right');addPart(svg,'03_eye_left','eye-white');addPart(svg,'04_eye_right','eye-white');addPart(svg,'05_pupil_left','pupil');addPart(svg,'06_pupil_right','pupil');hiddenLids.forEach(id=>{const lid=addPart(svg,id,'eyelid');if(lid)lid.style.display='none';});setMouth('13_mouth_smile');}function setMouth(id){svg.querySelectorAll('[data-mouth=true]').forEach(n=>n.remove());const p=partMap[id];if(!p)return;const g=addPart(svg,id,'mouth');if(g)g.setAttribute('data-mouth','true');}function setEyesClosed(closed){hiddenLids.forEach(id=>{const el=svg.querySelector(`[data-part=${id}]`);if(el)el.style.display=closed?'':'none';});}function showPetMessage(text,duration=3500){bubble.textContent=String(text);bubble.style.display='block';if(bubbleTimer)clearTimeout(bubbleTimer);if(duration>0)bubbleTimer=setTimeout(()=>bubble.style.display='none',duration);}function hidePetMessage(){bubble.style.display='none';if(bubbleTimer)clearTimeout(bubbleTimer);}window.showPetMessage=showPetMessage;function wake(){sleeping=false;pet.classList.remove('sleeping');setEyesClosed(false);pet.classList.add('walking');}window.petAction=function(action){if(action==='feed'){wake();setMouth('14_mouth_open');showPetMessage('Ням-ням! Спасибо за яблочко 🍎');setTimeout(()=>setMouth('13_mouth_smile'),1500);}else if(action==='play'){wake();pet.classList.remove('jumping');void pet.offsetWidth;pet.classList.add('jumping');showPetMessage('Ура! Давай играть! ⚽');setTimeout(()=>pet.classList.remove('jumping'),750);}else if(action==='sleep'){sleeping=true;pet.classList.remove('walking','jumping');pet.classList.add('sleeping');setEyesClosed(true);showPetMessage('Спокойной ночи... 💤',0);}else if(action==='wake'){wake();hidePetMessage();}};window.onServerReply=function(reply){wake();showPetMessage(reply,6000);setMouth('13_mouth_smile');};function walkFrame(){if(!sleeping){const width=pet.getBoundingClientRect().width||190;const max=Math.max(0,window.innerWidth-width-4);posX+=speed*direction;if(posX>=max){posX=max;direction=-1;}if(posX<=0){posX=0;direction=1;}pet.style.left=posX+'px';pet.style.transform=direction<0?'scaleX(-1)':'scaleX(1)';pet.classList.add('walking');}requestAnimationFrame(walkFrame);}window.addEventListener('resize',()=>{posX=Math.min(posX,Math.max(0,innerWidth-pet.offsetWidth));});buildPet();pet.classList.add('walking');requestAnimationFrame(walkFrame);</script></body></html>";

        petWebView.loadDataWithBaseURL("file:///android_asset/", htmlCode, "text/html", "UTF-8", null);

        petParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        petParams.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(petWebView, petParams);
    }

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
            setChatText("Слушаю... 🎤");
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
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(dp(58), dp(48));
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
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        controlParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        controlParams.y = dp(18);
        controlParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
        controlParams.setTitle("iZi controls");

        windowManager.addView(controlRoot, controlParams);
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f);
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
        controlParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        windowManager.updateViewLayout(controlRoot, controlParams);
        if (showKeyboard) {
            messageInput.requestFocus();
            messageInput.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(messageInput, InputMethodManager.SHOW_IMPLICIT);
            }, 250);
        }
    }

    private void closeChat() {
        if (chatPanel == null || controlRoot == null) return;
        chatOpen = false;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && messageInput != null) {
            imm.hideSoftInputFromWindow(messageInput.getWindowToken(), 0);
        }
        chatPanel.setVisibility(View.GONE);
        controlParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        controlParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
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
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");

                JSONObject body = new JSONObject();
                body.put("message", message);
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(bytes);
                }

                int status = connection.getResponseCode();
                InputStream stream = (status >= 200 && status < 300) ? connection.getInputStream() : connection.getErrorStream();
                StringBuilder response = new StringBuilder();
                if (stream != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) response.append(line);
                    }
                }
                if (status < 200 || status >= 300) {
                    throw new IllegalStateException("HTTP " + status + (response.length() > 0 ? ": " + response : ""));
                }

                JSONObject result = new JSONObject(response.toString());
                String reply = result.optString("reply", "Я получил сообщение, но ответа нет 🐾");
                deliverReply(reply);
            } catch (Exception e) {
                String messageText = "Не удалось связаться с сервером: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
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
                petWebView.evaluateJavascript("window.onServerReply && window.onServerReply(" + quoted + ");", null);
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
                try { windowManager.removeViewImmediate(controlRoot); } catch (Exception ignored) {}
                controlRoot = null;
            }
            if (petWebView != null) {
                try { windowManager.removeViewImmediate(petWebView); } catch (Exception ignored) {}
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