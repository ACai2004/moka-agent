package com.moka.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 实时天气信息获取工具。
 * <p>
 * 提供两级天气查询：
 * 1. 高德天气 API：行政区级别（如"朝阳区"），需 API Key
 * 2. wttr.in：城市级别（如"北京"），免费无需 Key，作为 fallback
 * <p>
 * 高德 API 失败时自动降级到 wttr.in，不阻塞 Workflow。
 */
@Component
public class WeatherTool {

    private static final Logger log = LoggerFactory.getLogger(WeatherTool.class);
    private static final String WTTR_URL = "https://wttr.in/%s?format=%%C:+%%t+%%w&lang=zh";

    /** 高德 API 要求用行政区代码（adcode）而非城市名查询，此处映射常用城市 */
    private static final Map<String, String> CITY_ADCODES = Map.of(
            "北京", "110000",
            "上海", "310000",
            "广州", "440100",
            "深圳", "440300",
            "天津", "120000",
            "重庆", "500000",
            "杭州", "330100",
            "成都", "510100",
            "武汉", "420100",
            "南京", "320100"
    );

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String gaodeKey;
    private final String gaodeBaseUrl;

    public WeatherTool(
            @Value("${moka.gaode.api-key}") String gaodeKey,
            @Value("${moka.gaode.base-url}") String gaodeBaseUrl) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.gaodeKey = gaodeKey;
        this.gaodeBaseUrl = gaodeBaseUrl;
    }

    /**
     * 获取城市级别天气（wttr.in，fallback）。
     */
    public String getWeather(String city) {
        try {
            String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
            String url = String.format(WTTR_URL, encodedCity);
            String response = restTemplate.getForObject(url, String.class);
            if (response != null) {
                return response.trim();
            }
            return "未知";
        } catch (Exception e) {
            log.warn("wttr.in 获取天气失败(city={}): {}", city, e.getMessage());
            return "未知";
        }
    }

    /**
     * 获取行政区级别天气（高德 API）。
     * <p>
     * 例如：district="朝阳区" → "朝阳区：多云 23°C 东南风2级"
     * 失败时自动降级到 getWeather(city)。
     *
     * @param district 行政区名，如"朝阳区"、"海淀区"
     * @param cityFallback 降级用的城市名，如"北京"
     * @return 天气描述
     */
    public String getDistrictWeather(String district, String cityFallback) {
        try {
            String gaodeCity = district.contains("区") ? cityFallback : district;

            // 尝试用城市名查询
            String result = tryGaodeQuery(gaodeCity);
            if (result != null) return result;

            // 城市名查不到时，尝试用 adcode（行政区代码）查询
            String adcode = CITY_ADCODES.get(cityFallback);
            if (adcode != null) {
                log.debug("城市名查询无结果，尝试 adcode: {}={}", cityFallback, adcode);
                result = tryGaodeQuery(adcode);
                if (result != null) return result;
            }

            log.warn("高德天气返回异常，降级到 wttr.in: district={}", district);
            return getWeather(cityFallback);

        } catch (Exception e) {
            log.warn("高德天气 API 调用失败，降级到 wttr.in: {}", e.getMessage());
            return getWeather(cityFallback);
        }
    }

    /**
     * 执行一次高德天气查询，成功返回格式化字符串，失败返回 null。
     */
    private String tryGaodeQuery(String cityParam) {
        try {
            String encoded = URLEncoder.encode(cityParam, StandardCharsets.UTF_8);
            String urlStr = gaodeBaseUrl + "/weather/weatherInfo?city=" + encoded + "&key=" + gaodeKey;

            var httpClient = java.net.http.HttpClient.newHttpClient();
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(urlStr))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();
            var httpResp = httpClient.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String response = httpResp.body();

            if (response != null && !response.isBlank()) {
                JsonNode root = objectMapper.readTree(response);
                if ("1".equals(root.path("status").asText())) {
                    JsonNode live = root.path("lives").get(0);
                    if (live != null) {
                        String weather = live.path("weather").asText("未知");
                        String temp = live.path("temperature").asText("?");
                        String windDir = live.path("winddirection").asText();
                        String windPower = live.path("windpower").asText();
                        return String.format("%s：%s %s°C %s风%s级", cityParam, weather, temp, windDir, windPower);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("高德查询失败(cityParam={}): {}", cityParam, e.getMessage());
        }
        return null;
    }
}
