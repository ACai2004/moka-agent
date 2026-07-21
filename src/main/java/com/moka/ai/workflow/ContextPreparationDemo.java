package com.moka.ai.workflow;

import com.moka.ai.context.*;
import com.moka.ai.prompt.PromptAssembler;
import com.moka.ai.retrieval.DishRetriever;
import com.moka.common.mock.MockLlmService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Context 准备流程演示（Mock 版）。
 * <p>
 * 启动时自动跑一遍完整的 6 步数据流，将生成的 Runtime Prompt 输出到日志。
 * 用于验证各模块集成正确、端到端数据流通畅。
 * <p>
 * 仅在 {@code moka.llm.mock=true} 时生效。
 */
@Component
@ConditionalOnProperty(name = "moka.llm.mock", havingValue = "true")
public class ContextPreparationDemo {

    private static final Logger log = LoggerFactory.getLogger(ContextPreparationDemo.class);

    private final MockLlmService mockLlmService;
    private final DishRetriever dishRetriever;
    private final ContextAssembler contextAssembler;
    private final PromptAssembler promptAssembler;

    public ContextPreparationDemo(MockLlmService mockLlmService,
                                  DishRetriever dishRetriever,
                                  ContextAssembler contextAssembler,
                                  PromptAssembler promptAssembler) {
        this.mockLlmService = mockLlmService;
        this.dishRetriever = dishRetriever;
        this.contextAssembler = contextAssembler;
        this.promptAssembler = promptAssembler;
    }

    @PostConstruct
    public void demo() {
        try {
            log.info("===== Context 准备流程演示（Mock 版）=====");

            // 步骤 1: 模拟订单解析
            OrderData order = mockLlmService.mockOrderData();
            log.info("[1] OrderUnderstanding: {} 用餐 {} 人，{} 道菜",
                    order.restaurant(), order.people(), order.items().size());

            // 步骤 2: 菜品知识检索
            List<String> dishNames = order.items().stream()
                    .map(DishItem::name)
                    .collect(Collectors.toList());
            List<DishKnowledge> dishes = dishRetriever.retrieve(dishNames);
            log.info("[2] DishRetrieval: 找到 {}/{} 道菜品知识", dishes.size(), dishNames.size());

            // 步骤 3: 实时信息（使用 Mock 值）
            RealtimeInfo realtime = new RealtimeInfo("晴", "端午节", null, "周五晚上");
            log.info("[3] RealtimeInfo: 天气={}, 节日={}", realtime.weather(), realtime.holiday());

            // 步骤 4: 体验理解（Mock）
            ExperienceUnderstanding experience = mockLlmService.mockExperienceUnderstanding();
            log.info("[4] ExperienceUnderstanding: {} 条可能性", experience.possibilities().size());

            // 步骤 5: 对话策略（Mock）
            ConversationPlan plan = mockLlmService.mockConversationPlan();
            log.info("[5] ConversationPlan: {} 条方向, {} 个机会点, {} 条限制",
                    plan.directions().size(), plan.availableHooks().size(), plan.avoid().size());

            // 组装 WorkflowContext
            WorkflowContext ctx = new WorkflowContext();
            ctx.setPhotoBase64("");  // Mock 模式不需要真实照片
            ctx.setOrder(order);
            ctx.setDishes(dishes);
            ctx.setRealtime(realtime);
            ctx.setExperience(experience);
            ctx.setPlan(plan);

            // 步骤 6: Prompt 组装
            RuntimePrompt runtimePrompt = promptAssembler.assemble(ctx);
            log.info("[6] PromptAssembly: 耗时 {}ms, 共 {} 字符",
                    runtimePrompt.assemblyDurationMs(), runtimePrompt.finalPrompt().length());

            // 输出结果预览
            log.info("===== Runtime Prompt 预览（前 600 字符）=====");
            String preview = runtimePrompt.finalPrompt();
            if (preview.length() > 600) {
                preview = preview.substring(0, 600) + "\n...（余下 " + (preview.length() - 600) + " 字符）";
            }
            log.info("\n{}", preview);

            log.info("===== ✅ 端到端数据流验证通过 =====");

        } catch (Exception e) {
            log.error("Context 准备流程演示失败", e);
        }
    }
}
