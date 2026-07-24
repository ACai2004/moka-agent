package com.moka.ai.workflow;

import com.moka.ai.agent.ExperienceUnderstandingAgent;
import com.moka.ai.context.ExperienceUnderstanding;
import com.moka.ai.context.WorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * [4] 体验理解节点。
 * <p>
 * 调用 DeepSeek 推理本次用餐可能的体验场景。
 * 仅在 Real 模式下生效（依赖 ExperienceUnderstandingAgent，该 Bean 在 Mock 模式下不存在）。
 */
@Component("ExperienceNode")
@ConditionalOnProperty(name = "moka.llm.mock", havingValue = "false")
public class ExperienceNode implements WorkflowNode {

    private static final Logger log = LoggerFactory.getLogger(ExperienceNode.class);

    private final ExperienceUnderstandingAgent experienceAgent;

    public ExperienceNode(ExperienceUnderstandingAgent experienceAgent) {
        this.experienceAgent = experienceAgent;
    }

    @Override
    public String nodeName() { return "ExperienceNode"; }

    @Override
    public WorkflowContext execute(WorkflowContext ctx) {
        ExperienceUnderstanding experience = experienceAgent.analyze(
                ctx.getOrder(), ctx.getDishes(), ctx.getRealtime());
        ctx.setExperience(experience);
        log.info("[ExperienceNode] {} 条可能性",
                experience != null ? experience.possibilities().size() : 0);
        return ctx;
    }

    @Override
    public List<String> dependsOn() {
        return List.of("OrderNode", "DishNode", "RealtimeNode");
    }
}
