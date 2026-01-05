package com.aihelper.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;

/**
 * Minimal Markdown renderer that applies simple styling to a StyledText widget.
 * It intentionally supports a limited subset of Markdown that aligns with the
 * responses returned by common LLM providers (headings, bullet lists, bold and
 * italic text, and horizontal rules).
 */
public final class MarkdownRenderer {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern HORIZONTAL_RULE = Pattern.compile("^\\s*([-*_])\\1{2,}\\s*$");
    private static final Pattern NUMBERED_LIST = Pattern.compile("^\\d+\\.\\s+(.*)$");

    public void append(StyledText target, String markdown) {
        if (target == null || target.isDisposed()) return;
        if (markdown == null || markdown.isEmpty()) return;

        String normalized = normalize(markdown);
        if (normalized.isEmpty()) return;

        int baseOffset = target.getCharCount();
        Color codeBackground = target.getDisplay().getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW);
        Font monoFont = JFaceResources.getFont(JFaceResources.TEXT_FONT);
        StringBuilder builder = new StringBuilder();
        List<StyleRange> ranges = new ArrayList<>();

        renderBlocks(normalized, builder, ranges, baseOffset, codeBackground, monoFont);

        String rendered = builder.toString();
        if (rendered.isEmpty()) return;

        target.append(rendered);
        for (StyleRange range : ranges) {
            if (range.length > 0) {
                target.setStyleRange(range);
            }
        }
    }

    private String normalize(String text) {
        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\\t", "\t");
    }

    private void renderBlocks(String text, StringBuilder builder, List<StyleRange> ranges, int baseOffset, Color codeBackground, Font monoFont) {
        String[] lines = text.split("\n", -1);

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                builder.append('\n');
                continue;
            }

            if (HORIZONTAL_RULE.matcher(trimmed).matches()) {
                builder.append("\n────────────────────────\n");
                continue;
            }

            Matcher headingMatcher = HEADING.matcher(trimmed);
            if (headingMatcher.matches()) {
                String content = headingMatcher.group(2).trim();
                int start = builder.length();
                appendInline(content, builder, ranges, baseOffset, codeBackground, monoFont);
                int length = builder.length() - start;
                if (length > 0) {
                    StyleRange headingRange = new StyleRange();
                    headingRange.start = baseOffset + start;
                    headingRange.length = length;
                    headingRange.fontStyle = SWT.BOLD;
                    ranges.add(headingRange);
                }
                builder.append('\n');
                continue;
            }

            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                builder.append('\u2022').append(' ');
                appendInline(trimmed.substring(2).trim(), builder, ranges, baseOffset, codeBackground, monoFont);
                builder.append('\n');
                continue;
            }

            Matcher numbered = NUMBERED_LIST.matcher(trimmed);
            if (numbered.matches()) {
                builder.append(numberedGroupPrefix(trimmed)).append(' ');
                appendInline(numbered.group(1).trim(), builder, ranges, baseOffset, codeBackground, monoFont);
                builder.append('\n');
                continue;
            }

            if (trimmed.startsWith(">")) {
                builder.append("> ");
                appendInline(trimmed.substring(1).trim(), builder, ranges, baseOffset, codeBackground, monoFont);
                builder.append('\n');
                continue;
            }

            appendInline(line, builder, ranges, baseOffset, codeBackground, monoFont);
            builder.append('\n');
        }
    }

    private void appendInline(String text, StringBuilder builder, List<StyleRange> ranges, int baseOffset, Color codeBackground, Font monoFont) {
        if (text == null || text.isEmpty()) return;

        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) == '`') {
                int end = text.indexOf('`', i + 1);
                if (end == -1) {
                    builder.append(text.substring(i));
                    break;
                }
                String inner = text.substring(i + 1, end);
                int start = builder.length();
                builder.append(inner);
                int length = builder.length() - start;
                if (length > 0) {
                    StyleRange code = new StyleRange();
                    code.start = baseOffset + start;
                    code.length = length;
                    code.font = monoFont;
                    code.background = codeBackground;
                    ranges.add(code);
                }
                i = end + 1;
                continue;
            }

            if (text.startsWith("**", i)) {
                int end = text.indexOf("**", i + 2);
                if (end == -1) {
                    builder.append(text.substring(i));
                    break;
                }
                String inner = text.substring(i + 2, end);
                int start = builder.length();
                appendInline(inner, builder, ranges, baseOffset, codeBackground, monoFont);
                int length = builder.length() - start;
                if (length > 0) {
                    StyleRange bold = new StyleRange();
                    bold.start = baseOffset + start;
                    bold.length = length;
                    bold.fontStyle = SWT.BOLD;
                    ranges.add(bold);
                }
                i = end + 2;
                continue;
            }

            if (text.startsWith("_", i)) {
                int end = text.indexOf('_', i + 1);
                if (end == -1) {
                    builder.append(text.substring(i));
                    break;
                }
                String inner = text.substring(i + 1, end);
                int start = builder.length();
                appendInline(inner, builder, ranges, baseOffset, codeBackground, monoFont);
                int length = builder.length() - start;
                if (length > 0) {
                    StyleRange italic = new StyleRange();
                    italic.start = baseOffset + start;
                    italic.length = length;
                    italic.fontStyle = SWT.ITALIC;
                    ranges.add(italic);
                }
                i = end + 1;
                continue;
            }

            builder.append(text.charAt(i));
            i++;
        }
    }

    private String numberedGroupPrefix(String line) {
        int idx = line.indexOf('.');
        if (idx <= 0) {
            return "1.";
        }
        return line.substring(0, idx + 1);
    }
}
