package com.premisave.auth.service.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small, dependency-free User-Agent reader. It is a best effort on a value the client
 * controls, so the result is only used for labels and for noticing changes.
 */
public final class UserAgentParser {

    private static final int MAX_LENGTH = 300;

    private static final Pattern WINDOWS = Pattern.compile("Windows NT (\\d+\\.\\d+)");
    private static final Pattern ANDROID = Pattern.compile("Android[ /](\\d+(?:\\.\\d+)*)");
    private static final Pattern IOS = Pattern.compile("(?:iPhone|CPU) OS (\\d+(?:[_.]\\d+)*)");
    private static final Pattern MAC = Pattern.compile("Mac OS X (\\d+(?:[_.]\\d+)*)");

    private static final Pattern EDGE = Pattern.compile("Edg(?:e|A|iOS)?/(\\d+)");
    private static final Pattern OPERA = Pattern.compile("(?:OPR|Opera)/(\\d+)");
    private static final Pattern SAMSUNG = Pattern.compile("SamsungBrowser/(\\d+)");
    private static final Pattern FIREFOX = Pattern.compile("(?:Firefox|FxiOS)/(\\d+)");
    private static final Pattern CHROME = Pattern.compile("(?:Chrome|CriOS)/(\\d+)");
    private static final Pattern SAFARI = Pattern.compile("Version/(\\d+)[^ ]* (?:Mobile/[^ ]+ )?Safari/");

    private UserAgentParser() {
    }

    /** Removes control characters and caps the length, so the value is safe to store and print. */
    public static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replaceAll("[\\p{Cntrl}]", " ").trim();
        return cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH) : cleaned;
    }

    public static DeviceInfo parse(String raw) {
        String ua = sanitize(raw);
        if (ua.isEmpty()) {
            return DeviceInfo.unknown();
        }

        String os = detectOs(ua);
        String browser = detectBrowser(ua);
        String type = detectType(ua);

        String label;
        if (browser != null && os != null) {
            label = browser + " on " + os;
        } else if (browser != null) {
            label = browser;
        } else if (os != null) {
            label = os + " device";
        } else {
            label = "Unknown device";
        }
        return new DeviceInfo(label, type, os, browser);
    }

    private static String detectOs(String ua) {
        Matcher m = WINDOWS.matcher(ua);
        if (m.find()) {
            return "Windows " + switch (m.group(1)) {
                case "10.0" -> "10/11";
                case "6.3" -> "8.1";
                case "6.2" -> "8";
                case "6.1" -> "7";
                default -> m.group(1);
            };
        }
        m = ANDROID.matcher(ua);
        if (m.find()) {
            return "Android " + m.group(1);
        }
        m = IOS.matcher(ua);
        if (m.find() && (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod"))) {
            return (ua.contains("iPad") ? "iPadOS " : "iOS ") + m.group(1).replace('_', '.');
        }
        m = MAC.matcher(ua);
        if (m.find()) {
            return "macOS " + m.group(1).replace('_', '.');
        }
        if (ua.contains("CrOS")) {
            return "ChromeOS";
        }
        if (ua.contains("Linux") || ua.contains("X11")) {
            return "Linux";
        }
        return null;
    }

    private static String detectBrowser(String ua) {
        Matcher m;
        if ((m = EDGE.matcher(ua)).find()) {
            return "Edge " + m.group(1);
        }
        if ((m = OPERA.matcher(ua)).find()) {
            return "Opera " + m.group(1);
        }
        if ((m = SAMSUNG.matcher(ua)).find()) {
            return "Samsung Internet " + m.group(1);
        }
        if ((m = FIREFOX.matcher(ua)).find()) {
            return "Firefox " + m.group(1);
        }
        if ((m = CHROME.matcher(ua)).find()) {
            return "Chrome " + m.group(1);
        }
        if ((m = SAFARI.matcher(ua)).find()) {
            return "Safari " + m.group(1);
        }
        String lower = ua.toLowerCase();
        if (lower.contains("dart/") || lower.contains("okhttp") || lower.contains("dalvik") || lower.contains("cfnetwork")) {
            return "Mobile app";
        }
        if (lower.contains("curl/") || lower.contains("postmanruntime") || lower.contains("python-requests")
                || lower.contains("insomnia") || lower.contains("httpie")) {
            return "API client";
        }
        return null;
    }

    private static String detectType(String ua) {
        String lower = ua.toLowerCase();
        if (lower.contains("ipad") || lower.contains("tablet") || (lower.contains("android") && !lower.contains("mobile"))) {
            return "TABLET";
        }
        if (lower.contains("mobi") || lower.contains("iphone") || lower.contains("android")
                || lower.contains("dart/") || lower.contains("okhttp") || lower.contains("dalvik")) {
            return "MOBILE";
        }
        if (lower.contains("windows") || lower.contains("macintosh") || lower.contains("x11")
                || lower.contains("linux") || lower.contains("cros")) {
            return "DESKTOP";
        }
        return "UNKNOWN";
    }
}
