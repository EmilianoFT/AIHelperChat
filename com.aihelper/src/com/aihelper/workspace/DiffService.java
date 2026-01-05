package com.aihelper.workspace;

public class DiffService {

    public String diff(String original, String modified) {
    StringBuilder sb = new StringBuilder();
    sb.append("--- original\n");
    sb.append("+++ modified\n");

    String[] origLines = safeLines(original);
    String[] modLines = safeLines(modified);
    int max = Math.max(origLines.length, modLines.length);

    for (int i = 0; i < max; i++) {
      String o = i < origLines.length ? origLines[i] : "";
      String m = i < modLines.length ? modLines[i] : "";
      if (o.equals(m)) {
        sb.append("  ").append(o).append('\n');
      } else {
        if (!o.isEmpty()) sb.append("- ").append(o).append('\n');
        if (!m.isEmpty()) sb.append("+ ").append(m).append('\n');
      }
    }
    return sb.toString();
    }

  private String[] safeLines(String text) {
    if (text == null) return new String[0];
    return text.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
  }
}
