package ua.beengoo.logdo2.plugin.util;

public class StringUtil {
    private StringUtil() {
        // Utility
    }

    public static String stripTrailingSlash(String s) {
        if (s == null) return null;
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
