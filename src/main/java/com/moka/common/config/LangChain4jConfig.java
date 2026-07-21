package com.moka.common.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

@Configuration
public class LangChain4jConfig {

    /**
     * DeepSeek 直连，用于文本推理（ExperienceUnderstanding / ConversationPlanner）。
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "moka.llm.mock", havingValue = "false", matchIfMissing = true)
    public ChatLanguageModel deepseekChatModel(
            @Value("${moka.deepseek.api-key}") String apiKey,
            @Value("${moka.deepseek.base-url}") String baseUrl,
            @Value("${moka.deepseek.text-model}") String modelName
    ) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.7)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    /**
     * OpenRouter 视觉模型，用于 OrderUnderstandingAgent 解析小票照片。
     */
    @Bean
    @Qualifier("visionChatModel")
    @ConditionalOnProperty(name = "moka.llm.mock", havingValue = "false", matchIfMissing = true)
    public ChatLanguageModel visionChatModel(
            @Value("${moka.openrouter.api-key}") String apiKey,
            @Value("${moka.openrouter.base-url}") String baseUrl,
            @Value("${moka.openrouter.vision-model}") String modelName
    ) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.1)
                .maxTokens(2000)
                .timeout(Duration.ofSeconds(60))
                .build();
    }
}
