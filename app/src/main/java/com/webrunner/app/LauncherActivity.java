package com.webrunner.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Switch;

import androidx.appcompat.app.AppCompatActivity;

public class ToolsActivity extends AppCompatActivity {

    public static final String PREFS = "webrunner_prefs";
    public static final String KEY_DEV = "developer_mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tools);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Switch sw = findViewById(R.id.switch_dev);
        sw.setChecked(prefs.getBoolean(KEY_DEV, false));

        sw.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(KEY_DEV, isChecked).apply());
    }
}
