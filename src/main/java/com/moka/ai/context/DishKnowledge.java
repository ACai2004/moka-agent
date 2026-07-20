package com.moka.ai.context;

import java.util.List;

/**
 * 菜品知识（RAG 检索结果）。
 * <p>
 * 来源：菜品知识库，通过 DishRetriever [2] 检索。
 * 注意：DishKnowledge 不直接进入 Runtime Prompt，它被 ExperienceUnderstandingAgent [4]
 * 和 ConversationPlannerAgent [5] 消费，用于生成体验可能性和对话策略。
 * <p>
 * dishRole 控制对话参与程度：招牌菜可主动聊、配菜不提及、饮品看情况等。
 *
 * @param dishName       菜品名称
 * @param dishRole       菜品在用餐中的角色（控制对话策略）
 * @param features       菜品特点，如["经典泰式", "酸辣开胃"]
 * @param experienceTags 体验方向，如["下饭", "招牌必点"]
 */
public record DishKnowledge(
        String dishName,
        DishRole dishRole,
        List<String> features,
        List<String> experienceTags
) {
}
