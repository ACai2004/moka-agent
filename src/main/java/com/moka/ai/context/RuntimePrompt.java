package com.moka.ai.context;

/**
 * 最终 Runtime Prompt 封装。
 * <p>
 * 合并 Static System Prompt 和 Dynamic Context（三层）后的最终产物，
 * 将传递给火山引擎 Voice Agent。
 *
 * @param finalPrompt         合并后的完整 Runtime Prompt
 * @param systemPromptVersion System Prompt 版本号（预留，用于版本管理）
 * @param generatedAt         生成时间戳
 * @param assemblyDurationMs  组装耗时（毫秒）
 */
public record RuntimePrompt(
        String finalPrompt,
        String systemPromptVersion,
        long generatedAt,
        int assemblyDurationMs
) {
}
