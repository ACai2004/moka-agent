package com.moka.ai.context;

import com.moka.ai.retrieval.RestaurantRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Dynamic Context 三层组装器。
 * <p>
 * 将 WorkflowContext 中的数据组装为纯文本格式的 Dynamic Context。
 * 按 Layer 1（Raw Facts）→ Layer 2（Experience Understanding）→ Layer 3（Conversation Plan）顺序拼接。
 * <p>
 * 输出为纯文本，不包含 JSON 或 Markdown 标记。
 */
@Component
public class ContextAssembler {

    private final RestaurantRepository restaurantRepository;

    public ContextAssembler(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    /**
     * 组装三层 Dynamic Context。
     *
     * @param ctx 工作流上下文（各 Agent 的输出）
     * @return 纯文本格式的 Dynamic Context
     */
    public String assemble(WorkflowContext ctx) {
        StringBuilder sb = new StringBuilder();

        assembleLayer1(sb, ctx);
        sb.append("\n");
        assembleLayer2(sb, ctx);
        sb.append("\n");
        assembleLayer3(sb, ctx);

        return sb.toString().strip();
    }

    // ========== Layer 1: Raw Facts ==========

    private void assembleLayer1(StringBuilder sb, WorkflowContext ctx) {
        OrderData order = ctx.getOrder();
        RealtimeInfo realtime = ctx.getRealtime();

        sb.append("--- 用餐信息 ---\n");
        sb.append("餐厅：").append(order.restaurant()).append("\n");

        // 餐厅环境与服务信息：优先用业务系统传入的，否则查漫谈本地餐厅库（demo 路径）
        Optional<RestaurantProfile> restaurant = restaurantRepository.findByName(order.restaurant());
        List<String> envFeatures = (ctx.getEnvironmentFeatures() != null && !ctx.getEnvironmentFeatures().isEmpty())
                ? ctx.getEnvironmentFeatures()
                : (restaurant.isPresent() ? restaurant.get().environmentFeatures() : null);
        if (envFeatures != null && !envFeatures.isEmpty()) {
            sb.append("氛围：").append(String.join("、", envFeatures)).append("\n");
        }
        List<String> svcFeatures = (ctx.getServiceFeatures() != null && !ctx.getServiceFeatures().isEmpty())
                ? ctx.getServiceFeatures()
                : (restaurant.isPresent() ? restaurant.get().serviceFeatures() : null);
        if (svcFeatures != null && !svcFeatures.isEmpty()) {
            sb.append("服务：").append(String.join("、", svcFeatures)).append("\n");
        }

        sb.append("时间：").append(order.time()).append("\n");
        sb.append("人数：").append(order.people()).append(" 人\n");
        if (order.duration() != null && !order.duration().isBlank()) {
            sb.append("用餐时长：").append(order.duration()).append("\n");
        }

        // 构建菜品名→菜品知识 Map（只包含用户点的菜）
        Map<String, DishKnowledge> knowledgeMap = ctx.getDishes() != null
                ? ctx.getDishes().stream()
                        .collect(Collectors.toMap(DishKnowledge::dishName, dk -> dk, (a, b) -> a))
                : Map.of();

        sb.append("菜品：\n");
        for (DishItem item : order.items()) {
            String line = formatDishItem(item, knowledgeMap.get(item.name()));
            sb.append("  · ").append(line).append("\n");
        }

        if (realtime != null) {
            if (realtime.weather() != null && !realtime.weather().isBlank()) {
                sb.append("当日天气：").append(realtime.weather()).append("\n");
            }
            if (realtime.holiday() != null && !realtime.holiday().isBlank()) {
                sb.append("临近节日：").append(realtime.holiday()).append("\n");
            }
            if (realtime.currentTime() != null && !realtime.currentTime().isBlank()) {
                sb.append("当前时间：").append(realtime.currentTime()).append("\n");
            }
        }
    }

    private String formatDishItem(DishItem item, DishKnowledge knowledge) {
        StringBuilder line = new StringBuilder(item.name());
        line.append(" ×").append(item.quantity());

        // 辣度与备注
        if (item.spiceLevel() != null && !item.spiceLevel().isBlank()) {
            line.append("（辣度：").append(item.spiceLevel()).append("）");
        }
        if (item.notes() != null && !item.notes().isBlank()) {
            if (item.spiceLevel() != null && !item.spiceLevel().isBlank()) {
                line.append("，备注：").append(item.notes());
            } else {
                line.append("（备注：").append(item.notes()).append("）");
            }
        }

        // 菜品特点（如果有知识匹配）
        if (knowledge != null) {
            List<String> tags = knowledge.features();
            if (tags != null && !tags.isEmpty()) {
                line.append(" —— ");
                line.append(String.join("、", tags.subList(0, Math.min(tags.size(), 3))));
            }
            List<String> expTags = knowledge.experienceTags();
            if (expTags != null && !expTags.isEmpty()) {
                line.append("，").append(String.join("、", expTags.subList(0, Math.min(expTags.size(), 2))));
            }
        }

        return line.toString();
    }

    // ========== Layer 2: Experience Understanding ==========

    private void assembleLayer2(StringBuilder sb, WorkflowContext ctx) {
        ExperienceUnderstanding exp = ctx.getExperience();
        if (exp == null || exp.possibilities() == null || exp.possibilities().isEmpty()) {
            sb.append("--- 体验理解 ---\n");
            sb.append("（暂无体验理解数据）\n");
            return;
        }

        sb.append("--- 体验理解 ---\n");
        for (ExperiencePossibility p : exp.possibilities()) {
            String prefix = switch (p.confidenceLevel()) {
                case MEDIUM -> "推测";
                case LOW -> "参考";
            };
            sb.append("- ").append(prefix).append("：").append(p.description());
            if (p.evidenceSource() != null && !p.evidenceSource().isBlank()) {
                sb.append("（依据：").append(p.evidenceSource()).append("）");
            }
            sb.append("\n");
        }
    }

    // ========== Layer 3: Conversation Plan ==========

    private void assembleLayer3(StringBuilder sb, WorkflowContext ctx) {
        ConversationPlan plan = ctx.getPlan();
        if (plan == null) {
            sb.append("--- 对话策略 ---\n");
            sb.append("（暂无对话策略）\n");
            return;
        }

        sb.append("--- 对话策略 ---\n");

        if (plan.directions() != null && !plan.directions().isEmpty()) {
            sb.append("方向：\n");
            for (String d : plan.directions()) {
                sb.append("  - ").append(d).append("\n");
            }
        }

        if (plan.availableHooks() != null && !plan.availableHooks().isEmpty()) {
            sb.append("机会点：\n");
            for (String h : plan.availableHooks()) {
                sb.append("  - ").append(h).append("\n");
            }
        }

        if (plan.avoid() != null && !plan.avoid().isEmpty()) {
            sb.append("限制：\n");
            for (String a : plan.avoid()) {
                sb.append("  - ").append(a).append("\n");
            }
        }
    }
}
