package com.aihelper.ai;

import java.util.List;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.io.InputStreamReader;
import java.io.BufferedReader;

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
                "gpt-4o"
        );
        try {
            URL url = URI.create(apiUrl() + "/v1/models").toURL();
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
            return models;
        } catch (Exception e) {
            return fallback;
        }
    }
}