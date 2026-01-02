package com.aihelper.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import com.aihelper.ai.util.JsonHelper;
import com.aihelper.preferences.CredentialsService;

public class GeminiChatService implements AiChatService {

    private final HttpClient client = HttpClient.newHttpClient();
    private String userModel;

    @Override
    public void sendMessageStreaming(
            String prompt,
            String context,
            Consumer<String> onChunk,
            Consumer<Throwable> onError,
            Runnable onComplete) {

        String apiKey = trimToNull(CredentialsService.getGeminiApiKey());
        if (apiKey == null) {
            onError.accept(new IllegalStateException("Configura la API Key de Gemini"));
            onComplete.run();
            return;
        }

        String base = trimToNull(CredentialsService.getGeminiBaseUrl());
        if (base == null) {
            onError.accept(new IllegalStateException("Configura la base URL de Gemini"));
            onComplete.run();
            return;
        }

        String chatModel = effectiveModel();
        if (chatModel == null) {
            onError.accept(new IllegalStateException("Configura el modelo por defecto de Gemini"));
            onComplete.run();
            return;
        }

        try {
            String normalizedBase = base.endsWith("/") ? base : base + "/";
            String url = normalizedBase + "models/" + chatModel + ":generateContent?key=" + apiKey;

            String payload = """
            {
              "contents": [
                {
                  "role": "user",
                  "parts": [
                    {"text": "%s\n\n%s"}
                  ]
                }
              ]
            }
            """.formatted(
                        JsonHelper.escape(context),
                        JsonHelper.escape(prompt)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 400) {
                            onError.accept(new IllegalStateException(
                                    "Gemini respondió con estado " + response.statusCode()
                                            + ": " + response.body()));
                            return;
                        }
                        String text = extractText(response.body());
                        if (!text.isBlank()) {
                            onChunk.accept(text);
                        }
                    })
                    .whenComplete((r, ex) -> {
                        if (ex != null) {
                            onError.accept(ex);
                        }
                        onComplete.run();
                    });

        } catch (Exception e) {
            onError.accept(e);
            onComplete.run();
        }
    }

    private String extractText(String json) {
        if (json == null || json.isEmpty()) {
            return "";
        }

        String marker = "\"text\"";
        int index = 0;
        StringBuilder full = new StringBuilder();

        while ((index = json.indexOf(marker, index)) != -1) {
            int startQuote = json.indexOf('"', index + marker.length());
            if (startQuote == -1) break;
            startQuote++; // move past quote

            StringBuilder chunk = new StringBuilder();
            boolean escaping = false;

            for (int i = startQuote; i < json.length(); i++) {
                char c = json.charAt(i);
                if (escaping) {
                    switch (c) {
                        case 'n' -> chunk.append('\n');
                        case 't' -> chunk.append('\t');
                        case 'r' -> { /* omit */ }
                        case '"' -> chunk.append('"');
                        case '\\' -> chunk.append('\\');
                        default -> chunk.append(c);
                    }
                    escaping = false;
                    continue;
                }

                if (c == '\\') {
                    escaping = true;
                    continue;
                }

                if (c == '"') {
                    index = i + 1;
                    break;
                }

                chunk.append(c);
            }

            if (chunk.length() > 0) {
                if (full.length() > 0) {
                    full.append('\n');
                }
                full.append(chunk);
            }
        }

        return full.toString();
    }

    @Override
    public String sendMessage(String prompt, String context) {
        StringBuilder result = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);

        sendMessageStreaming(
                prompt,
                context,
                result::append,
                e -> {
                    result.append("\nERROR: ").append(e.getMessage());
                    latch.countDown();
                },
                latch::countDown
        );

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return result.toString();
    }

    @Override
    public void setModel(String model) {
        this.userModel = trimToNull(model);
    }

    @Override
    public List<String> listModels() {
        Set<String> ordered = new LinkedHashSet<>();
        String defaultModel = trimToNull(CredentialsService.getGeminiDefaultModel());
        if (defaultModel != null) {
            ordered.add(defaultModel);
        }
        ordered.add("gemini-1.5-flash");
        ordered.add("gemini-1.5-pro");
        String current = effectiveModel();
        if (current != null) {
            ordered.add(current);
        }
        return new ArrayList<>(ordered);
    }

    private String effectiveModel() {
        String override = trimToNull(userModel);
        if (override != null) {
            return override;
        }
        return trimToNull(CredentialsService.getGeminiDefaultModel());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
