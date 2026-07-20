package com.moka.common.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LangChain4jConfig {

    @Value("${moka.openrouter.base-url}")
    private String baseUrl;

    @Value("${moka.openrouter.api-key}")
    private String apiKey;

    @Value("${moka.openrouter.text-model}")
    private String textModel;

    @Value("${moka.openrouter.vision-model}")
    private String visionModel;

    @Value("${moka.openrouter.embedding-model}")
    private String embeddingModel;

    @Bean
    @ConditionalOnProperty(name = "moka.llm.mock", havingValue = "false", matchIfMissing = true)
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(textModel)
                .temperature(0.7)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "moka.llm.mock", havingValue = "false", matchIfMissing = true)
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(embeddingModel)
                .timeout(Duration.ofSeconds(30))
                .build();
    }
}
