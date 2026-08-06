package com.moka.ai.workflow;

import com.moka.ai.context.DishItem;
import com.moka.ai.context.DishKnowledge;
import com.moka.ai.context.WorkflowContext;
import com.moka.ai.retrieval.DishRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * [2] 菜品知识检索节点。
 * <p>
 * 根据订单中的菜品名，在 115 道菜品知识库中精确匹配。
 */
@Component("DishNode")
public class DishNode implements WorkflowNode {

    private static final Logger log = LoggerFactory.getLogger(DishNode.class);

    private final DishRetriever dishRetriever;

    public DishNode(DishRetriever dishRetriever) {
        this.dishRetriever = dishRetriever;
    }

    @Override
    public String nodeName() { return "DishNode"; }

    @Override
    public WorkflowContext execute(WorkflowContext ctx) {
        // 业务系统已随订单传入菜品知识（含特点/体验标签/角色）时，直接使用，跳过内部匹配
        if (ctx.getDishes() != null && !ctx.getDishes().isEmpty()) {
            log.info("[DishNode] 使用外部传入的菜品知识（{} 道），跳过内部匹配", ctx.getDishes().size());
            return ctx;
        }

        List<String> dishNames = ctx.getOrder().items().stream()
                .map(DishItem::name)
                .collect(Collectors.toList());
        List<DishKnowledge> dishes = dishRetriever.retrieve(dishNames);
        ctx.setDishes(dishes);
        log.info("[DishNode] 匹配 {}/{}", dishes.size(), dishNames.size());
        return ctx;
    }

    @Override
    public List<String> dependsOn() { return List.of("OrderNode"); }
}
