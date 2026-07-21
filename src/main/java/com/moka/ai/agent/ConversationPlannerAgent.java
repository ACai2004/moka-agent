package com.moka.ai.agent;

import com.moka.ai.context.*;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.List;

/**
 * 对话规划 Agent（Layer 3）。
 * <p>
 * 根据订单信息、菜品知识、实时信息以及体验理解分析，
 * 为 Voice Agent 生成对话策略（方向 / 机会点 / 限制）。
 * <p>
 * 注意：此接口不加 @AiService 注解，代理由 AiServiceConfig 条件创建。
 */
@SystemMessage("""
        你是一个对话规划专家。
        根据订单信息、菜品知识、实时信息以及体验理解分析，
        为 Voice Agent 生成对话策略。

        输出结构：
        1. Directions（方向）：当前适合关注什么
        2. Available Hooks（机会点）：可以自然利用的话题
        3. Avoid（限制）：应避免的方向

        重要约束（必须遵守）：
        1. 不要输出聊天脚本（不要写"第一句话说什么，第二句话说什么"）
        2. 不要指定具体的台词
        3. 方向是框架性的，不是时间线顺序
        4. Hooks 是自然切入点，不是待办清单
        5. 根据菜品的 dishRole 决定交流参与程度：
           - SIGNATURE + MAIN：用户表现出兴趣时可以深入聊
           - STAPLE + DESSERT：自然流动时提及
           - SIDE + CONDIMENT：不主动提及，用户说到再跟
           - DRINK：普通饮品不提，特色饮品可一带而过

        输出 JSON 格式，包含以下字段：
        - directions（字符串数组）
        - availableHooks（字符串数组）
        - avoid（字符串数组）
        """)
public interface ConversationPlannerAgent {

    @UserMessage("订单信息：{{order}}\n\n菜品知识：{{dishes}}\n\n实时环境：{{realtime}}\n\n体验理解：{{experience}}")
    ConversationPlan plan(
            @V("order") OrderData order,
            @V("dishes") List<DishKnowledge> dishes,
            @V("realtime") RealtimeInfo realtime,
            @V("experience") ExperienceUnderstanding experience
    );
}
