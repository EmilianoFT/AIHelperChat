package com.aihelper.ai;

import java.util.List;
import java.util.function.Consumer;

public interface AiChatService {
    String sendMessage(String prompt, String context);

    /**
     * Inicia un streaming y devuelve un manejador de cancelación. El manejador
     * debe ser seguro de invocar múltiples veces.
     */
    Runnable sendMessageStreaming(
            String prompt,
            String context,
            Consumer<String> onChunk,
            Consumer<Throwable> onError,
            Runnable onComplete
        );

	void setModel(String model);
	List<String> listModels();
}
