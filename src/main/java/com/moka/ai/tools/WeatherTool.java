package com.moka.ai.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 实时天气信息获取工具。
 * <p>
 * 调用 wttr.in（免费 API，无需 Key），获取指定城市的简短天气描述。
 * API 失败时优雅降级，返回"未知"，不阻塞下游 Workflow。
 */
@Component
public class WeatherTool {

    private static final Logger log = LoggerFactory.getLogger(WeatherTool.class);
    private static final String WTTR_URL = "https://wttr.in/%s?format=%%C:+%%t+%%w&lang=zh";

    private final RestTemplate restTemplate;

    public WeatherTool() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * 获取指定城市的当前天气情况。
     *
     * @param city 城市名，如"北京"
     * @return 天气描述，如"北京: ☀️ +30°C ↑10km/h"，失败时返回"未知"
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
            log.warn("获取天气失败(city={}): {}", city, e.getMessage());
            return "未知";
        }
    }
}
