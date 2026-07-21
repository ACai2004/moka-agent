package com.moka.ai.prompt;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Static System Prompt 加载器。
 * <p>
 * 从 classpath:prompt/system-prompt.md 读取预设的系统提示词。
 * 文件不存在时优雅降级（返回空字符串 + 日志 warn），不阻塞启动。
 */
@Component
public class SystemPromptLoader {

    private static final Logger log = LoggerFactory.getLogger(SystemPromptLoader.class);
    private static final String PROMPT_PATH = "prompt/system-prompt.md";
    private static final String FALLBACK_PROMPT = "你是一个餐后体验回访 AI。";

    private String systemPrompt;

    @PostConstruct
    public void init() {
        try {
            var resource = new ClassPathResource(PROMPT_PATH);
            if (!resource.exists()) {
                log.warn("System Prompt 文件不存在: {}, 使用默认提示词", PROMPT_PATH);
                systemPrompt = FALLBACK_PROMPT;
                return;
            }
            systemPrompt = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.info("System Prompt 已加载 ({} 字符)", systemPrompt.length());
        } catch (IOException e) {
            log.warn("读取 System Prompt 失败: {}, 使用默认提示词", e.getMessage());
            systemPrompt = FALLBACK_PROMPT;
        }
    }

    /**
     * 获取 Static System Prompt 文本。
     */
    public String load() {
        return systemPrompt;
    }
}
