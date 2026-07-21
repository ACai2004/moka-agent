package com.moka.ai.agent;

import com.moka.ai.context.DishItem;
import com.moka.ai.context.OrderData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 真实模式下的订单理解服务（Stub）。
 * <p>
 * 当 {@code moka.llm.mock=false} 时生效。此时视觉 LLM 尚未集成，
 * 返回预设订单数据确保 API 不会因缺少 bean 而失败。
 * <p>
 * Phase 4+ 将替换为真实的 @AiService 视觉调用。
 */
@Component
@ConditionalOnProperty(name = "moka.llm.mock", havingValue = "false")
public class RealOrderUnderstandingService implements OrderUnderstandingService {

    private static final Logger log = LoggerFactory.getLogger(RealOrderUnderstandingService.class);

    @Override
    public OrderData analyzeOrder(String photoBase64) {
        log.info("OrderUnderstanding（Stub 模式）：返回预设订单数据");
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
}
