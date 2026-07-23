package com.moka.demo;

import com.moka.ai.agent.ConversationPlannerAgent;
import com.moka.ai.agent.ExperienceUnderstandingAgent;
import com.moka.ai.agent.OrderUnderstandingService;
import com.moka.ai.context.*;
import com.moka.ai.prompt.PromptAssembler;
import com.moka.ai.retrieval.DishRetriever;
import com.moka.ai.retrieval.RestaurantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Demo 专用端点。
 * <p>
 * 手动编排完整的 Context Preparation Pipeline 6 个步骤，
 * 返回每一步的中间产物供前端可视化展示。
 * <p>
 * 仅在 {@code moka.llm.mock=false} 时生效。
 */
@RestController
@RequestMapping("/api/v1/calls")
@ConditionalOnProperty(name = "moka.llm.mock", havingValue = "false")
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    private final OrderUnderstandingService orderService;
    private final DishRetriever dishRetriever;
    private final RealtimeInfoBuilder realtimeInfoBuilder;
    private final ExperienceUnderstandingAgent experienceAgent;
    private final ConversationPlannerAgent plannerAgent;
    private final PromptAssembler promptAssembler;
    private final RestaurantRepository restaurantRepository;

    public DemoController(
            OrderUnderstandingService orderService,
            DishRetriever dishRetriever,
            RealtimeInfoBuilder realtimeInfoBuilder,
            ExperienceUnderstandingAgent experienceAgent,
            ConversationPlannerAgent plannerAgent,
            PromptAssembler promptAssembler,
            RestaurantRepository restaurantRepository) {
        this.orderService = orderService;
        this.dishRetriever = dishRetriever;
        this.realtimeInfoBuilder = realtimeInfoBuilder;
        this.experienceAgent = experienceAgent;
        this.plannerAgent = plannerAgent;
        this.promptAssembler = promptAssembler;
        this.restaurantRepository = restaurantRepository;
    }

    /**
     * 执行完整 Demo Pipeline。
     * <p>
     * 接收小票照片 → 执行 6 步 Pipeline → 返回全部中间数据。
     * 耗时约 60-90 秒（取决于 Vision API + DeepSeek 响应速度）。
     *
     * @param file 小票照片（MultipartFile）
     * @return 包含全部中间数据的 DemoResponse
     */
    @PostMapping("/demo")
    public ResponseEntity<DemoResponse> demo(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            // 读取文件 → Base64
            String base64 = Base64.getEncoder().encodeToString(file.getBytes());

            // ============================================================
            // [1] Order — 调用视觉模型解析小票
            // ============================================================
            log.info("[Demo] [1] 开始解析订单...");
            OrderData order = orderService.analyzeOrder(base64);
            int itemCount = order.items() != null ? order.items().size() : 0;
            log.info("[Demo] [1] Order: {} {} 人, {} 道菜",
                    order.restaurant(), order.people(), itemCount);

            // 修正：用数据库中的规范餐厅名覆盖 OCR 识别结果
            String matchedName = restaurantRepository.findByName(order.restaurant())
                    .map(RestaurantProfile::restaurantName)
                    .orElse(null);
            if (matchedName != null && !matchedName.equals(order.restaurant())) {
                log.info("[Demo] [1] 校正餐厅名: {} → {}", order.restaurant(), matchedName);
                order = new OrderData(matchedName, order.time(), order.people(),
                        order.items(), order.duration());
            }

            // ============================================================
            // [2] Dish — 检索菜品知识
            // ============================================================
            log.info("[Demo] [2] 开始检索菜品知识...");
            List<String> dishNames = order.items().stream()
                    .map(DishItem::name)
                    .collect(Collectors.toList());
            List<DishKnowledge> dishes = dishRetriever.retrieve(dishNames);
            log.info("[Demo] [2] Dish: 匹配 {}/{}", dishes.size(), dishNames.size());

            // ============================================================
            // [3] Realtime — 实时环境（天气 + 时间 + 节假日）
            // ============================================================
            log.info("[Demo] [3] 开始获取实时信息...");
            RealtimeInfo realtime = realtimeInfoBuilder.build(order.restaurant());
            log.info("[Demo] [3] Realtime: 天气={}, 时间={}, 节日={}",
                    realtime.weather(), realtime.currentTime(), realtime.holiday());

            // ============================================================
            // [4] Experience — 体验推理
            // ============================================================
            log.info("[Demo] [4] 开始体验推理...");
            ExperienceUnderstanding experience = experienceAgent.analyze(order, dishes, realtime);
            int expCount = experience != null && experience.possibilities() != null
                    ? experience.possibilities().size() : 0;
            log.info("[Demo] [4] Experience: {} 条可能性", expCount);

            // ============================================================
            // [5] Plan — 对话规划
            // ============================================================
            log.info("[Demo] [5] 开始对话规划...");
            ConversationPlan plan = plannerAgent.plan(order, dishes, realtime, experience);
            log.info("[Demo] [5] Planner: {} 条方向, {} 个机会点, {} 条限制",
                    plan.directions().size(),
                    plan.availableHooks().size(),
                    plan.avoid().size());

            // ============================================================
            // [6] Assembly — 组装 Runtime Prompt
            // ============================================================
            log.info("[Demo] [6] 开始组装 Prompt...");
            WorkflowContext ctx = new WorkflowContext()
                    .withPhotoBase64(base64)
                    .withOrder(order)
                    .withDishes(dishes)
                    .withRealtime(realtime)
                    .withExperience(experience)
                    .withPlan(plan);
            RuntimePrompt prompt = promptAssembler.assemble(ctx);
            String finalPrompt = prompt.finalPrompt();
            ctx.setRuntimePrompt(finalPrompt);
            log.info("[Demo] [6] Assembly: {} 字符", finalPrompt.length());

            // ============================================================
            // 返回
            // ============================================================
            DemoResponse response = new DemoResponse(
                    true, order, dishes, realtime, experience, plan, prompt);
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("[Demo] 文件读取失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("[Demo] Pipeline 执行失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
