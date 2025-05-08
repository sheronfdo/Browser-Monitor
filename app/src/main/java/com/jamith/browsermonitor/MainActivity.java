package com.jamith.browsermonitor;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.view.accessibility.AccessibilityManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private Button toggleButton;
    private Button debugButton;
    private TextView statusText;
    private static final int ACCESSIBILITY_PERMISSION_REQUEST_CODE = 100;
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toggleButton = findViewById(R.id.toggle_button);
        debugButton = findViewById(R.id.debug_button);
        statusText = findViewById(R.id.status_text);

        toggleButton.setOnClickListener(v -> toggleAccessibilityService());
        debugButton.setOnClickListener(v -> displayData());
        updateStatus();
    }

    private void toggleAccessibilityService() {
        if (isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Browser Monitor Service is already enabled. Disable it in Accessibility settings if needed.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } else {
            showConsentDialog();
        }
    }

    private void showConsentDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Consent Required")
                .setMessage("This app monitors browsing activity to capture search queries and URLs for testing. Do you consent?")
                .setPositiveButton("Agree", (dialog, which) -> requestAccessibilityPermission())
                .setNegativeButton("Decline", null)
                .show();
    }

    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager accessibilityManager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        ComponentName serviceComponent = new ComponentName(this, BrowserMonitorService.class);
        String expectedId = serviceComponent.flattenToString();
        Log.d(TAG, "Expected service ID: " + expectedId);

        for (AccessibilityServiceInfo service : accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            String serviceId = service.getId();
            Log.d(TAG, "Found service ID: " + serviceId);
            if (serviceId.equals(expectedId)) {
                return true;
            }
        }
        return false;
    }

    private void requestAccessibilityPermission() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivityForResult(intent, ACCESSIBILITY_PERMISSION_REQUEST_CODE);
        Toast.makeText(this, "Please enable Browser Monitor Service in Accessibility settings", Toast.LENGTH_LONG).show();
    }

    private void updateStatus() {
        boolean isEnabled = isAccessibilityServiceEnabled();
        toggleButton.setText(isEnabled ? "Manage Monitoring" : "Enable Monitoring");
        statusText.setText(isEnabled ? "Status: Monitoring Active" : "Status: Monitoring Inactive");
    }

    private void displayData() {
        try {
            File file = new File(getFilesDir(), "browsing_data.txt");
            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();
            TextView dataView = findViewById(R.id.data_text);
            dataView.setText(content.length() > 0 ? content.toString() : "No data captured yet.");
            Log.d(TAG, "File content:\n" + content.toString());
        } catch (IOException e) {
            Log.e(TAG, "Failed to read file: " + e.getMessage());
            Toast.makeText(this, "Error reading data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACCESSIBILITY_PERMISSION_REQUEST_CODE) {
            updateStatus();
            if (isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Browser Monitor Service enabled", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Browser Monitor Service not enabled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }
}