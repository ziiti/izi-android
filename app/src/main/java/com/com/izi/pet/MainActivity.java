package com.izi.pet;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.view.Gravity;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#FFF0F5"));

        TextView title = new TextView(this);
        title.setText("iZi Pet");
        title.setTextSize(40);
        title.setTextColor(Color.parseColor("#D36B9A"));
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        Button btnStart = new Button(this);
        btnStart.setText("Выпустить погулять");
        btnStart.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else {
                startService(new Intent(MainActivity.this, OverlayService.class));
                finish();
            }
        });
        layout.addView(btnStart);

        Button btnStop = new Button(this);
        btnStop.setText("Забрать домой");
        btnStop.setOnClickListener(v -> stopService(new Intent(MainActivity.this, OverlayService.class)));
        layout.addView(btnStop);

        setContentView(layout);
    }
}