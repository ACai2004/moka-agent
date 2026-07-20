package com.moka.ai.context;

import java.util.List;

/**
 * 对话策略（Layer 3）。
 * <p>
 * 来源：ConversationPlannerAgent [5] 的输出。
 * 告诉 Voice Agent 如何使用 Layer 1 和 Layer 2 的信息进行自然对话。
 * <p>
 * 注意：输出的是方向性指导，不是聊天脚本。不指定具体台词，不预设时间线顺序。
 * （预留字段 subGoals、contingencies 将在未来版本中加入，用于子目标分解和条件分支。）
 *
 * @param directions     方向，如"优先从整体离店感受切入，不要直接进入菜品评价"
 * @param availableHooks 机会点，可自然利用的话题，如"大雨天气"、"较长的用餐时间"
 * @param avoid          限制，应避免的方向，如"不假设同行关系"、"不逐菜询问"
 */
public record ConversationPlan(
        List<String> directions,
        List<String> availableHooks,
        List<String> avoid
) {
}
