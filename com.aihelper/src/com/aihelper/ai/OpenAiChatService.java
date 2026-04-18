package com.aihelper.ai;

import java.util.List;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.LinkedHashSet;

import com.aihelper.ai.util.JsonHelper;
import com.aihelper.preferences.CredentialsService;

public class OpenAiChatService extends OpenAiCompatibleChatService {

    public OpenAiChatService() {
        super(CredentialsService.getOpenAiDefaultModel());
    }

    @Override
    protected String providerName() {
        return "OpenAI";
    }

    @Override
    protected String apiKey() {
        return CredentialsService.getOpenAiApiKey();
    }

    @Override
    protected String apiUrl() {
        return CredentialsService.getOpenAiBaseUrl();
    }

    @Override
    protected List<String> catalogModels() {
        List<String> fallback = List.of(
                CredentialsService.getOpenAiDefaultModel(),
                "gpt-4o-mini",
                "gpt-4.1-mini",
                "gpt-4o",
                "gpt-3.5-turbo"
        );
        try {
            String base = apiUrl();
            if (base == null || base.isBlank()) {
                return fallback;
            }
            String normalized = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
            String modelsPath = normalized.endsWith("/v1") ? "/models" : "/v1/models";
            URL url = URI.create(normalized + modelsPath).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey());
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int status = conn.getResponseCode();
            if (status != 200) return fallback;
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            String json = sb.toString();
            // Extraer los ids de los modelos usando JsonHelper
            List<String> models = JsonHelper.extractArrayField(json, "id");
            if (models.isEmpty()) return fallback;

            LinkedHashSet<String> ordered = new LinkedHashSet<>();
            for (String preferred : fallback) {
                if (preferred != null && !preferred.isBlank()) {
                    ordered.add(preferred);
                }
            }
            for (String model : models) {
                if (model != null && !model.isBlank()) {
                    ordered.add(model);
                }
            }
            return List.copyOf(ordered);
        } catch (Exception e) {
            return fallback;
        }
    }
}