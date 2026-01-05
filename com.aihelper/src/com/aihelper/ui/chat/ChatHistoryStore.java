package com.aihelper.ui.chat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.eclipse.core.runtime.IPath;

import com.aihelper.Activator;
import com.aihelper.model.ChatMessage;

/**
 * Persiste el historial de chat en el directorio de estado del plugin.
 * Formato simple por línea: role|base64(content)
 */
public class ChatHistoryStore {

    private static final String FILE_NAME = "chat-history";

    public List<ChatMessage> load(String project, int maxEntries) {
        List<ChatMessage> messages = new ArrayList<>();
        File file = resolveFile(project);
        if (file == null || !file.exists()) {
            return messages;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int sep = line.indexOf('|');
                if (sep <= 0) continue;
                String role = line.substring(0, sep);
                String encoded = line.substring(sep + 1);
                String content = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
                messages.add(new ChatMessage(role, content));
                if (messages.size() >= maxEntries) {
                    break;
                }
            }
        } catch (Exception ignored) {
        }

        return messages;
    }

    public void save(String project, List<ChatMessage> history, int limit) {
        if (history == null || history.isEmpty()) return;
        File file = resolveFile(project);
        if (file == null) return;

        int start = Math.max(0, history.size() - limit);
        List<ChatMessage> tail = history.subList(start, history.size());

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8, false))) {
            for (ChatMessage msg : tail) {
                String role = msg.getRole() == null ? "" : msg.getRole();
                String content = msg.getContent() == null ? "" : msg.getContent();
                String encoded = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
                writer.write(role + "|" + encoded);
                writer.newLine();
            }
        } catch (Exception ignored) {
        }
    }

    public void clear(String project) {
        File file = resolveFile(project);
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    private File resolveFile(String project) {
        Activator activator = Activator.getDefault();
        if (activator == null) return null;
        IPath state = activator.getStateLocation();
        if (state == null) return null;
        String suffix = (project == null || project.isBlank()) ? "global" : project;
        String safe = suffix.replaceAll("[^A-Za-z0-9._-]", "_");
        return state.append(FILE_NAME + "-" + safe + ".txt").toFile();
    }
}