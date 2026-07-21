package com.moka.common.mock;

import com.moka.ai.context.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mock LLM 服务。
 * <p>
 * 当 {@code moka.llm.mock=true} 时生效，返回预设的结构化数据，
 * 用于开发阶段避免调用真实 LLM API。
 */
@Component
@ConditionalOnProperty(name = "moka.llm.mock", havingValue = "true")
public class MockLlmService {

    /**
     * 返回预设的订单数据。
     */
    public OrderData mockOrderData() {
        return new OrderData(
                "売泰",
                "周五 19:20",
                3,
                List.of(
                        new DishItem("打抛饭（不可免辣）", 1, "不可免辣", "加牛肉", "主食", "38"),
                        new DishItem("牛肉船粉", 1, null, null, "主食", "48"),
                        new DishItem("可乐（罐装）", 2, null, null, "饮品", "10")
                ),
                "1h30min"
        );
    }

    /**
     * 返回预设的体验理解数据。
     */
    public ExperienceUnderstanding mockExperienceUnderstanding() {
        return new ExperienceUnderstanding(List.of(
                new ExperiencePossibility(
                        "可能存在朋友聚餐场景，用户更关注整体用餐氛围",
                        ConfidenceLevel.MEDIUM,
                        "订单显示 3 人用餐，点了打抛饭和牛肉船粉等主食"
                ),
                new ExperiencePossibility(
                        "打抛饭选择了加牛肉且辣度固定，用户可能是能吃辣的肉食爱好者",
                        ConfidenceLevel.LOW,
                        "打抛饭备注加牛肉，辣度固定不可免辣"
                ),
                new ExperiencePossibility(
                        "用户可能对泰式船粉有期待，作为招牌主食被点单",
                        ConfidenceLevel.LOW,
                        "牛肉船粉是餐厅招牌主食"
                )
        ));
    }

    /**
     * 返回预设的对话策略数据。
     */
    public ConversationPlan mockConversationPlan() {
        return new ConversationPlan(
                List.of("优先从整体离店感受切入，让用户先分享整体印象"),
                List.of("打抛饭的辣度和加牛肉备注可成为口味偏好话题"),
                List.of("不逐菜询问", "不主动评价菜品口味", "不假设同行关系")
        );
    }
}
