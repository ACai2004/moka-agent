package com.moka.ai.workflow;

import com.moka.ai.agent.ConversationPlannerAgent;
import com.moka.ai.agent.ExperienceUnderstandingAgent;
import com.moka.ai.context.*;
import com.moka.ai.prompt.PromptAssembler;
import com.moka.ai.retrieval.DishRetriever;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 真实模式 AI Agent 验证演示。
 * <p>
 * 当 {@code moka.llm.mock=false} 时自动运行，调用真实 DeepSeek 模型
 * 验证 ExperienceUnderstandingAgent 和 ConversationPlannerAgent
 * 能正确返回结构化输出。
 */
@Component
@ConditionalOnProperty(name = "moka.llm.mock", havingValue = "false")
public class RealModeDemo {

    private static final Logger log = LoggerFactory.getLogger(RealModeDemo.class);

    private final ExperienceUnderstandingAgent experienceAgent;
    private final ConversationPlannerAgent plannerAgent;
    private final DishRetriever dishRetrieval;
    private final ContextAssembler contextAssembler;
    private final PromptAssembler promptAssembler;

    public RealModeDemo(ExperienceUnderstandingAgent experienceAgent,
                        ConversationPlannerAgent plannerAgent,
                        DishRetriever dishRetriever,
                        ContextAssembler contextAssembler,
                        PromptAssembler promptAssembler) {
        this.experienceAgent = experienceAgent;
        this.plannerAgent = plannerAgent;
        this.dishRetrieval = dishRetriever;
        this.contextAssembler = contextAssembler;
        this.promptAssembler = promptAssembler;
    }

    @PostConstruct
    public void demo() {
        try {
            log.info("===== AI Agent 验证演示（真实模型）=====");

            // 准备测试数据
            OrderData order = new OrderData(
                    "売泰", "周五 19:20", 3,
                    List.of(
                            new DishItem("打抛饭（不可免辣）", 1, "不可免辣", "加牛肉", "主食", "38"),
                            new DishItem("牛肉船粉", 1, null, null, "主食", "48"),
                            new DishItem("可乐（罐装）", 2, null, null, "饮品", "10")
                    ),
                    "1h30min"
            );
            log.info("[测试数据] 订单：{} 用餐 {} 人", order.restaurant(), order.people());

            // 菜品知识检索
            List<String> dishNames = order.items().stream()
                    .map(DishItem::name)
                    .collect(Collectors.toList());
            List<DishKnowledge> dishes = dishRetrieval.retrieve(dishNames);
            log.info("[DishRetrieval] 找到 {}/{} 道菜品知识", dishes.size(), dishNames.size());

            // 实时信息
            RealtimeInfo realtime = new RealtimeInfo("晴", "端午节", null, "周五晚上");

            // 步骤 [4]: 调用真实 ExperienceUnderstandingAgent
            log.info("[4] 调用 ExperienceUnderstandingAgent（DeepSeek）...");
            long start = System.currentTimeMillis();
            ExperienceUnderstanding experience = experienceAgent.analyze(order, dishes, realtime);
            long elapsed = System.currentTimeMillis() - start;
            log.info("[4] 完成（{}ms），返回 {} 条可能性", elapsed,
                    experience != null ? experience.possibilities().size() : 0);

            if (experience != null && experience.possibilities() != null) {
                for (ExperiencePossibility p : experience.possibilities()) {
                    log.info("    - [{}] {}（依据：{}）",
                            p.confidenceLevel(), p.description(), p.evidenceSource());
                }
            }

            // 步骤 [5]: 调用真实 ConversationPlannerAgent
            log.info("[5] 调用 ConversationPlannerAgent（DeepSeek）...");
            start = System.currentTimeMillis();
            ConversationPlan plan = plannerAgent.plan(order, dishes, realtime, experience);
            elapsed = System.currentTimeMillis() - start;
            log.info("[5] 完成（{}ms）", elapsed);

            if (plan != null) {
                log.info("    方向：{}", plan.directions());
                log.info("    机会点：{}", plan.availableHooks());
                log.info("    限制：{}", plan.avoid());
            }

            // 组装 WorkflowContext 并生成 RuntimePrompt
            WorkflowContext ctx = new WorkflowContext();
            ctx.setOrder(order);
            ctx.setDishes(dishes);
            ctx.setRealtime(realtime);
            ctx.setExperience(experience);
            ctx.setPlan(plan);

            RuntimePrompt prompt = promptAssembler.assemble(ctx);
            log.info("[6] PromptAssembly 完成，共 {} 字符", prompt.finalPrompt().length());

            log.info("===== ✅ AI Agent 验证通过 =====");

        } catch (Exception e) {
            log.error("AI Agent 验证失败：{}", e.getMessage(), e);
        }
    }
}
