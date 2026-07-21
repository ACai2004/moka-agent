package com.moka.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moka.ai.context.DishItem;
import com.moka.ai.context.OrderData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 真实模式下的订单理解服务。
 * <p>
 * 当 {@code moka.llm.mock=false} 时生效。
 * 接收小票照片 base64，直接调用 OpenRouter Vision API 解析为结构化 OrderData。
 * <p>
 * photoBase64 为空时（预览模式），返回预设 fallback 订单数据。
 */
@Component
@ConditionalOnProperty(name = "moka.llm.mock", havingValue = "false")
public class RealOrderUnderstandingService implements OrderUnderstandingService {

    private static final Logger log = LoggerFactory.getLogger(RealOrderUnderstandingService.class);

    private static final String VISION_PROMPT = """
            你是一个小票识别专家。分析这张餐厅小票照片，提取结构化订单信息。
            输出严格 JSON 格式（只输出 JSON，不要多余文本），包含以下字段：
            - restaurant: 餐厅名称
            - time: 用餐时间
            - people: 用餐人数
            - items: 菜品数组，每项包含 name(名称)、quantity(数量)、spiceLevel(辣度要求)、notes(备注)、category(品类)、price(单价)
            - duration: 用餐时长
            要求：
            - 不确定的字段用 null，不要编造
            - 菜品名称从照片如实提取
            """;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String modelName;

    public RealOrderUnderstandingService(
            @Value("${moka.openrouter.api-key}") String apiKey,
            @Value("${moka.openrouter.base-url}") String baseUrl,
            @Value("${moka.openrouter.vision-model}") String modelName,
            ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.modelName = modelName;
    }

    @Override
    public OrderData analyzeOrder(String photoBase64) {
        if (photoBase64 == null || photoBase64.isBlank()) {
            log.info("photoBase64 为空，返回预设 fallback 数据（预览模式）");
            return fallbackOrderData();
        }

        try {
            String dataUri = "data:image/jpeg;base64," + photoBase64;

            // 构建 OpenRouter API 请求体
            Map<String, Object> requestBody = Map.of(
                    "model", modelName,
                    "messages", List.of(
                            Map.of("role", "system", "content", VISION_PROMPT),
                            Map.of("role", "user", "content", List.of(
                                    Map.of("type", "text", "text", "请识别这张小票并提取结构化信息。"),
                                    Map.of("type", "image_url", "image_url", Map.of("url", dataUri))
                            ))
                    ),
                    "temperature", 0.1,
                    "max_tokens", 2000
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            log.info("调用视觉模型解析小票...");
            long start = System.currentTimeMillis();
            ResponseEntity<String> rawResponse = restTemplate.postForEntity(
                    baseUrl + "/chat/completions", request, String.class);
            long elapsed = System.currentTimeMillis() - start;

            // 解析响应
            JsonNode root = objectMapper.readTree(rawResponse.getBody());
            String content = root.path("choices").get(0).path("message").path("content").asText().trim();
            log.info("视觉模型响应（{}ms），长度: {} 字符", elapsed, content.length());

            // 提取 JSON（模型经常会包在 ```json 代码块中）
            if (content.startsWith("```")) {
                int startIdx = content.indexOf('\n') + 1;
                int endIdx = content.lastIndexOf("```");
                content = content.substring(startIdx, endIdx).trim();
            }

            OrderData orderData = objectMapper.readValue(content, OrderData.class);
            log.info("解析成功: {} {} 人, {} 道菜",
                    orderData.restaurant(), orderData.people(),
                    orderData.items() != null ? orderData.items().size() : 0);
            return orderData;

        } catch (Exception e) {
            log.error("视觉模型解析小票失败: {}", e.getMessage());
            throw new RuntimeException("小票识别失败: " + e.getMessage(), e);
        }
    }

    private OrderData fallbackOrderData() {
        return new OrderData(
                "売泰", "周五 19:20", 3,
                List.of(
                        new DishItem("打抛饭（不可免辣）", 1, "不可免辣", "加牛肉", "主食", "38"),
                        new DishItem("牛肉船粉", 1, null, null, "主食", "48"),
                        new DishItem("可乐（罐装）", 2, null, null, "饮品", "10")
                ),
                "1h30min"
        );
    }
}
