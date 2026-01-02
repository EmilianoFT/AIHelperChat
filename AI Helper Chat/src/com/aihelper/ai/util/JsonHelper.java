package com.aihelper.ai.util;

import java.util.ArrayList;
import java.util.List;

public final class JsonHelper {

    private JsonHelper() {}

    public static String escape(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
            .replace("\t", "\\t")
                .replace("\r", "");
    }

    public static String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\":";
        int index = json.indexOf(pattern);
        if (index == -1) return null;

        int start = json.indexOf("\"", index + pattern.length()) + 1;
        int end = json.indexOf("\"", start);
        if (start < 0 || end < 0) return null;

        return json.substring(start, end);
    }

    public static List<String> extractArrayField(String json, String key) {
        List<String> values = new ArrayList<>();
        String[] parts = json.split("\\{");

        for (String part : parts) {
            if (part.contains("\"" + key + "\"")) {
                String value = extractJsonValue("{" + part, key);
                if (value != null) {
                    values.add(value);
                }
            }
        }
        return values;
    }
}

