package com.izi.pet;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_OVERLAY = 7301;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(32, 48, 32, 32);

        TextView title = new TextView(this);
        title.setText("iZi Pet 🐾");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView description = new TextView(this);
        description.setText("Выпусти питомца поверх других приложений. " +
                "Для работы потребуется разрешение «Поверх других окон».");
        description.setTextSize(16);
        description.setPadding(0, 24, 0, 24);
        root.addView(description);

        Button start = new Button(this);
        start.setText("Выпустить iZi");
        root.addView(start, new LinearLayout.LayoutParams(-1, -2));

        Button stop = new Button(this);
        stop.setText("Забрать домой");
        root.addView(stop, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);

        start.setOnClickListener(v -> startPet());
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, OverlayService.class));
            Toast.makeText(this, "iZi вернулся домой 🏠", Toast.LENGTH_SHORT).show();
        });
    }

    private void startPet() {
        if (!Settings.canDrawOverlays(this)) {
            Intent permission = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(permission, REQUEST_OVERLAY);
            return;
        }

        Intent service = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }
        Toast.makeText(this, "iZi выходит гулять 🐾", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY && Settings.canDrawOverlays(this)) {
            startPet();
        }
    }
}
