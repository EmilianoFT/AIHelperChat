package com.aihelper.ai;

import java.util.List;

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
        return List.of(
                CredentialsService.getOpenAiDefaultModel(),
                "gpt-4o-mini",
                "gpt-4.1-mini",
                "gpt-4o"
        );
    }
}
