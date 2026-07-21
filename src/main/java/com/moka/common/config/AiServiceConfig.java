package com.moka.common.config;

import com.moka.ai.agent.ConversationPlannerAgent;
import com.moka.ai.agent.ExperienceUnderstandingAgent;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI Service 代理配置。
 * <p>
 * 当 {@code moka.llm.mock=false} 时，创建真实的 @AiService 代理，
 * 使用 DeepSeek 模型进行推理。
 * <p>
 * Mock 模式下（默认），此配置不生效，AI 功能由 MockLlmService 提供。
 */
@Configuration
@ConditionalOnProperty(name = "moka.llm.mock", havingValue = "false")
public class AiServiceConfig {

    private static final Logger log = LoggerFactory.getLogger(AiServiceConfig.class);

    @Bean
    public ExperienceUnderstandingAgent experienceUnderstandingAgent(
            @Qualifier("deepseekChatModel") ChatLanguageModel model) {
        log.info("创建 ExperienceUnderstandingAgent 代理（DeepSeek）");
        return AiServices.builder(ExperienceUnderstandingAgent.class)
                .chatLanguageModel(model)
                .build();
    }

    @Bean
    public ConversationPlannerAgent conversationPlannerAgent(
            @Qualifier("deepseekChatModel") ChatLanguageModel model) {
        log.info("创建 ConversationPlannerAgent 代理（DeepSeek）");
        return AiServices.builder(ConversationPlannerAgent.class)
                .chatLanguageModel(model)
                .build();
    }
}
