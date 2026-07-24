package com.moka.ai.workflow;

import com.moka.ai.agent.ConversationPlannerAgent;
import com.moka.ai.context.ConversationPlan;
import com.moka.ai.context.WorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * [5] 对话规划节点。
 * <p>
 * 调用 DeepSeek 生成对话策略（方向 / 机会点 / 限制）。
 * 仅在 Real 模式下生效（依赖 ConversationPlannerAgent，该 Bean 在 Mock 模式下不存在）。
 */
@Component("PlannerNode")
@ConditionalOnProperty(name = "moka.llm.mock", havingValue = "false")
public class PlannerNode implements WorkflowNode {

    private static final Logger log = LoggerFactory.getLogger(PlannerNode.class);

    private final ConversationPlannerAgent plannerAgent;

    public PlannerNode(ConversationPlannerAgent plannerAgent) {
        this.plannerAgent = plannerAgent;
    }

    @Override
    public String nodeName() { return "PlannerNode"; }

    @Override
    public WorkflowContext execute(WorkflowContext ctx) {
        ConversationPlan plan = plannerAgent.plan(
                ctx.getOrder(), ctx.getDishes(), ctx.getRealtime(), ctx.getExperience());
        ctx.setPlan(plan);
        log.info("[PlannerNode] {} 条方向, {} 个机会点, {} 条限制",
                plan.directions().size(), plan.availableHooks().size(), plan.avoid().size());
        return ctx;
    }

    @Override
    public List<String> dependsOn() {
        return List.of("OrderNode", "DishNode", "RealtimeNode", "ExperienceNode");
    }
}
