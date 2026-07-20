package com.moka.ai.context;

/**
 * 实时环境信息。
 * <p>
 * 来源：WeatherTool [3] 的 API 调用结果。
 * MVP 阶段全部使用 String 类型，最终以自然语言文本拼入 Raw Facts。
 *
 * @param weather     天气描述，如"大雨"、"晴"
 * @param holiday     临近节日，如"端午节"（无节日则为 null 或空字符串）
 * @param traffic     交通情况（预留，MVP 阶段可为 null）
 * @param currentTime 当前时间描述，如"周五晚上"
 */
public record RealtimeInfo(
        String weather,
        String holiday,
        String traffic,
        String currentTime
) {
}
