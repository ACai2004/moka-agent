package com.moka.biz.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moka.ai.agent.ConversationPlannerAgent;
import com.moka.ai.agent.ExperienceUnderstandingAgent;
import com.moka.ai.agent.OrderUnderstandingService;
import com.moka.ai.context.*;
import com.moka.ai.retrieval.DishRetriever;
import com.moka.ai.retrieval.RestaurantRepository;
import com.moka.ai.tools.WeatherTool;
import com.moka.common.mock.MockLlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pre-call Context 生成接口。
 * <p>
 * 运行 Pre-call 流程，输出结构化的 Pre-call Context JSON，
 * 供 Python Runtime Agent 的会话初始化使用。
 * <p>
 * 同时兼容两种模式：
 * - mock=true：订单/体验/规划用 Mock 数据，菜品/餐厅/天气用真实数据
 * - mock=false：全部走真实 Agent（视觉 OCR + DeepSeek 推理）
 * <p>
 * 路径：POST /api/v1/calls/context
 */
@RestController
@RequestMapping("/api/v1/calls")
public class PreCallContextController {

    private static final Logger log = LoggerFactory.getLogger(PreCallContextController.class);

    private final OrderUnderstandingService orderUnderstandingService;
    private final DishRetriever dishRetriever;
    private final WeatherTool weatherTool;
    private final RestaurantRepository restaurantRepository;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private MockLlmService mockLlmService;

    @Autowired(required = false)
    private ExperienceUnderstandingAgent experienceAgent;

    @Autowired(required = false)
    private ConversationPlannerAgent plannerAgent;

    public PreCallContextController(OrderUnderstandingService orderUnderstandingService,
                                    DishRetriever dishRetriever,
                                    WeatherTool weatherTool,
                                    RestaurantRepository restaurantRepository,
                                    ObjectMapper objectMapper) {
        this.orderUnderstandingService = orderUnderstandingService;
        this.dishRetriever = dishRetriever;
        this.weatherTool = weatherTool;
        this.restaurantRepository = restaurantRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成 Pre-call Context。
     *
     * @param req 可选，包含 photoBase64（小票照片）。为空时用 Mock/fallback 订单。
     */
    @PostMapping("/context")
    public ResponseEntity<Map<String, Object>> generateContext(
            @RequestBody(required = false) PhotoRequest req) {

        try {
            String photoBase64 = req != null ? req.photoBase64() : "";

            // [1] 订单理解（mock 或真实 OCR）
            OrderData order = orderUnderstandingService.analyzeOrder(photoBase64);

            // [2] 菜品知识检索（真实知识库）
            List<String> dishNames = order.items().stream()
                    .map(DishItem::name)
                    .collect(Collectors.toList());
            List<DishKnowledge> dishes = dishRetriever.retrieve(dishNames);

            // [3] 餐厅信息（真实资料库）
            Optional<RestaurantProfile> restaurant = restaurantRepository.findByName(order.restaurant());

            // [4] 实时信息（真实天气/时间/节日）
            RealtimeInfo realtime = buildRealtime(order.restaurant());

            // [5] 体验理解（mock 或真实 Agent）
            ExperienceUnderstanding experience;
            if (mockLlmService != null) {
                experience = mockLlmService.mockExperienceUnderstanding();
            } else if (experienceAgent != null) {
                experience = experienceAgent.analyze(order, dishes, realtime);
            } else {
                experience = new ExperienceUnderstanding(List.of());
            }

            // [6] 对话规划（mock 或真实 Agent）
            ConversationPlan plan;
            if (mockLlmService != null) {
                plan = mockLlmService.mockConversationPlan();
            } else if (plannerAgent != null) {
                plan = plannerAgent.plan(order, dishes, realtime, experience);
            } else {
                plan = new ConversationPlan(List.of(), List.of(), List.of());
            }

            // [7] 组装 Pre-call Context JSON
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("order", order);
            context.put("dishes", dishes);
            context.put("restaurant_profile", restaurant.orElse(null));
            context.put("realtime", realtime);
            context.put("experience", experience);
            context.put("plan", plan);

            log.info("Pre-call Context 生成成功: {} {} 人, {} 道菜",
                    order.restaurant(), order.people(),
                    dishes.size());
            return ResponseEntity.ok(context);

        } catch (Exception e) {
            log.error("生成 Pre-call Context 失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /** 构建实时信息（真实高德天气 + 系统时间 + 节假日）。 */
    private RealtimeInfo buildRealtime(String restaurantName) {
        String weather;
        String cityFallback = "北京";

        Optional<RestaurantProfile> restaurant = restaurantRepository.findByName(restaurantName);
        if (restaurant.isPresent() && restaurant.get().address() != null) {
            String addr = restaurant.get().address();
            String district = extractDistrict(addr);
            cityFallback = extractCity(addr);
            weather = weatherTool.getDistrictWeather(district, cityFallback);
        } else {
            weather = weatherTool.getWeather(cityFallback);
        }

        LocalDateTime now = LocalDateTime.now();
        String dayOfWeek = now.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINESE);
        String formattedTime = String.format("%s %s", dayOfWeek,
                now.format(DateTimeFormatter.ofPattern("HH:mm")));

        String holiday = fetchHoliday(now.toLocalDate().toString());

        return new RealtimeInfo(weather, holiday, null, formattedTime);
    }

    /** 从地址提取区名，如"北京市朝阳区三里屯" → "朝阳区" */
    private String extractDistrict(String address) {
        int start = address.indexOf("市");
        int end = address.indexOf("区");
        if (start >= 0 && end > start) {
            return address.substring(start + 1, end + 1);
        }
        return address;
    }

    /** 从地址提取城市名 */
    private String extractCity(String address) {
        if (address.startsWith("北京")) return "北京";
        if (address.startsWith("上海")) return "上海";
        if (address.startsWith("广州")) return "广州";
        if (address.startsWith("深圳")) return "深圳";
        int idx = address.indexOf("市");
        if (idx > 0) return address.substring(0, idx + 1);
        return address;
    }

    /** 调用节假日 API */
    private String fetchHoliday(String dateStr) {
        try {
            RestTemplate rt = new RestTemplate();
            String url = "https://timor.tech/api/holiday/info/" + dateStr;
            String resp = rt.getForObject(url, String.class);
            if (resp != null) {
                JsonNode root = objectMapper.readTree(resp);
                JsonNode holidayNode = root.path("holiday");
                if (!holidayNode.isNull() && !holidayNode.isMissingNode()) {
                    String name = holidayNode.path("name").asText("");
                    if (!name.isBlank()) return name;
                }
            }
        } catch (Exception e) {
            log.debug("节假日 API 调用失败: {}", e.getMessage());
        }
        return null;
    }

    /** 请求体：可选的小票照片 base64 */
    public record PhotoRequest(String photoBase64) {}
}
