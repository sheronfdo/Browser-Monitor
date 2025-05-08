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
    private long lastScrapeTime = 0;
    private static final long SCRAPE_INTERVAL_MS = 60000;
    private static final int MAX_RECURSION_DEPTH = 10;
    private static final int MAX_SCRAPE_RETRIES = 3;
    private static final int SCRAPE_TIMEOUT_MS = 15000;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED |
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED |
                AccessibilityEvent.TYPE_VIEW_FOCUSED;
        info.packageNames = BROWSER_PACKAGES.toArray(new String[0]);
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 100;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);

        executor = Executors.newSingleThreadExecutor();
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Log.d(TAG, "BrowserMonitorService connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
        if (!BROWSER_PACKAGES.contains(packageName)) return;

        try {
            switch (event.getEventType()) {
                case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
                case AccessibilityEvent.TYPE_VIEW_FOCUSED:
                    String text = event.getText() != null ? event.getText().toString() : "";
                    if (text.contains("google.com/search") || text.startsWith("http")) {
                        processBrowsingData(text);
                    }
                    break;

                case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                    AccessibilityNodeInfo source = event.getSource();
                    if (source == null) return;

                    try {
                        String url = findUrlFromNode(source, packageName);
                        if (url != null) {
                            processBrowsingData(url);
                        }
                    } finally {
                        source.recycle();
                    }
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing event: " + e.getMessage(), e);
        }
    }

    private String findUrlFromNode(AccessibilityNodeInfo source, String packageName) {
        String[] viewIds = {":id/url_bar", ":id/address_bar", ":id/location_bar"};
        for (String id : viewIds) {
            List<AccessibilityNodeInfo> nodes = source.findAccessibilityNodeInfosByViewId(packageName + id);
            if (!nodes.isEmpty()) {
                String url = nodes.get(0).getText() != null ? nodes.get(0).getText().toString() : "";
                if (!url.isEmpty() && url.startsWith("http")) {
                    return url;
                }
            }
        }

        findUrlInNode(source);
        return null;
    }

    private void findUrlInNode(AccessibilityNodeInfo node) {
        findUrlInNodeRecursive(node, 0);
    }

    private void findUrlInNodeRecursive(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > MAX_RECURSION_DEPTH) return;

        try {
            if (node.isEditable() && node.getText() != null) {
                String text = node.getText().toString();
                if (text.startsWith("http") || text.contains("google.com/search")) {
                    processBrowsingData(text);
                }
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    findUrlInNodeRecursive(child, depth + 1);
                    child.recycle();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in findUrlInNode: " + e.getMessage());
        }
    }

    private void processBrowsingData(String data) {
        if (data == null || data.trim().isEmpty()) return;

        try {
            boolean isSearch = data.contains("google.com/search");
            String type = isSearch ? "search" : "url";
            String url = isSearch ? extractSearchUrl(data) : data;
            String timestamp = dateFormat.format(new Date());
            String entry = String.format("%s | %s | %s\n", timestamp, type, data);
            writeToFile(entry);

            if (!isSearch && data.startsWith("http")) {
                scrapeWebContent(data);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing data: " + e.getMessage());
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
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastScrapeTime < SCRAPE_INTERVAL_MS) return;
        lastScrapeTime = currentTime;

        executor.submit(() -> {
            for (int attempt = 1; attempt <= MAX_SCRAPE_RETRIES; attempt++) {
                try {
                    Document doc = Jsoup.connect(url)
                            .timeout(SCRAPE_TIMEOUT_MS)
                            .userAgent("Mozilla/5.0")
                            .get();

                    String title = doc.title();
                    String content = doc.select("p").stream()
                            .limit(3)
                            .map(p -> p.text())
                            .collect(java.util.stream.Collectors.joining(" "));
                    String timestamp = dateFormat.format(new Date());
                    String entry = String.format("%s | scraped | URL: %s | Title: %s | Content: %s\n",
                            timestamp, url, title, content);
                    writeToFile(entry);
                    return;
                } catch (IOException e) {
                    if (attempt == MAX_SCRAPE_RETRIES) {
                        String entry = String.format("%s | scrape_failed | %s | Error: %s\n",
                                dateFormat.format(new Date()), url, e.getMessage());
                        writeToFile(entry);
                    }
                    try {
                        Thread.sleep(1000 * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }

    private void writeToFile(String entry) {
        try (FileOutputStream fos = new FileOutputStream(new File(getFilesDir(), DATA_FILE), true)) {
            fos.write(entry.getBytes());
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to file: " + e.getMessage());
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }
}
