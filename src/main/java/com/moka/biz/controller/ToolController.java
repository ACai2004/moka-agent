package com.moka.biz.controller;

import com.moka.ai.retrieval.DishRetriever;
import com.moka.ai.retrieval.RestaurantRepository;
import com.moka.ai.tools.WeatherTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Tool API 控制器。
 * <p>
 * 将 Java 后端的内部工具暴露为标准 REST API，供 Python Runtime Agent 调用。
 * 三个端点均包含参数校验和错误降级处理。
 * <p>
 * 路径前缀：/api/v1/tools
 */
@RestController
@RequestMapping("/api/v1/tools")
public class ToolController {

    private static final Logger log = LoggerFactory.getLogger(ToolController.class);

    private final WeatherTool weatherTool;
    private final DishRetriever dishRetriever;
    private final RestaurantRepository restaurantRepository;

    public ToolController(WeatherTool weatherTool,
                          DishRetriever dishRetriever,
                          RestaurantRepository restaurantRepository) {
        this.weatherTool = weatherTool;
        this.dishRetriever = dishRetriever;
        this.restaurantRepository = restaurantRepository;
    }

    // ================================================================
    //  天气查询
    // ================================================================

    /**
     * 查询行政区级别天气。
     * <p>
     * 内部调用 WeatherTool.getDistrictWeather()，含高德 → wttr.in 自动降级。
     * 两级都失败时返回 degraded 状态。
     *
     * @param district 行政区名，如"朝阳区"（必填）
     * @param city     城市名，如"北京"（可选，默认"北京"）
     */
    @GetMapping("/weather")
    public ResponseEntity<ToolResponse> getWeather(
            @RequestParam String district,
            @RequestParam(defaultValue = "北京") String city) {

        if (district == null || district.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ToolResponse.error("district 参数不能为空"));
        }

        try {
            String result = weatherTool.getDistrictWeather(district, city);

            if (result == null || result.isBlank() || "未知".equals(result)) {
                log.warn("天气查询降级: district={}, city={}", district, city);
                return ResponseEntity.ok(
                        ToolResponse.degraded("未知", "天气服务暂时不可用"));
            }

            return ResponseEntity.ok(ToolResponse.success(result));

        } catch (Exception e) {
            log.error("天气查询异常: district={}, city={}", district, city, e);
            return ResponseEntity.ok(
                    ToolResponse.degraded("未知", "天气查询异常: " + e.getMessage()));
        }
    }

    // ================================================================
    //  菜品知识搜索
    // ================================================================

    /**
     * 搜索菜品知识。
     * <p>
     * 内部调用 DishRetriever.retrieve()，精确匹配菜品名。
     * 未匹配到时返回空列表（不是错误）。
     *
     * @param name 菜品名（必填）
     */
    @GetMapping("/dish-search")
    public ResponseEntity<ToolResponse> searchDish(@RequestParam String name) {

        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ToolResponse.error("name 参数不能为空"));
        }

        try {
            List<?> results = dishRetriever.retrieve(List.of(name));
            return ResponseEntity.ok(ToolResponse.success(results));

        } catch (Exception e) {
            log.error("菜品搜索异常: name={}", name, e);
            return ResponseEntity.ok(
                    ToolResponse.degraded(List.of(), "菜品搜索异常: " + e.getMessage()));
        }
    }

    // ================================================================
    //  餐厅信息查询
    // ================================================================

    /**
     * 查询餐厅信息。
     * <p>
     * 内部调用 RestaurantRepository.findByName()，模糊匹配餐厅名。
     * 未匹配到时返回 degraded 状态。
     *
     * @param name 餐厅名（必填）
     */
    @GetMapping("/restaurant")
    public ResponseEntity<ToolResponse> getRestaurant(@RequestParam String name) {

        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ToolResponse.error("name 参数不能为空"));
        }

        try {
            Optional<?> result = restaurantRepository.findByName(name);

            if (result.isEmpty()) {
                return ResponseEntity.ok(
                        ToolResponse.degraded(null, "未找到餐厅: " + name));
            }

            return ResponseEntity.ok(ToolResponse.success(result.get()));

        } catch (Exception e) {
            log.error("餐厅查询异常: name={}", name, e);
            return ResponseEntity.ok(
                    ToolResponse.degraded(null, "餐厅查询异常: " + e.getMessage()));
        }
    }
}
