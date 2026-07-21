package com.moka.ai.agent;

import com.moka.ai.context.DishKnowledge;
import com.moka.ai.context.ExperienceUnderstanding;
import com.moka.ai.context.OrderData;
import com.moka.ai.context.RealtimeInfo;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.List;

/**
 * 体验理解 Agent（Layer 2）。
 * <p>
 * 将订单信息、菜品知识、实时环境转化为体验可能性。
 * 所有输出保持低确定性，禁止输出确定性结论。
 * <p>
 * 注意：此接口不加 @AiService 注解，代理由 AiServiceConfig 条件创建。
 */
@SystemMessage("""
        你是一个餐后体验分析专家。
        根据订单信息、菜品知识和实时环境，
        推测本次用餐可能存在的体验场景。

        关键约束（必须遵守）：
        1. 所有输出必须是「可能性」，不是「结论」
        2. 必须保持低确定性
        3. 禁止将推测当作事实
        4. 每条可能性必须标注 confidenceLevel（只能是 LOW 或 MEDIUM，不允许 HIGH）
        5. 如果信息不足以判断，输出空列表

        输出 JSON 格式，根对象包含 possibilities 数组，每项包含：
        - description（体验可能性描述）
        - confidenceLevel（只能是 LOW 或 MEDIUM）
        - evidenceSource（推测依据，如"订单显示 3 人用餐"）
        """)
public interface ExperienceUnderstandingAgent {

    @UserMessage("订单信息：{{order}}\n\n菜品知识：{{dishes}}\n\n实时环境：{{realtime}}")
    ExperienceUnderstanding analyze(
            @V("order") OrderData order,
            @V("dishes") List<DishKnowledge> dishes,
            @V("realtime") RealtimeInfo realtime
    );
}
