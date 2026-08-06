package com.moka.simulation;

import com.moka.ai.context.DishItem;
import com.moka.ai.context.DishKnowledge;
import com.moka.ai.context.DishRole;
import com.moka.ai.context.OrderData;
import com.moka.ai.context.RuntimePrompt;
import com.moka.ai.context.WorkflowContext;
import com.moka.ai.workflow.ContextPreparationWorkflow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * 模拟：业务系统按契约 inputSchema 传入结构化订单 + 菜品知识 + 定位 + 氛围/服务，
 * 端到端跑通 Pre-call 工作流，验证数据能否流入最终 Runtime Prompt。
 * <p>
 * 对应契约：docs/漫谈-Agent-场景接口契约.md 第 3.2 章输入 Schema。
 * 运行需真实 Key（DEEPSEEK_API_KEY / OPENROUTER_API_KEY / GAODE_API_KEY）。
 */
@SpringBootTest(properties = {
        // 工作流不依赖数据库/Redis（餐厅/菜品数据来自 JSON 文件），排除以免本地连接失败
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
public class InvokeSimulationTest {

    @Autowired
    private ContextPreparationWorkflow workflow;

    @Test
    void simulateInvoke() {
        // ===== 构造符合契约 inputSchema 的输入（业务系统视角）=====

        // 订单：restaurant / time / people / duration / items
        List<DishItem> items = List.of(
                new DishItem("打抛饭（不可免辣）", 1, null, "加牛肉", "主食", "38"),
                new DishItem("牛肉船粉", 1, null, null, "主食", "48"),
                new DishItem("可乐（罐装）", 2, null, null, "饮品", "10")
        );
        OrderData order = new OrderData("売泰", "周五 19:20", 3, items, "1h30min");

        // 菜品知识：items[].features / experienceTags / dishRole（业务系统从自己菜品 DB 传入）
        List<DishKnowledge> dishes = List.of(
                new DishKnowledge("打抛饭（不可免辣）", DishRole.SIGNATURE,
                        List.of("经典打抛饭", "酸辣开胃", "泰式香料"),
                        List.of("人气必点", "下饭")),
                new DishKnowledge("牛肉船粉", DishRole.MAIN,
                        List.of("泰北风味", "牛肉香气"),
                        List.of("招牌", "暖胃")),
                new DishKnowledge("可乐（罐装）", DishRole.DRINK,
                        List.of(), List.of())
        );

        // 定位 + 氛围/服务（params 层，业务系统传入）
        WorkflowContext ctx = new WorkflowContext()
                .withOrder(order)
                .withDishes(dishes)
                .withDistrict("朝阳区")
                .withCity("北京")
                .withEnvironmentFeatures(List.of("复古海报", "马赛克瓷砖", "藤编灯具"))
                .withServiceFeatures(List.of("快餐型服务", "点餐上菜效率高"));

        // ===== 端到端执行工作流（真实 DeepSeek / 高德）=====
        RuntimePrompt prompt = workflow.execute(ctx);

        System.out.println("\n\n========== 模拟端到端输出：Runtime Prompt ==========\n");
        System.out.println(prompt.finalPrompt());
        System.out.println("\n========== 结束 ==========");
    }
}
