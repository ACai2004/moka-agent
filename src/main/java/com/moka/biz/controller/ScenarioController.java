package com.moka.biz.controller;

import com.moka.ai.context.DishItem;
import com.moka.ai.context.DishKnowledge;
import com.moka.ai.context.DishRole;
import com.moka.ai.context.OrderData;
import com.moka.ai.context.RuntimePrompt;
import com.moka.ai.context.WorkflowContext;
import com.moka.ai.workflow.ContextPreparationWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景接口（生产对接路径）。
 * <p>
 * 三个接口：
 * 1. POST /api/v1/access_token            —— appKey+appSecret 换 token
 * 2. POST /api/v1/scenarios/invoke        —— 场景调起（业务系统传入结构化订单 + 菜品知识 + 定位 + 氛围服务）
 * 3. GET  /api/v1/scenarios/{scenarioNo}  —— 查询场景定义的输入/输出
 * <p>
 * 仅当 {@code moka.llm.mock=false}（Real 模式）时可用。
 * 详见契约文档《漫谈-Agent-场景接口契约.md》。
 */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "moka.llm.mock", havingValue = "false")
public class ScenarioController {

    private static final Logger log = LoggerFactory.getLogger(ScenarioController.class);

    /** 当前唯一注册的场景 */
    public static final String SCENARIO_MANTAN_RECALL = "MANTAN_DINING_RECALL";

    private final ContextPreparationWorkflow workflow;
    private final ApiTokenService tokenService;

    public ScenarioController(ContextPreparationWorkflow workflow, ApiTokenService tokenService) {
        this.workflow = workflow;
        this.tokenService = tokenService;
    }

    // ================= 鉴权：获取 Token =================

    /**
     * POST /api/v1/access_token
     */
    @PostMapping("/access_token")
    public Map<String, Object> accessToken(@RequestBody AccessTokenRequest req) {
        String token = tokenService.issue(req.appKey(), req.appSecret());
        if (token == null) {
            return response(0, "鉴权失败：appKey 或 appSecret 错误", "AUTH_INVALID", null);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessToken", token);
        data.put("expiration", 30);
        return response(1, "ok", "", data);
    }

    // ================= 业务接口 1：查询场景定义 =================

    /**
     * GET /api/v1/scenarios/{scenarioNo}
     */
    @GetMapping("/scenarios/{scenarioNo}")
    public Map<String, Object> getScenario(
            @RequestHeader(value = "AccessToken", required = false) String token,
            @PathVariable String scenarioNo) {
        if (!tokenService.isValid(token)) {
            return response(0, "Token 无效或已过期", "AUTH_INVALID", null);
        }
        if (!SCENARIO_MANTAN_RECALL.equals(scenarioNo)) {
            return response(0, "场景不存在: " + scenarioNo, "SCENARIO_NOT_FOUND", null);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scenarioNo", SCENARIO_MANTAN_RECALL);
        data.put("name", "餐后回访提示词生成");
        data.put("description", "输入一次用餐的识别结果，生成个性化餐后回访 Runtime Prompt");
        data.put("version", "1.0.0");
        data.put("inputSchema", INPUT_SCHEMA_JSON);
        data.put("outputSchema", OUTPUT_SCHEMA_JSON);
        return response(1, "ok", "", data);
    }

    // ================= 业务接口 2：场景调起 =================

    /**
     * POST /api/v1/scenarios/invoke
     */
    @PostMapping("/scenarios/invoke")
    public Map<String, Object> invoke(
            @RequestHeader(value = "AccessToken", required = false) String token,
            @RequestBody InvokeRequest req) {
        if (!tokenService.isValid(token)) {
            return response(0, "Token 无效或已过期", "AUTH_INVALID", null);
        }
        if (req == null || req.scenarioNo() == null || !SCENARIO_MANTAN_RECALL.equals(req.scenarioNo())) {
            return response(0, "场景不存在", "SCENARIO_NOT_FOUND", null);
        }
        if (req.params() == null) {
            return response(0, "params 不能为空", "PARAMS_INVALID", null);
        }
        Params p = req.params();
        if (isBlank(p.restaurant()) || isBlank(p.time())
                || p.people() == null || p.items() == null || p.items().isEmpty()) {
            return response(0, "必填字段缺失：restaurant / time / people / items（至少 1 条）",
                    "PARAMS_INVALID", null);
        }

        try {
            long start = System.currentTimeMillis();
            WorkflowContext ctx = mapToContext(p);
            RuntimePrompt prompt = workflow.execute(ctx);
            log.info("[ScenarioController] invoke 成功，耗时 {}ms", System.currentTimeMillis() - start);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("finalPrompt", prompt.finalPrompt());
            data.put("systemPromptVersion", prompt.systemPromptVersion());
            data.put("generatedAt", prompt.generatedAt());
            data.put("assemblyDurationMs", prompt.assemblyDurationMs());
            return response(1, "ok", "", data);
        } catch (Exception e) {
            log.error("[ScenarioController] invoke 失败", e);
            return response(0, "内部错误: " + e.getMessage(), "INTERNAL_ERROR", null);
        }
    }

    // ================= 映射：params -> WorkflowContext =================

    private WorkflowContext mapToContext(Params p) {
        List<DishItem> items = new ArrayList<>();
        List<DishKnowledge> dishes = new ArrayList<>();
        if (p.items() != null) {
            for (ItemDTO it : p.items()) {
                String name = it.name();
                int qty = it.quantity() == null ? 1 : it.quantity();
                items.add(new DishItem(name, qty, null, it.notes(), it.category(), it.price()));
                dishes.add(new DishKnowledge(name, parseDishRole(it.dishRole()),
                        it.features() == null ? List.of() : it.features(),
                        it.experienceTags() == null ? List.of() : it.experienceTags()));
            }
        }
        OrderData order = new OrderData(p.restaurant(), p.time(),
                p.people() == null ? 0 : p.people(), items, p.duration());

        return new WorkflowContext()
                .withOrder(order)
                .withDishes(dishes)
                .withDistrict(p.district())
                .withCity(p.city())
                .withEnvironmentFeatures(p.environmentFeatures())
                .withServiceFeatures(p.serviceFeatures());
    }

    private DishRole parseDishRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        try {
            return DishRole.valueOf(role);
        } catch (IllegalArgumentException e) {
            log.warn("[ScenarioController] 未知 dishRole: {}", role);
            return null;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ================= 响应封装 =================

    private Map<String, Object> response(int success, String message, String errorCode, Object data) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", success);
        r.put("message", message);
        r.put("errorCode", errorCode);
        r.put("data", data);
        return r;
    }

    // ================= 请求 DTO =================

    public record AccessTokenRequest(String appKey, String appSecret) {}

    public record InvokeRequest(String scenarioNo, Params params) {}

    public record Params(String orderNo, String storeNo, String restaurant, String district, String city,
                         String time, Integer people, String duration, List<ItemDTO> items,
                         List<String> environmentFeatures, List<String> serviceFeatures) {}

    public record ItemDTO(String name, Integer quantity, String notes, String category, String price,
                          List<String> features, List<String> experienceTags, String dishRole) {}

    // ================= 场景定义的转义 JSON Schema 字符串 =================
    // 内容为标准 JSON Schema，与契约文档第 4 章一致

    private static final String INPUT_SCHEMA_JSON = "{\"type\":\"object\",\"required\":[\"restaurant\",\"time\",\"people\",\"items\"],\"properties\":{\"orderNo\":{\"type\":\"string\",\"description\":\"业务系统订单号（可选）\"},\"storeNo\":{\"type\":\"string\",\"description\":\"业务系统门店编号（可选）\"},\"restaurant\":{\"type\":\"string\",\"description\":\"识别出的餐厅名\"},\"district\":{\"type\":\"string\",\"description\":\"餐厅所在区，如朝阳区（天气定位用）\"},\"city\":{\"type\":\"string\",\"description\":\"餐厅所在城市，如北京（天气定位用，默认北京）\"},\"environmentFeatures\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"description\":\"餐厅氛围特征，如复古海报（Layer1氛围行用）\"},\"serviceFeatures\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"description\":\"餐厅服务特征，如快餐型服务（Layer1服务行用）\"},\"time\":{\"type\":\"string\",\"description\":\"用餐时间\"},\"people\":{\"type\":\"integer\",\"description\":\"用餐人数\"},\"duration\":{\"type\":\"string\",\"description\":\"用餐时长（可选，可传可不传）\"},\"items\":{\"type\":\"array\",\"description\":\"菜品明细，至少 1 条\",\"items\":{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\",\"description\":\"菜品名称\"},\"quantity\":{\"type\":\"integer\",\"description\":\"数量，默认 1\"},\"notes\":{\"type\":\"string\",\"description\":\"备注/特殊要求（含辣度等）\"},\"category\":{\"type\":\"string\",\"description\":\"品类\"},\"price\":{\"type\":\"string\",\"description\":\"单价（字符串）\"},\"features\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"description\":\"菜品特点，如酸甜开胃（Layer1菜品行用）\"},\"experienceTags\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"description\":\"菜品体验标签，如配烤肉（Layer1菜品行用）\"},\"dishRole\":{\"type\":\"string\",\"description\":\"菜品角色（SIGNATURE/MAIN/SIDE/STAPLE/DESSERT/DRINK/CONDIMENT），可选\"}}}}}}";

    private static final String OUTPUT_SCHEMA_JSON = "{\"type\":\"object\",\"required\":[\"finalPrompt\",\"systemPromptVersion\",\"generatedAt\",\"assemblyDurationMs\"],\"properties\":{\"finalPrompt\":{\"type\":\"string\",\"description\":\"完整 Runtime Prompt\"},\"systemPromptVersion\":{\"type\":\"string\",\"description\":\"系统提示词版本号\"},\"generatedAt\":{\"type\":\"integer\",\"description\":\"生成时间戳（毫秒）\"},\"assemblyDurationMs\":{\"type\":\"integer\",\"description\":\"组装耗时（毫秒）\"}}}";
}
