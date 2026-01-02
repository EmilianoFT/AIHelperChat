package com.aihelper.ai;

import java.util.List;

import com.aihelper.preferences.CredentialsService;

public class QwenChatService extends OpenAiCompatibleChatService {

    public QwenChatService() {
        super(CredentialsService.getQwenDefaultModel());
    }

    @Override
    protected String providerName() {
        return "Qwen";
    }

    @Override
    protected String apiKey() {
        return CredentialsService.getQwenApiKey();
    }

    @Override
    protected String apiUrl() {
        return CredentialsService.getQwenBaseUrl();
    }

    @Override
    protected List<String> catalogModels() {
        return List.of(
                CredentialsService.getQwenDefaultModel(),
                "qwen-plus",
                "qwen-max"
        );
    }
}
