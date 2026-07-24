package com.moka.ai.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moka.ai.context.RealtimeInfo;
import com.moka.ai.context.RestaurantProfile;
import com.moka.ai.context.WorkflowContext;
import com.moka.ai.retrieval.RestaurantRepository;
import com.moka.ai.tools.WeatherTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * [3] 实时信息节点。
 * <p>
 * 获取天气（高德 + wttr.in fallback）、当前时间、节假日信息。
 */
@Component("RealtimeNode")
public class RealtimeNode implements WorkflowNode {

    private static final Logger log = LoggerFactory.getLogger(RealtimeNode.class);

    private final WeatherTool weatherTool;
    private final RestaurantRepository restaurantRepository;
    private final ObjectMapper objectMapper;

    public RealtimeNode(WeatherTool weatherTool,
                        RestaurantRepository restaurantRepository,
                        ObjectMapper objectMapper) {
        this.weatherTool = weatherTool;
        this.restaurantRepository = restaurantRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String nodeName() { return "RealtimeNode"; }

    @Override
    public WorkflowContext execute(WorkflowContext ctx) {
        String restaurantName = ctx.getOrder() != null ? ctx.getOrder().restaurant() : "";
        String weather;
        String cityFallback = "北京";

        // 根据餐厅名查找地址，提取区/城市
        Optional<RestaurantProfile> restaurant = restaurantRepository.findByName(restaurantName);
        if (restaurant.isPresent() && restaurant.get().address() != null) {
            String addr = restaurant.get().address();
            String district = extractDistrict(addr);
            cityFallback = extractCity(addr);
            weather = weatherTool.getDistrictWeather(district, cityFallback);
        } else {
            weather = weatherTool.getWeather(cityFallback);
        }

        // 当前时间
        LocalDateTime now = LocalDateTime.now();
        String dayOfWeek = now.getDayOfWeek().getDisplayName(
                TextStyle.FULL, Locale.CHINESE);
        String formattedTime = String.format("%s %s", dayOfWeek,
                now.format(DateTimeFormatter.ofPattern("HH:mm")));

        // 节假日
        String holiday = fetchHoliday(now.toLocalDate().toString());

        RealtimeInfo realtime = new RealtimeInfo(weather, holiday, null, formattedTime);
        ctx.setRealtime(realtime);
        log.info("[RealtimeNode] 天气={}, 时间={}, 节日={}", weather, formattedTime, holiday);
        return ctx;
    }

    /** 从地址中提取区名，如"北京市朝阳区三里屯" → "朝阳区" */
    private String extractDistrict(String address) {
        int start = address.indexOf("市");
        int end = address.indexOf("区");
        if (start >= 0 && end > start) {
            return address.substring(start + 1, end + 1);
        }
        return address;
    }

    /** 从地址中提取城市名，如"北京市朝阳区三里屯" → "北京" */
    private String extractCity(String address) {
        if (address.startsWith("北京")) return "北京";
        if (address.startsWith("上海")) return "上海";
        if (address.startsWith("广州")) return "广州";
        if (address.startsWith("深圳")) return "深圳";
        int idx = address.indexOf("市");
        if (idx > 0) return address.substring(0, idx + 1);
        return address;
    }

    /** 调用节假日 API 获取当天节日信息 */
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

    @Override
    public List<String> dependsOn() { return List.of("OrderNode"); }
}
