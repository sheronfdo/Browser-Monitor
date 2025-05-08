package com.jamith.browsermonitor;


import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.jamith.browsermonitor.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String DATA_FILE = "browser_data.txt";
    private TextView logTextView;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "MainActivity created.");

        logTextView = findViewById(R.id.logTextView);
        scrollView = findViewById(R.id.scrollView);
        Button openSettingsButton = findViewById(R.id.openSettingsButton);
        Button viewLogsButton = findViewById(R.id.viewLogsButton);

        openSettingsButton.setOnClickListener(v -> {
            Log.d(TAG, "Opening Accessibility Settings");
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });

        viewLogsButton.setOnClickListener(v -> {
            Log.d(TAG, "Viewing log file");
            displayLogs();
        });
    }

    private void displayLogs() {
        File file = new File(getFilesDir(), DATA_FILE);
        Log.d(TAG, "Attempting to read: " + file.getAbsolutePath());

        if (!file.exists()) {
            logTextView.setText("No data found.");
            Log.w(TAG, "Log file not found.");
            return;
        }

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
            logTextView.setText(builder.toString());
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
            Log.d(TAG, "Log file loaded successfully.");
        } catch (IOException e) {
            logTextView.setText("Error reading data.");
            Log.e(TAG, "Error reading log file: " + e.getMessage());
        }
    }
}
