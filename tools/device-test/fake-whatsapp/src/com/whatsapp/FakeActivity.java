package com.whatsapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

/**
 * Shows and logs exactly what it was launched with, so a device test can assert on it:
 * {@code adb logcat -s FakeWhatsApp}.
 */
public class FakeActivity extends Activity {
    private TextView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new TextView(this);
        view.setTextSize(22);
        view.setPadding(48, 200, 48, 48);
        setContentView(view);
        show(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        show(intent);
    }

    private void show(Intent intent) {
        String text = "FAKE_WHATSAPP action=" + intent.getAction()
                + " data=" + intent.getDataString()
                + " type=" + intent.getType();
        Log.i("FakeWhatsApp", text);
        view.setText(text);
    }
}
