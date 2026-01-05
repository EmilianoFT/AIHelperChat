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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.aihelper.ai.util.JsonHelper;

public abstract class OpenAiCompatibleChatService implements AiChatService {

    private final HttpClient client = HttpClient.newHttpClient();
    private final String defaultModel;
    private String model;

    protected OpenAiCompatibleChatService(String defaultModel) {
        this.defaultModel = trimToNull(defaultModel);
        this.model = this.defaultModel;
    }

    protected abstract String providerName();
    protected abstract String apiKey();
    protected abstract String apiUrl();
    protected List<String> catalogModels() {
        String effective = effectiveModel();
        return effective == null ? List.of() : List.of(effective);
    }
    protected String completionsPath() { return "/chat/completions"; }

    @Override
    public Runnable sendMessageStreaming(
            String prompt,
            String context,
            Consumer<String> onChunk,
            Consumer<Throwable> onError,
            Runnable onComplete) {

        String key = trimToNull(apiKey());
        if (key == null) {
            onError.accept(new IllegalStateException("Configura la API Key de " + providerName()));
            onComplete.run();
            return () -> {};
        }

        String endpoint = buildChatEndpoint();
        if (endpoint == null) {
            onError.accept(new IllegalStateException("Configura la base URL de " + providerName()));
            onComplete.run();
            return () -> {};
        }

        String chatModel = effectiveModel();
        if (chatModel == null) {
            onError.accept(new IllegalStateException("Configura el modelo por defecto de " + providerName()));
            onComplete.run();
            return () -> {};
        }

                try {
                        String payload = """
                        {
                            "model": "%s",
                            "stream": true,
                            "messages": [
                                {"role":"system","content":"%s"},
                                {"role":"user","content":"%s"}
                            ]
                        }
                        """.formatted(
                                        chatModel,
                                        JsonHelper.escape(context),
                                        JsonHelper.escape(prompt)
                        );

                        HttpRequest request = HttpRequest.newBuilder()
                                        .uri(URI.create(endpoint))
                                        .header("Authorization", "Bearer " + key)
                                        .header("Content-Type", "application/json")
                                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                                        .build();

            var future = client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 400) {
                            String errorBody = safeCollect(response.body());
                            onError.accept(new IllegalStateException(
                                    providerName() + " respondió con estado " + response.statusCode()
                                            + messageSuffix(errorBody)));
                            return;
                        }

                        try (Stream<String> lines = response.body()) {
                            lines.forEach(line -> {
                                if (!line.startsWith("data: ") || line.contains("[DONE]")) {
                                    return;
                                }
                                List<String> contents = JsonHelper.extractAllValues(line, "content");
                                for (String c : contents) {
                                    if (c != null && !c.isEmpty()) {
                                        onChunk.accept(c);
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
        String trimmed = trimToNull(model);
        this.model = trimmed == null ? defaultModel : trimmed;
    }

    @Override
    public List<String> listModels() {
        Set<String> ordered = new LinkedHashSet<>();
        for (String candidate : catalogModels()) {
            if (candidate != null && !candidate.isBlank()) {
                ordered.add(candidate);
            }
        }
        String current = effectiveModel();
        if (current != null) {
            ordered.add(current);
        }
        return new ArrayList<>(ordered);
    }

    private String effectiveModel() {
        return trimToNull(model);
    }

    private String buildChatEndpoint() {
        String base = trimToNull(apiUrl());
        if (base == null) {
            return null;
        }

        String normalizedBase = stripTrailingSlash(base);
        if (normalizedBase.isEmpty()) {
            return null;
        }
        String path = trimToNull(completionsPath());
        if (path == null) {
            return normalizedBase;
        }

        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        if (normalizedBase.endsWith(normalizedPath)) {
            return normalizedBase;
        }
        return normalizedBase + normalizedPath;
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

    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
