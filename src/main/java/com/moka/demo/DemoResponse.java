package com.moka.demo;

import com.moka.ai.context.*;

import java.util.List;

/**
 * Demo 端点专用返回体。
 * <p>
 * 包含 Pipeline 全部中间数据（订单、菜品知识、实时环境、体验理解、对话规划、最终 Prompt），
 * 供前端 Demo 页面逐步骤展示。
 *
 * @param success                  是否成功
 * @param orderData                订单信息（Layer 1）
 * @param dishKnowledge            菜品知识（Layer 1）
 * @param realtimeInfo             实时环境（Layer 1）
 * @param experienceUnderstanding  体验理解（Layer 2）
 * @param conversationPlan         对话规划（Layer 3）
 * @param runtimePrompt            最终 Runtime Prompt
 */
public record DemoResponse(
        boolean success,
        OrderData orderData,
        List<DishKnowledge> dishKnowledge,
        RealtimeInfo realtimeInfo,
        ExperienceUnderstanding experienceUnderstanding,
        ConversationPlan conversationPlan,
        RuntimePrompt runtimePrompt
) {
}
