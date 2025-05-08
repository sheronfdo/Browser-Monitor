package com.jamith.browsermonitor;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

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
    private static final long WATCHDOG_INTERVAL_MS = 300000; // 5 minutes
    private long lastScrapeTime = 0;
    private long lastEventTime = 0;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final int MAX_SCRAPE_RETRIES = 3;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED |
                AccessibilityEvent.TYPE_VIEW_FOCUSED |
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.packageNames = null; // Monitor all packages, filter in code
        info.flags = AccessibilityServiceInfo.DEFAULT;
        info.notificationTimeout = 100;
        setServiceInfo(info);

        lastEventTime = System.currentTimeMillis();
        startWatchdog();
        Log.d(TAG, "Service connected, monitoring all packages with browser filter: " + BROWSER_PACKAGES);
    }

    private void startWatchdog() {
        executor.submit(() -> {
            while (!executor.isShutdown()) {
                try {
                    Thread.sleep(WATCHDOG_INTERVAL_MS);
                    if (System.currentTimeMillis() - lastEventTime > WATCHDOG_INTERVAL_MS) {
                        Log.w(TAG, "No events for 5 minutes, restarting service");
                        Intent intent = new Intent(this, BrowserMonitorService.class);
                        stopService(intent);
                        startService(intent);
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "Watchdog interrupted: " + e.getMessage());
                }
            }
        });
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        lastEventTime = System.currentTimeMillis();
        if (event == null) {
            Log.d(TAG, "Received null event, skipping");
            return;
        }

        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
        Log.d(TAG, "Event received from: " + packageName + " | Type: " + event.getEventType());

        if (!BROWSER_PACKAGES.contains(packageName)) {
            Log.d(TAG, "Ignored non-browser package: " + packageName);
            return;
        }

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            Log.w(TAG, "Root node is null for package: " + packageName);
            return;
        }

        try {
            traverseNode(rootNode);
        } finally {
            rootNode.recycle();
        }
    }

    private void traverseNode(AccessibilityNodeInfo node) {
        if (node == null) {
            Log.d(TAG, "Null node in traverseNode, skipping");
            return;
        }

        try {
            CharSequence text = node.getText();
            if (text != null && !text.toString().trim().isEmpty()) {
                String data = text.toString().trim();
                Log.d(TAG, "Captured node text: " + data);
                processBrowsingData(data);
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    traverseNode(child);
                    child.recycle();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error traversing node: " + e.getMessage());
        }
    }

    private void processBrowsingData(String data) {
        if (data == null || data.trim().isEmpty()) {
            Log.d(TAG, "Empty or null data, skipping");
            return;
        }

        // Skip non-URLs and search queries
        if (!data.startsWith("http") || data.contains("google.com/search")) {
            Log.d(TAG, "Skipping non-URL or search data: " + data);
            return;
        }

        Log.d(TAG, "Processing valid URL for scraping: " + data);
        scrapeWebContent(data);
    }

    private void writeToFile(String entry) {
        FileOutputStream fos = null;
        try {
            File file = new File(getFilesDir(), DATA_FILE);
            fos = new FileOutputStream(file, true);
            fos.write(entry.getBytes());
            Log.d(TAG, "Wrote to file: " + file.getAbsolutePath() + " | Entry: " + entry.trim());
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to file: " + e.getMessage());
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing file: " + e.getMessage());
                }
            }
        }
    }

    private void scrapeWebContent(String url) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastScrapeTime < SCRAPE_INTERVAL_MS) {
            Log.d(TAG, "Skipping scrape for " + url + ": too soon");
            return;
        }
        lastScrapeTime = currentTime;

        Log.d(TAG, "Starting scrape for URL: " + url);
        executor.submit(() -> {
            for (int attempt = 1; attempt <= MAX_SCRAPE_RETRIES; attempt++) {
                Log.d(TAG, "Scraping attempt " + attempt + " for: " + url);
                try {
                    Document doc = Jsoup.connect(url)
                            .userAgent("Mozilla/5.0 (Android)")
                            .timeout(5000)
                            .get();

                    String title = doc.title();
                    Log.d(TAG, "Extracted title: " + title);

                    Element paragraphElement = doc.selectFirst("p");
                    String paragraph = paragraphElement != null ?
                            paragraphElement.text().substring(0, Math.min(paragraphElement.text().length(), 200)) :
                            "No paragraph found";
                    Log.d(TAG, "Extracted paragraph: " + paragraph);

                    String entry = "Title: " + title + "\nParagraph: " + paragraph + "\n\n";
                    writeToFile(entry);
                    Log.d(TAG, "Scrape successful for " + url + ": " + entry.trim());
                    return;

                } catch (IOException e) {
                    Log.e(TAG, "Scrape failed on attempt " + attempt + " for " + url + ": " + e.getMessage());
                    if (attempt == MAX_SCRAPE_RETRIES) {
                        String entry = "Scrape Error: " + url + " | " + e.getMessage() + "\n\n";
                        writeToFile(entry);
                        Log.e(TAG, "Scrape failed after " + MAX_SCRAPE_RETRIES + " attempts: " + entry.trim());
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        Log.e(TAG, "Scrape retry sleep interrupted: " + ie.getMessage());
                        return;
                    }
                }
            }
        });
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        Log.d(TAG, "Service destroyed");
    }
}