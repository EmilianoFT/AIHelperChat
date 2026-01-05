package com.aihelper.ui.chat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normaliza y detecta bloques de código encerrados en ``` para que puedan
 * renderizarse correctamente en el chat.
 */
public final class CodeFenceHelper {

    private static final Pattern LEGACY_FENCE = Pattern.compile(
        "(?im)(^|\n)[ \t]*([A-Za-z0-9_+#.-]+)[ \t]*(?:\r?\n)?```"
    );

    private static final Pattern LOOSE_FENCE = Pattern.compile(
        "(?im)```[ \t]*([A-Za-z0-9_+#.-]+)[ \t]*(?:\r?\n)?"
    );

    private static final Pattern TILDE_FENCE = Pattern.compile(
        "(?m)^[ \t]*~~~[ \t]*([A-Za-z0-9_+#.-]+)?[ \t]*$"
    );

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
        "```[ \t]*([A-Za-z0-9_+#.-]+)?[ \t]*(?:\r?\n|$)([\\s\\S]*?)```[ \t]*(?:\r?\n|$)",
        Pattern.MULTILINE
    );

    private CodeFenceHelper() {
    }

    public static String normalizeFenceSyntax(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String working = text
            .replace("´´´", "```")
            .replaceAll("(?m)^\t+", "    ");

        Matcher tilde = TILDE_FENCE.matcher(working);
        StringBuffer tildeFixed = new StringBuffer();
        while (tilde.find()) {
            String lang = tilde.group(1);
            tilde.appendReplacement(tildeFixed, Matcher.quoteReplacement("```" + (lang == null ? "" : lang) + "\n"));
        }
        tilde.appendTail(tildeFixed);
        working = tildeFixed.toString();

        Matcher matcher = LEGACY_FENCE.matcher(working);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String prefix = matcher.group(1);
            if (prefix == null) prefix = "";
            String lang = matcher.group(2);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(prefix + "```" + lang + "\n"));
        }
        matcher.appendTail(sb);

        matcher = LOOSE_FENCE.matcher(sb.toString());
        StringBuffer normalized = new StringBuffer();
        while (matcher.find()) {
            String lang = matcher.group(1);
            matcher.appendReplacement(normalized, Matcher.quoteReplacement("```" + lang + "\n"));
        }
        matcher.appendTail(normalized);
        String finalText = normalized.toString();

        // Cierra fences huérfanos si se abrió sin cierre
        int fenceCount = 0;
        Matcher fenceCounter = Pattern.compile("```").matcher(finalText);
        while (fenceCounter.find()) {
            fenceCount++;
        }

        if ((fenceCount % 2) != 0) {
            finalText = finalText + "\n```";
        }

        return finalText;
    }

    public static Matcher codeBlockMatcher(String normalizedText) {
        return CODE_BLOCK_PATTERN.matcher(normalizedText);
    }
}
