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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BrowserMonitorService extends AccessibilityService {

    private static final String TAG = "BrowserMonitorService";
    private static final String DATA_FILE = "browser_data.txt";

    private static final Set<String> BROWSER_PACKAGES = new HashSet<>(Arrays.asList(
            "com.android.chrome", "org.mozilla.firefox", "com.opera.browser", "com.brave.browser",
            "com.microsoft.emmx", "com.sec.android.app.sbrowser"
    ));

    private static final long SCRAPE_INTERVAL_MS = 10 * 1000; // 10 seconds
    private long lastScrapeTime = 0;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final int MAX_SCRAPE_RETRIES = 3;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED |
                AccessibilityEvent.TYPE_VIEW_FOCUSED |
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.packageNames = null;
        info.flags = AccessibilityServiceInfo.DEFAULT;
        setServiceInfo(info);

        Log.d(TAG, "Service connected.");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
        Log.d(TAG, "Event received from: " + packageName + " | Type: " + event.getEventType());

        if (!BROWSER_PACKAGES.contains(packageName)) {
            Log.d(TAG, "Ignored package: " + packageName);
            return;
        }

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            Log.w(TAG, "Root node is null.");
            return;
        }

        traverseNode(rootNode);
    }

    private void traverseNode(AccessibilityNodeInfo node) {
        if (node == null) return;

        CharSequence text = node.getText();
        if (text != null) {
            processBrowsingData(text.toString());
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            traverseNode(node.getChild(i));
        }
    }

    private void processBrowsingData(String data) {
        if (data == null || data.trim().isEmpty()) return;

        Log.d(TAG, "Processing data: " + data);

        boolean isSearch = !data.startsWith("http") && !data.startsWith("www");
        String type = isSearch ? "Search" : "URL";
        String entry = type + ": " + data + "\n";

        writeToFile(entry);
        Log.d(TAG, "Data written: " + entry);

        if (!isSearch && data.startsWith("http")) {
            Log.d(TAG, "Triggering scrape for URL: " + data);
            scrapeWebContent(data);
        }
    }

    private void writeToFile(String entry) {
        try (FileOutputStream fos = new FileOutputStream(new File(getFilesDir(), DATA_FILE), true)) {
            fos.write(entry.getBytes());
            Log.d(TAG, "Entry written to file.");
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to file: " + e.getMessage());
        }
    }

    private void scrapeWebContent(String url) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastScrapeTime < SCRAPE_INTERVAL_MS) {
            Log.d(TAG, "Skipping scrape due to interval limit.");
            return;
        }
        lastScrapeTime = currentTime;

        Log.d(TAG, "Starting scrape thread for: " + url);

        executor.submit(() -> {
            for (int attempt = 1; attempt <= MAX_SCRAPE_RETRIES; attempt++) {
                Log.d(TAG, "Scraping attempt " + attempt + " for: " + url);
                try {
                    Document doc = Jsoup.connect(url)
                            .userAgent("Mozilla/5.0")
                            .timeout(5000)
                            .get();
                    String title = doc.title();
                    String metaDescription = doc.selectFirst("meta[name=description]") != null ?
                            doc.selectFirst("meta[name=description]").attr("content") : "";

                    String result = "Title: " + title + "\nDescription: " + metaDescription + "\n\n";
                    writeToFile(result);

                    Log.d(TAG, "Scrape successful for: " + url);
                    return;
                } catch (IOException e) {
                    Log.e(TAG, "Scrape failed on attempt " + attempt + ": " + e.getMessage());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        Log.e(TAG, "Scrape retry sleep interrupted.");
                        return;
                    }
                }
            }
            Log.e(TAG, "Scrape failed after " + MAX_SCRAPE_RETRIES + " attempts for: " + url);
        });
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted.");
    }
}
