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
        settings.setBlockNetworkImage(false);
        
        webView.setWebChromeClient(new WebChromeClient());
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        // ДОБАВЛЯЕМ МОСТ: Теперь HTML может просить Java сделать запрос
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        // Вшиваем исправленный HTML (без fetch, с мостом)
        String htmlCode = "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>" +
            "body { background: transparent; margin: 0; overflow: hidden; width: 100vw; height: 100vh; }" +
            "#pet-container { position: absolute; bottom: 10px; left: 50px; width: 160px; height: 200px; transition: left 0.1s linear; }" +
            "svg { width: 100%; height: 100%; filter: drop-shadow(0 10px 10px rgba(0,0,0,0.15)); }" +
            "#cloud { position: absolute; top: 10px; left: 50%; transform: translateX(-50%); width: 90%; max-width: 320px; background: rgba(255,255,255,0.98); border: 2px solid #D36B9A; border-radius: 20px; padding: 12px; display: none; flex-direction: column; z-index: 20; box-shadow: 0 5px 15px rgba(0,0,0,0.2); }" +
            "#cloud.active { display: flex; }" +
            "#cloud-text { font-size: 14px; color: #333; text-align: center; margin-bottom: 8px; font-weight: bold; }" +
            ".input-row { display: flex; gap: 6px; margin-bottom: 10px; }" +
            "#chat-input { flex: 1; padding: 8px; border-radius: 10px; border: 1px solid #ccc; }" +
            ".send-btn { background: #D36B9A; color: white; border: none; border-radius: 10px; padding: 8px 12px; font-weight: bold; }" +
            ".action-row { display: flex; justify-content: space-around; }" +
            ".action-btn { font-size: 24px; background: #FFF0F5; border: 1px solid #D36B9A; border-radius: 50%; width: 45px; height: 45px; display: flex; justify-content: center; align-items: center; }" +
            ".controls { position: absolute; bottom: 0; left: 50%; transform: translateX(-50%); display: flex; gap: 15px; z-index: 30; }" +
            ".ctrl-btn { width: 50px; height: 50px; border-radius: 50%; background: white; border: 2px solid #D36B9A; font-size: 24px; display: flex; justify-content: center; align-items: center; }" +
            "</style></head><body>" +
            "<div id='cloud'>" +
            "  <div id='cloud-text'>Привет! Я iZi.</div>" +
            "  <div class='input-row'><input type='text' id='chat-input' placeholder='Написать...'><button class='send-btn' onclick='sendMessage()'>➤</button></div>" +
            "  <div class='action-row'>" +
            "    <button class='action-btn' onclick='doAction(\"feed\")'>🍎</button>" +
            "    <button class='action-btn' onclick='doAction(\"play\")'>⚽</button>" +
            "    <button class='action-btn' onclick='doAction(\"sleep\")'>💤</button>" +
            "  </div>" +
            "</div>" +
            "<div id='pet-container'><div id='pet-svg-wrapper'></div></div>" +
            "<div class='controls'><button class='ctrl-btn' onclick='toggleCloud()'>💬</button><button class='ctrl-btn' onclick='startMic()'>🎤</button></div>" +
            "<script>" +
            "let manifest = { parts: [" +
            "{id:'01_head', file:'01_head.svg', size:[180,160], pivot:[90,80]}," +
            "{id:'03_eye_left', file:'03_eye_left.svg', size:[60,68], pivot:[30,34]}," +
            "{id:'04_eye_right', file:'04_eye_right.svg', size:[60,68], pivot:[30,34]}," +
            "{id:'05_pupil_left', file:'05_pupil_left.svg', size:[30,30], pivot:[15,15]}," +
            "{id:'06_pupil_right', file:'06_pupil_right.svg', size:[30,30], pivot:[15,15]}," +
            "{id:'07_lid_left', file:'07_lid_left.svg', size:[60,68], pivot:[30,34]}," +
            "{id:'08_lid_right', file:'08_lid_right.svg', size:[60,68], pivot:[30,34]}," +
            "{id:'09_headphones', file:'09_headphones.svg', size:[224,120], pivot:[112,70]}," +
            "{id:'10_brow_left', file:'10_brow_left.svg', size:[56,22], pivot:[28,11]}," +
            "{id:'11_brow_right', file:'11_brow_right.svg', size:[56,22], pivot:[28,11]}," +
            "{id:'13_mouth_smile', file:'13_mouth_smile.svg', size:[64,44], pivot:[32,22]}," +
            "{id:'14_mouth_open', file:'14_mouth_open.svg', size:[64,44], pivot:[32,22]}," +
            "{id:'17_body', file:'17_body.svg', size:[150,190], pivot:[75,95]}," +
            "{id:'18_vest', file:'18_vest.svg', size:[150,190], pivot:[75,95]}," +
            "{id:'19_backpack', file:'19_backpack.svg', size:[190,150], pivot:[95,75]}," +
            "{id:'20_shoulder_left', file:'20_shoulder_left.svg', size:[34,62], pivot:[17,14]}," +
            "{id:'21_forearm_left', file:'21_forearm_left.svg', size:[32,62], pivot:[16,14]}," +
            "{id:'22_hand_left', file:'22_hand_left.svg', size:[44,48], pivot:[22,12]}," +
            "{id:'23_shoulder_right', file:'23_shoulder_right.svg', size:[34,62], pivot:[17,14]}," +
            "{id:'24_forearm_right', file:'24_forearm_right.svg', size:[32,62], pivot:[16,14]}," +
            "{id:'25_hand_right', file:'25_hand_right.svg', size:[44,48], pivot:[22,12]}," +
            "{id:'26_thigh_left', file:'26_thigh_left.svg', size:[36,56], pivot:[18,14]}," +
            "{id:'27_shin_left', file:'27_shin_left.svg', size:[34,56], pivot:[17,14]}," +
            "{id:'28_foot_left', file:'28_foot_left.svg', size:[60,36], pivot:[30,8]}," +
            "{id:'29_thigh_right', file:'29_thigh_right.svg', size:[36,56], pivot:[18,14]}," +
            "{id:'30_shin_right', file:'30_shin_right.svg', size:[34,56], pivot:[17,14]}," +
            "{id:'31_foot_right', file:'31_foot_right.svg', size:[60,36], pivot:[30,8]}" +
            "]};" +
            
            "let currentMouth = '13_mouth_smile';" +
            "let isSleeping = false;" +
            "let isWalking = true;" +
            "let posX = 50;" +
            "let dir = 1;" +

            "function createImg(id) { const p = manifest.parts.find(x => x.id === id); if(!p) return ''; return `<image href='${p.file}' x='${-p.pivot[0]}' y='${-p.pivot[1]}' width='${p.size[0]}' height='${p.size[1]}' />`; }" +
            
            "function buildPet() {" +
            "  let eyes = isSleeping ? createImg('07_lid_left') + createImg('08_lid_right') : createImg('03_eye_left') + createImg('05_pupil_left') + createImg('04_eye_right') + createImg('06_pupil_right');" +
            "  document.getElementById('pet-svg-wrapper').innerHTML = `<svg viewBox='0 0 400 520'>" +
            "    <g transform='translate(200, 345)'>${createImg('19_backpack')}</g>" +
            "    <g id='leg-left' transform='translate(176, 408)'>${createImg('26_thigh_left')}<g transform='translate(0, 36)'>${createImg('27_shin_left')}</g><g transform='translate(0, 72)'>${createImg('28_foot_left')}</g></g>" +
            "    <g id='leg-right' transform='translate(224, 408)'>${createImg('29_thigh_right')}<g transform='translate(0, 36)'>${createImg('30_shin_right')}</g><g transform='translate(0, 72)'>${createImg('31_foot_right')}</g></g>" +
            "    <g transform='translate(200, 330)'>${createImg('17_body')}</g><g transform='translate(200, 330)'>${createImg('18_vest')}</g>" +
            "    <g transform='translate(142, 296)'>${createImg('20_shoulder_left')}<g transform='translate(0, 36)'>${createImg('21_forearm_left')}</g><g transform='translate(0, 70)'>${createImg('22_hand_left')}</g></g>" +
            "    <g transform='translate(258, 296)'>${createImg('23_shoulder_right')}<g transform='translate(0, 36)'>${createImg('24_forearm_right')}</g><g transform='translate(0, 70)'>${createImg('25_hand_right')}</g></g>" +
            "    <g transform='translate(200, 170)'>${createImg('01_head')}</g><g transform='translate(200, 160)'>${createImg('09_headphones')}</g>" +
            "    ${eyes}" +
            "    <g transform='translate(162, 128)'>${createImg('10_brow_left')}</g><g transform='translate(238, 128)'>${createImg('11_brow_right')}</g>" +
            "    <g transform='translate(200, 208)'>${createImg(currentMouth)}</g>" +
            "  </svg>`;" +
            "}" +

            "function movePet() {" +
            "  if (!isWalking || isSleeping) return;" +
            "  posX += dir * 1.5;" +
            "  if (posX > window.innerWidth - 160) { posX = window.innerWidth - 160; dir = -1; }" +
            "  if (posX < 10) { posX = 10; dir = 1; }" +
            "  document.getElementById('pet-container').style.left = posX + 'px';" +
            "}" +

            "function doAction(type) {" +
            "  if (type === 'feed') {" +
            "    currentMouth = '14_mouth_open';" +
            "    buildPet();" +
            "    document.getElementById('cloud-text').innerText = 'Ням-ням! Спасибо! 😋';" +
            "    setTimeout(() => { currentMouth = '13_mouth_smile'; buildPet(); }, 1500);" +
            "  } else if (type === 'play') {" +
            "    document.getElementById('cloud-text').innerText = 'Ура! Давай играть! 🎉';" +
            "    const pet = document.getElementById('pet-container');" +
            "    pet.style.transform = 'scale(1.1) translateY(-20px)';" +
            "    setTimeout(() => pet.style.transform = 'scale(1) translateY(0)', 300);" +
            "  } else if (type === 'sleep') {" +
            "    isSleeping = !isSleeping;" +
            "    buildPet();" +
            "    document.getElementById('cloud-text').innerText = isSleeping ? 'Я баиньки... 💤' : 'Я проснулся! ☀️';" +
            "  }" +
            "}" +

            "function toggleCloud() { document.getElementById('cloud').classList.toggle('active'); }" +
            
            // ВЫЗОВ JAVA-МОСТА ВМЕСТО FETCH
            "function sendMessage() { " +
            "  const text = document.getElementById('chat-input').value; if(!text) return; " +
            "  document.getElementById('cloud-text').innerText = 'Думаю...'; " +
            "  document.getElementById('chat-input').value = ''; " +
            "  if (window.AndroidBridge) { " +
            "    window.AndroidBridge.sendMessage(text); " +
            "  } else { " +
            "    document.getElementById('cloud-text').innerText = 'Ошибка: мост не найден'; " +
            "  }" +
            "}" +
            
            "function startMic() { document.getElementById('cloud-text').innerText = 'Слушаю...'; document.getElementById('cloud').classList.add('active'); }" +

            "window.onload = function() { buildPet(); setInterval(movePet, 50); };" +
            "</script></body></html>";

        webView.loadDataWithBaseURL("file:///android_asset/", htmlCode, "text/html", "UTF-8", null);

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, 
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;

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

    // --- МОСТ МЕЖДУ JAVA И JAVASCRIPT ---
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
                            // Парсим простой JSON {"reply":"..."}
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
