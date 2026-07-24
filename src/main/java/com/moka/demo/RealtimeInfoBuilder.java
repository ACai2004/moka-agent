package com.moka.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moka.ai.context.RealtimeInfo;
import com.moka.ai.context.RestaurantProfile;
import com.moka.ai.retrieval.RestaurantRepository;
import com.moka.ai.tools.WeatherTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Optional;

/**
 * 实时信息构建器。
 * <p>
 * 从 ContextPreparationWorkflow.RealtimeNode 中提取的三个私有方法，
 * 封装为独立 @Component，供 DemoController 和未来其他场景复用。
 * <p>
 * 职责：
 * 1. 根据餐厅名查找地址提取区级信息 → 调高德天气 API（失败降级 wttr.in）
 * 2. 获取当前系统时间
 * 3. 调 timor.tech API 判断节假日
 */
@Component
@Profile("demo")
public class RealtimeInfoBuilder {

    private static final Logger log = LoggerFactory.getLogger(RealtimeInfoBuilder.class);

    private final WeatherTool weatherTool;
    private final RestaurantRepository restaurantRepository;
    private final ObjectMapper objectMapper;

    public RealtimeInfoBuilder(WeatherTool weatherTool,
                               RestaurantRepository restaurantRepository,
                               ObjectMapper objectMapper) {
        this.weatherTool = weatherTool;
        this.restaurantRepository = restaurantRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据餐厅名构建实时环境信息。
     *
     * @param restaurantName 餐厅名（可能为 null 或空字符串）
     * @return 实时环境信息
     */
    public RealtimeInfo build(String restaurantName) {
        String weather;
        String cityFallback = "北京";

        // 如果餐厅名不为空，尝试根据地址获取区级天气
        if (restaurantName != null && !restaurantName.isBlank()) {
            Optional<RestaurantProfile> restaurant = restaurantRepository.findByName(restaurantName);
            if (restaurant.isPresent() && restaurant.get().address() != null) {
                String addr = restaurant.get().address();
                String district = extractDistrict(addr);
                cityFallback = extractCity(addr);
                weather = weatherTool.getDistrictWeather(district, cityFallback);
            } else {
                weather = weatherTool.getWeather(cityFallback);
            }
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

        return new RealtimeInfo(weather, holiday, null, formattedTime);
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
        // 通用提取：取第一个"市"前的部分
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
}
