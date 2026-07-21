package com.moka.ai.workflow;

import com.moka.ai.agent.ConversationPlannerAgent;
import com.moka.ai.agent.ExperienceUnderstandingAgent;
import com.moka.ai.agent.OrderUnderstandingService;
import com.moka.ai.context.*;
import com.moka.ai.prompt.PromptAssembler;
import com.moka.ai.retrieval.DishRetriever;
import com.moka.ai.tools.WeatherTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Context 准备工作流。
 * <p>
 * 编排 6 个 Node，按顺序执行完整的数据流：
 * [1] OrderNode → [2] DishNode → [3] RealtimeNode → [4] ExperienceNode → [5] PlannerNode → [6] AssemblyNode
 * <p>
 * 仅在 {@code moka.llm.mock=false} 时生效。
 * Mock 模式下由 ContextPreparationDemo 替代。
 */
@Component
@ConditionalOnProperty(name = "moka.llm.mock", havingValue = "false")
public class ContextPreparationWorkflow {

    private static final Logger log = LoggerFactory.getLogger(ContextPreparationWorkflow.class);

    private final List<WorkflowNode> nodes;

    public ContextPreparationWorkflow(
            OrderUnderstandingService orderService,
            DishRetriever dishRetriever,
            WeatherTool weatherTool,
            ExperienceUnderstandingAgent experienceAgent,
            ConversationPlannerAgent plannerAgent,
            ContextAssembler contextAssembler,
            PromptAssembler promptAssembler
    ) {
        this.nodes = List.of(
                new OrderNode(orderService),
                new DishNode(dishRetriever),
                new RealtimeNode(weatherTool),
                new ExperienceNode(experienceAgent),
                new PlannerNode(plannerAgent),
                new AssemblyNode(contextAssembler, promptAssembler)
        );
    }

    /**
     * 执行完整 Workflow，生成 Runtime Prompt。
     *
     * @param photoBase64 小票照片的 base64 编码
     * @return 完整的 Runtime Prompt
     */
    public RuntimePrompt execute(String photoBase64) {
        log.info("===== ContextPreparationWorkflow 开始 =====");
        long start = System.currentTimeMillis();

        WorkflowContext ctx = new WorkflowContext();
        ctx.setPhotoBase64(photoBase64);

        for (WorkflowNode node : nodes) {
            try {
                log.debug("执行 Node: {}", node.nodeName());
                ctx = node.execute(ctx);
                log.debug("Node {} 完成", node.nodeName());
            } catch (Exception e) {
                if (node.isOptional()) {
                    log.warn("Node {} 失败，使用 fallback: {}", node.nodeName(), e.getMessage());
                    ctx = node.fallback(ctx);
                } else {
                    log.error("Node {} 失败，终止 Workflow", node.nodeName(), e);
                    throw new WorkflowExecutionException("Node " + node.nodeName() + " 失败", e);
                }
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("===== ContextPreparationWorkflow 完成（{}ms）=====", elapsed);
        return new RuntimePrompt(
                ctx.getRuntimePrompt(),
                "v1",
                System.currentTimeMillis(),
                (int) elapsed
        );
    }

    // ========== Node 实现 ==========

    /** [1] 订单解析 */
    private record OrderNode(OrderUnderstandingService service) implements WorkflowNode {
        @Override
        public String nodeName() { return "Order"; }

        @Override
        public WorkflowContext execute(WorkflowContext ctx) {
            OrderData order = service.analyzeOrder(ctx.getPhotoBase64());
            ctx.setOrder(order);
            log.info("[1] Order: {} {} 人, {} 道菜", order.restaurant(), order.people(), order.items().size());
            return ctx;
        }
    }

    /** [2] 菜品知识检索 */
    private record DishNode(DishRetriever retriever) implements WorkflowNode {
        @Override
        public String nodeName() { return "Dish"; }

        @Override
        public WorkflowContext execute(WorkflowContext ctx) {
            List<String> dishNames = ctx.getOrder().items().stream()
                    .map(DishItem::name)
                    .collect(Collectors.toList());
            List<DishKnowledge> dishes = retriever.retrieve(dishNames);
            ctx.setDishes(dishes);
            log.info("[2] Dish: 匹配 {}/{}", dishes.size(), dishNames.size());
            return ctx;
        }
    }

    /** [3] 实时信息 */
    private record RealtimeNode(WeatherTool tool) implements WorkflowNode {
        @Override
        public String nodeName() { return "Realtime"; }

        @Override
        public WorkflowContext execute(WorkflowContext ctx) {
            String weather = tool.getWeather("北京");
            // 当前时间取系统时间
            RealtimeInfo realtime = new RealtimeInfo(weather, null, null, null);
            ctx.setRealtime(realtime);
            log.info("[3] Realtime: 天气={}", weather);
            return ctx;
        }
    }

    /** [4] 体验理解 */
    private record ExperienceNode(ExperienceUnderstandingAgent agent) implements WorkflowNode {
        @Override
        public String nodeName() { return "Experience"; }

        @Override
        public WorkflowContext execute(WorkflowContext ctx) {
            ExperienceUnderstanding experience = agent.analyze(
                    ctx.getOrder(), ctx.getDishes(), ctx.getRealtime());
            ctx.setExperience(experience);
            log.info("[4] Experience: {} 条可能性",
                    experience != null ? experience.possibilities().size() : 0);
            return ctx;
        }
    }

    /** [5] 对话规划 */
    private record PlannerNode(ConversationPlannerAgent agent) implements WorkflowNode {
        @Override
        public String nodeName() { return "Planner"; }

        @Override
        public WorkflowContext execute(WorkflowContext ctx) {
            ConversationPlan plan = agent.plan(
                    ctx.getOrder(), ctx.getDishes(), ctx.getRealtime(), ctx.getExperience());
            ctx.setPlan(plan);
            log.info("[5] Planner: {} 条方向, {} 个机会点, {} 条限制",
                    plan.directions().size(), plan.availableHooks().size(), plan.avoid().size());
            return ctx;
        }
    }

    /** [6] Prompt 组装 */
    private record AssemblyNode(ContextAssembler assembler, PromptAssembler promptAssembler) implements WorkflowNode {
        @Override
        public String nodeName() { return "Assembly"; }

        @Override
        public WorkflowContext execute(WorkflowContext ctx) {
            RuntimePrompt prompt = promptAssembler.assemble(ctx);
            ctx.setRuntimePrompt(prompt.finalPrompt());
            log.info("[6] Assembly: {} 字符", prompt.finalPrompt().length());
            return ctx;
        }
    }
}
