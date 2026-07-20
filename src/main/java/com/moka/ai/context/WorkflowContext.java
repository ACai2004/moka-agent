package com.moka.ai.context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流上下文。
 * <p>
 * 所有 Agent 之间传递数据的统一容器（第 15.2.2 节）。
 * 使用 class 而非 record，因为需要在 Workflow 执行过程中逐步填充各字段。
 * <p>
 * 数据流：photoBase64 → [1] order → [2] dishes → [3] realtime
 *         → [4] experience → [5] plan → [6] runtimePrompt
 */
public class WorkflowContext {

    // === 原始输入 ===
    private String photoBase64;

    // === Layer 1：原始事实 ===
    private OrderData order;
    private List<DishKnowledge> dishes;
    private RealtimeInfo realtime;

    // === Layer 2：体验理解 ===
    private ExperienceUnderstanding experience;

    // === Layer 3：对话策略 ===
    private ConversationPlan plan;

    // === 最终输出 ===
    private String runtimePrompt;

    // === 预留 ===
    private Map<String, Object> metadata = new HashMap<>();
    private int executionRound = 0;

    // --- getters / setters / fluent setters ---

    public String getPhotoBase64() { return photoBase64; }
    public void setPhotoBase64(String photoBase64) { this.photoBase64 = photoBase64; }
    public WorkflowContext withPhotoBase64(String photoBase64) { this.photoBase64 = photoBase64; return this; }

    public OrderData getOrder() { return order; }
    public void setOrder(OrderData order) { this.order = order; }
    public WorkflowContext withOrder(OrderData order) { this.order = order; return this; }

    public List<DishKnowledge> getDishes() { return dishes; }
    public void setDishes(List<DishKnowledge> dishes) { this.dishes = dishes; }
    public WorkflowContext withDishes(List<DishKnowledge> dishes) { this.dishes = dishes; return this; }

    public RealtimeInfo getRealtime() { return realtime; }
    public void setRealtime(RealtimeInfo realtime) { this.realtime = realtime; }
    public WorkflowContext withRealtime(RealtimeInfo realtime) { this.realtime = realtime; return this; }

    public ExperienceUnderstanding getExperience() { return experience; }
    public void setExperience(ExperienceUnderstanding experience) { this.experience = experience; }
    public WorkflowContext withExperience(ExperienceUnderstanding experience) { this.experience = experience; return this; }

    public ConversationPlan getPlan() { return plan; }
    public void setPlan(ConversationPlan plan) { this.plan = plan; }
    public WorkflowContext withPlan(ConversationPlan plan) { this.plan = plan; return this; }

    public String getRuntimePrompt() { return runtimePrompt; }
    public void setRuntimePrompt(String runtimePrompt) { this.runtimePrompt = runtimePrompt; }
    public WorkflowContext withRuntimePrompt(String runtimePrompt) { this.runtimePrompt = runtimePrompt; return this; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public int getExecutionRound() { return executionRound; }
    public void setExecutionRound(int executionRound) { this.executionRound = executionRound; }
}
