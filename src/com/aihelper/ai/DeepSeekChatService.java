package com.aihelper.ai;

import java.util.List;

import com.aihelper.preferences.CredentialsService;

public class DeepSeekChatService extends OpenAiCompatibleChatService {

    public DeepSeekChatService() {
        super(CredentialsService.getDeepSeekDefaultModel());
    }

    @Override
    protected String providerName() {
        return "DeepSeek";
    }

    @Override
    protected String apiKey() {
        return CredentialsService.getDeepSeekApiKey();
    }

    @Override
    protected String apiUrl() {
        return CredentialsService.getDeepSeekBaseUrl();
    }

    @Override
    protected List<String> catalogModels() {
        return List.of(
                CredentialsService.getDeepSeekDefaultModel(),
                "deepseek-chat",
                "deepseek-coder"
        );
    }
}
