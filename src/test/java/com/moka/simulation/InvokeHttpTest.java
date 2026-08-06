package com.moka.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端 HTTP 验证：
 * access_token → 查场景定义 → 场景调起（真实 DeepSeek / 高德）。
 * 模拟业务系统按契约 inputSchema 调用（含 features / experienceTags / dishRole / district/city / 氛围服务）。
 * 运行需真实 Key（DEEPSEEK_API_KEY / OPENROUTER_API_KEY / GAODE_API_KEY）。
 */
@SpringBootTest(properties = {
        // 工作流不依赖数据库/Redis，排除以免本地连接失败
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
public class InvokeHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void endToEndHttp() throws Exception {
        // ===== 1. 获取 Token =====
        MvcResult tokenResult = mockMvc.perform(post("/api/v1/access_token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appKey\":\"mantan-app\",\"appSecret\":\"mantan-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(1))
                .andReturn();
        JsonNode tokenJson = objectMapper.readTree(tokenResult.getResponse().getContentAsString());
        String token = tokenJson.path("data").path("accessToken").asText();
        System.out.println("== ① access_token 获取成功");

        // ===== 2. 查场景定义 =====
        mockMvc.perform(get("/api/v1/scenarios/MANTAN_DINING_RECALL")
                        .header("AccessToken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(1))
                .andExpect(jsonPath("$.data.inputSchema").isString())
                .andExpect(jsonPath("$.data.inputSchema").value(
                        org.hamcrest.Matchers.containsString("features")))
                .andExpect(jsonPath("$.data.outputSchema").isString());
        System.out.println("== ② 查场景定义成功（inputSchema 含 features 字段）");

        // ===== 3. 场景调起（真实 DeepSeek / 高德）=====
        String body = "{\"scenarioNo\":\"MANTAN_DINING_RECALL\",\"params\":{"
                + "\"orderNo\":\"ORD-20260805-00123\",\"restaurant\":\"売泰\","
                + "\"district\":\"朝阳区\",\"city\":\"北京\","
                + "\"environmentFeatures\":[\"复古海报\",\"马赛克瓷砖\",\"藤编灯具\"],"
                + "\"serviceFeatures\":[\"快餐型服务\",\"点餐上菜效率高\"],"
                + "\"time\":\"周五 19:20\",\"people\":3,\"duration\":\"1h30min\","
                + "\"items\":["
                + "{\"name\":\"打抛饭（不可免辣）\",\"quantity\":1,\"notes\":\"加牛肉\",\"category\":\"主食\",\"price\":\"38\","
                + "\"features\":[\"经典打抛饭\",\"酸辣开胃\",\"泰式香料\"],\"experienceTags\":[\"人气必点\",\"下饭\"],\"dishRole\":\"SIGNATURE\"},"
                + "{\"name\":\"牛肉船粉\",\"quantity\":1,\"category\":\"主食\",\"price\":\"48\","
                + "\"features\":[\"泰北风味\",\"牛肉香气\"],\"experienceTags\":[\"招牌\",\"暖胃\"],\"dishRole\":\"MAIN\"},"
                + "{\"name\":\"可乐（罐装）\",\"quantity\":2,\"category\":\"饮品\",\"price\":\"10\"}"
                + "]}}";

        MvcResult invokeResult = mockMvc.perform(post("/api/v1/scenarios/invoke")
                        .header("AccessToken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(1))
                .andReturn();
        JsonNode invokeJson = objectMapper.readTree(invokeResult.getResponse().getContentAsString());
        String finalPrompt = invokeJson.path("data").path("finalPrompt").asText();
        System.out.println("== ③ invoke 成功，finalPrompt 长度: " + finalPrompt.length());

        // 打印 Dynamic Context 部分（从 "--- 用餐信息 ---" 开始）
        int idx = finalPrompt.indexOf("--- 用餐信息 ---");
        System.out.println("\n========== 最终 Runtime Prompt（Dynamic Context 部分）==========\n");
        System.out.println(idx >= 0 ? finalPrompt.substring(idx) : finalPrompt);
        System.out.println("\n========== 结束 ==========");

        // ===== 关键断言：传入的知识确实进入了提示词 =====
        Assertions.assertTrue(finalPrompt.contains("氛围：复古海报"), "氛围应来自传入的 environmentFeatures");
        Assertions.assertTrue(finalPrompt.contains("服务：快餐型服务"), "服务应来自传入的 serviceFeatures");
        Assertions.assertTrue(finalPrompt.contains("经典打抛饭"), "菜品特点应来自传入的 features");
        Assertions.assertTrue(finalPrompt.contains("人气必点"), "体验标签应来自传入的 experienceTags");
    }
}
