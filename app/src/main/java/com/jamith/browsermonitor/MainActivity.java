package com.jamith.browsermonitor;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.content.ComponentName;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityManager;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private Button openSettingsButton, viewLogsButton;
    private TextView logsTextView;
    private static final String DATA_FILE = "browsing_data.txt";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        openSettingsButton = findViewById(R.id.btnOpenSettings);
        viewLogsButton = findViewById(R.id.btnViewLogs);
        logsTextView = findViewById(R.id.txtLogs);

        openSettingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "Enable BrowserMonitorService", Toast.LENGTH_LONG).show();
        });

        viewLogsButton.setOnClickListener(v -> displayLogs());
    }

    private void displayLogs() {
        File file = new File(getFilesDir(), DATA_FILE);
        if (!file.exists()) {
            logsTextView.setText("No data available.");
            return;
        }

        StringBuilder builder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                builder.append(line).append("\n");
            }
        } catch (IOException e) {
            logsTextView.setText("Error reading log file: " + e.getMessage());
            return;
        }

        logsTextView.setText(builder.toString());
    }
}
