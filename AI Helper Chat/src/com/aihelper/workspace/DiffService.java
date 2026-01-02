package com.aihelper.workspace;

public class DiffService {

    public String diff(String original, String modified) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ORIGINAL ===\n")
          .append(original)
          .append("\n\n=== MODIFIED ===\n")
          .append(modified);
        return sb.toString();
    }
}
