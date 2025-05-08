package com.jamith.browsermonitor;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BrowserMonitorService extends AccessibilityService {
    private static final String TAG = "BrowserMonitorService";
    private static final List<String> BROWSER_PACKAGES = Arrays.asList(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.sec.android.app.sbrowser"
    );
    private ExecutorService executor;
    private static final String DATA_FILE = "browsing_data.txt";
    private SimpleDateFormat dateFormat;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.packageNames = BROWSER_PACKAGES.toArray(new String[0]);
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 100;
        setServiceInfo(info);

        executor = Executors.newSingleThreadExecutor();
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Log.d(TAG, "BrowserMonitorService connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getSource() == null) return;

        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
        if (!BROWSER_PACKAGES.contains(packageName)) return;

        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
                String text = event.getText() != null ? event.getText().toString() : "";
                if (text.contains("google.com/search") || text.startsWith("http")) {
                    Log.d(TAG, "Captured text: " + text);
                    processBrowsingData(text);
                }
                break;
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                AccessibilityNodeInfo source = event.getSource();
                if (source != null) {
                    List<AccessibilityNodeInfo> urlNodes = source.findAccessibilityNodeInfosByViewId(packageName + ":id/url_bar");
                    if (!urlNodes.isEmpty()) {
                        String url = urlNodes.get(0).getText() != null ? urlNodes.get(0).getText().toString() : "";
                        if (!url.isEmpty() && url.startsWith("http")) {
                            Log.d(TAG, "Captured URL: " + url);
                            processBrowsingData(url);
                        }
                    }
                }
                break;
        }
    }

    private void processBrowsingData(String data) {
        boolean isSearch = data.contains("google.com/search");
        String type = isSearch ? "search" : "url";
        String url = isSearch ? extractSearchUrl(data) : data;

        // Save to local file
        String timestamp = dateFormat.format(new Date());
        String entry = String.format("%s | %s | %s\n", timestamp, type, data);
        writeToFile(entry);

        if (!isSearch && data.startsWith("http")) {
            scrapeWebContent(data);
        }
    }

    private String extractSearchUrl(String data) {
        String query = data.contains("q=") ? data.substring(data.indexOf("q=") + 2) : "";
        if (query.contains("&")) {
            query = query.substring(0, query.indexOf("&"));
        }
        return "https://www.google.com/search?q=" + query;
    }

    private void scrapeWebContent(String url) {
        executor.submit(() -> {
            try {
                Document doc = Jsoup.connect(url).get();
                String title = doc.select("title").text();
                String paragraphs = doc.select("p").stream()
                        .limit(3)
                        .map(element -> element.text())
                        .collect(java.util.stream.Collectors.joining(" "));

                String timestamp = dateFormat.format(new Date());
                String entry = String.format("%s | scraped | URL: %s | Title: %s | Content: %s\n", timestamp, url, title, paragraphs);
                writeToFile(entry);
            } catch (IOException e) {
                Log.e(TAG, "Scraping failed for " + url + ": " + e.getMessage());
                String timestamp = dateFormat.format(new Date());
                String entry = String.format("%s | scraped_error | URL: %s | Error: %s\n", timestamp, url, e.getMessage());
                writeToFile(entry);
            }
        });
    }

    private void writeToFile(String entry) {
        try {
            File file = new File(getFilesDir(), DATA_FILE);
            FileOutputStream fos = new FileOutputStream(file, true); // Append mode
            fos.write(entry.getBytes());
            fos.close();
            Log.d(TAG, "Wrote to file: " + entry);
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to file: " + e.getMessage());
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "BrowserMonitorService interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
        Log.d(TAG, "BrowserMonitorService destroyed");
    }
}