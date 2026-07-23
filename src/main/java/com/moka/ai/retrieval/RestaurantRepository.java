package com.moka.ai.retrieval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moka.ai.context.RestaurantProfile;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 餐厅信息仓库。
 * <p>
 * 从 sample-restaurants.json 加载餐厅信息，提供按名称模糊匹配的能力。
 * 匹配策略：检测识别出的餐厅名是否包含 JSON 中任意餐厅的名称（或其子串）。
 */
@Component
public class RestaurantRepository {

    private static final Logger log = LoggerFactory.getLogger(RestaurantRepository.class);
    private static final String DATA_FILE = "data/sample-restaurants.json";

    private final ObjectMapper objectMapper;
    private List<RestaurantProfile> restaurants = Collections.emptyList();

    public RestaurantRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
            var resource = new ClassPathResource(DATA_FILE);
            if (!resource.exists()) {
                log.warn("餐厅数据文件不存在: {}", DATA_FILE);
                return;
            }
            // JSON 可能以数组开头，也可能是单个对象
            var jsonNode = objectMapper.readTree(resource.getInputStream());
            if (jsonNode.isArray()) {
                restaurants = objectMapper.readValue(
                        resource.getInputStream(),
                        new TypeReference<List<RestaurantProfile>>() {});
            } else {
                var single = objectMapper.readValue(resource.getInputStream(), RestaurantProfile.class);
                restaurants = List.of(single);
            }
            log.info("RestaurantRepository 已加载 {} 家餐厅", restaurants.size());
        } catch (Exception e) {
            log.error("加载餐厅数据失败", e);
        }
    }

    /**
     * 按名称模糊查找餐厅。
     * 例如：视觉识别"壳泰·泰式船粉"可匹配到"売泰"。
     */
    public Optional<RestaurantProfile> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String normalized = name.toLowerCase().replaceAll("[·•\\s]", "");

        for (RestaurantProfile r : restaurants) {
            String rName = r.restaurantName().toLowerCase().replaceAll("[·•\\s]", "");
            // 双向包含检测
            if (normalized.contains(rName) || rName.contains(normalized)) {
                return Optional.of(r);
            }

            // 字符重叠度兜底：共同字符数 / 较短名字长度 >= 50%
            Set<Character> normChars = new HashSet<>();
            Set<Character> restChars = new HashSet<>();
            for (char c : normalized.toCharArray()) normChars.add(c);
            for (char c : rName.toCharArray()) restChars.add(c);
            normChars.retainAll(restChars);
            int commonCount = normChars.size();
            int minLen = Math.min(normalized.length(), rName.length());
            if (minLen > 0 && (double) commonCount / minLen >= 0.5) {
                log.debug("字符重叠度匹配: {} (归一化) ~ {} (数据库), 重叠={}/{}={}%",
                        normalized, rName, commonCount, minLen,
                        String.format("%.0f", (double) commonCount / minLen * 100));
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }
}
