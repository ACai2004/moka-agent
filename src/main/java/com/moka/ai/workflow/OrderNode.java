package com.moka.ai.workflow;

import com.moka.ai.agent.OrderUnderstandingService;
import com.moka.ai.context.OrderData;
import com.moka.ai.context.RestaurantProfile;
import com.moka.ai.context.WorkflowContext;
import com.moka.ai.retrieval.RestaurantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * [1] 订单解析节点。
 * <p>
 * 调用视觉 LLM 解析小票照片为结构化 OrderData。
 * 包含餐厅名校正逻辑（用数据库中的规范名覆盖 OCR 识别结果）。
 */
@Component("OrderNode")
public class OrderNode implements WorkflowNode {

    private static final Logger log = LoggerFactory.getLogger(OrderNode.class);

    private final OrderUnderstandingService orderService;
    private final RestaurantRepository restaurantRepository;

    public OrderNode(OrderUnderstandingService orderService,
                     RestaurantRepository restaurantRepository) {
        this.orderService = orderService;
        this.restaurantRepository = restaurantRepository;
    }

    @Override
    public String nodeName() { return "OrderNode"; }

    @Override
    public WorkflowContext execute(WorkflowContext ctx) {
        OrderData order = ctx.getOrder();
        if (order == null) {
            // 业务系统未传入订单：走视觉识别（demo 路径）
            order = orderService.analyzeOrder(ctx.getPhotoBase64());
        } else {
            log.info("[OrderNode] 使用外部传入订单（跳过视觉识别）: {}", order.restaurant());
        }

        // 餐厅名校正（用数据库中的规范名覆盖 OCR 识别结果；漫谈本地无该餐厅数据时保持原样）
        String matchedName = restaurantRepository.findByName(order.restaurant())
                .map(RestaurantProfile::restaurantName)
                .orElse(null);
        if (matchedName != null && !matchedName.equals(order.restaurant())) {
            log.info("校正餐厅名: {} → {}", order.restaurant(), matchedName);
            order = new OrderData(matchedName, order.time(), order.people(),
                    order.items(), order.duration());
        }

        ctx.setOrder(order);
        log.info("[OrderNode] {} {} 人, {} 道菜",
                order.restaurant(), order.people(),
                order.items() != null ? order.items().size() : 0);
        return ctx;
    }

    @Override
    public List<String> dependsOn() { return List.of(); }
}
