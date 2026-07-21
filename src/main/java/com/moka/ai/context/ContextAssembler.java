package com.moka.ai.context;

import org.springframework.stereotype.Component;

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
        sb.append("时间：").append(order.time()).append("\n");
        sb.append("人数：").append(order.people()).append(" 人\n");
        sb.append("用餐时长：").append(order.duration()).append("\n");

        sb.append("菜品：\n");
        for (DishItem item : order.items()) {
            String line = formatDishItem(item);
            sb.append("  · ").append(line).append("\n");
        }

        if (realtime != null) {
            if (realtime.weather() != null && !realtime.weather().isBlank()) {
                sb.append("当日天气：").append(realtime.weather()).append("\n");
            }
            if (realtime.holiday() != null && !realtime.holiday().isBlank()) {
                sb.append("临近节日：").append(realtime.holiday()).append("\n");
            }
        }
    }

    private String formatDishItem(DishItem item) {
        StringBuilder line = new StringBuilder(item.name());
        line.append(" ×").append(item.quantity());

        if (item.spiceLevel() != null && !item.spiceLevel().isBlank()) {
            line.append("（辣度：").append(item.spiceLevel()).append("）");
        }
        if (item.notes() != null && !item.notes().isBlank()) {
            // 如果已有辣度信息，备注跟在后面
            if (item.spiceLevel() != null && !item.spiceLevel().isBlank()) {
                line.append("，备注：").append(item.notes());
            } else {
                line.append("（备注：").append(item.notes()).append("）");
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
            sb.append("- [").append(p.confidenceLevel()).append("] ");
            sb.append(p.description());
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
