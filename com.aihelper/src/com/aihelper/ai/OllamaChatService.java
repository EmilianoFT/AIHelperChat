package com.aihelper.ai;

import com.aihelper.ai.util.JsonHelper;
import com.aihelper.preferences.CredentialsService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OllamaChatService implements AiChatService {

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";

    private String model = "llama3.1:8b";
    private final HttpClient client = HttpClient.newHttpClient();

    @Override
    public Runnable sendMessageStreaming(
            String prompt,
            String context,
            Consumer<String> onChunk,
            Consumer<Throwable> onError,
            Runnable onComplete) {

        try {
            String generateUrl = endpoint("/api/generate");
            String payload = """
            {
              "model": "%s",
              "prompt": "%s\\n\\n%s",
              "stream": true
            }
            """.formatted(
                    model,
                    JsonHelper.escape(context),
                    JsonHelper.escape(prompt)
            );

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(generateUrl))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            var future = client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 400) {
                            String errorBody = safeCollect(response.body());
                            onError.accept(new IllegalStateException(
                                    "Ollama respondió con estado " + response.statusCode()
                                            + messageSuffix(errorBody)));
                            return;
                        }

                        try (Stream<String> lines = response.body()) {
                            lines.forEach(line -> {
                                if (line.contains("\"response\"")) {
                                    String chunk = JsonHelper.extractJsonValue(line, "response");
                                    if (chunk != null) {
                                        onChunk.accept(chunk);
                                    }
                                }
                            });
                        }
                    })
                    .whenComplete((r, ex) -> {
                        if (ex != null) {
                            onError.accept(ex);
                        }
                        onComplete.run();
                    });

            return () -> future.cancel(true);

        } catch (Exception e) {
            onError.accept(e);
            onComplete.run();
            return () -> {};
        }
    }

    private String safeCollect(Stream<String> lines) {
        if (lines == null) return "";
        try (Stream<String> closeable = lines) {
            return closeable.collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "";
        }
    }

    private String messageSuffix(String errorBody) {
        if (errorBody == null || errorBody.isBlank()) {
            return "";
        }
        return ": " + errorBody;
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
    public List<String> listModels() {
        try {
            String tagsUrl = endpoint("/api/tags");
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tagsUrl))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            return JsonHelper.extractArrayField(response.body(), "name");

        } catch (Exception e) {
            return List.of(model);
        }
    }

    @Override
    public void setModel(String model) {
        this.model = model;
    }

    private String endpoint(String path) {
        String base = trimToNull(CredentialsService.getOllamaBaseUrl());
        String effectiveBase = base == null ? DEFAULT_BASE_URL : base;
        String sanitizedBase = stripTrailingSlash(effectiveBase);
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return sanitizedBase + normalizedPath;
    }

    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result.isEmpty() ? DEFAULT_BASE_URL : result;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
