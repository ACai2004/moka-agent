package com.moka.ai.workflow;

import com.moka.ai.context.RuntimePrompt;
import com.moka.ai.context.WorkflowContext;
import com.moka.ai.prompt.PromptAssembler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * [6] Prompt 组装节点。
 * <p>
 * 将 Static System Prompt + 三层 Dynamic Context 合并为最终 Runtime Prompt。
 * 将完整 RuntimePrompt 对象存入 metadata，供 DemoController 和 Workflow 返回使用。
 */
@Component("AssemblyNode")
public class AssemblyNode implements WorkflowNode {

    private static final Logger log = LoggerFactory.getLogger(AssemblyNode.class);

    private final PromptAssembler promptAssembler;

    public AssemblyNode(PromptAssembler promptAssembler) {
        this.promptAssembler = promptAssembler;
    }

    @Override
    public String nodeName() { return "AssemblyNode"; }

    @Override
    public WorkflowContext execute(WorkflowContext ctx) {
        RuntimePrompt prompt = promptAssembler.assemble(ctx);
        ctx.setRuntimePrompt(prompt.finalPrompt());
        // 将完整对象存入 metadata，供 DemoController 和 Workflow 返回使用
        ctx.getMetadata().put("fullRuntimePrompt", prompt);
        log.info("[AssemblyNode] {} 字符", prompt.finalPrompt().length());
        return ctx;
    }

    @Override
    public List<String> dependsOn() {
        return List.of("OrderNode", "DishNode", "RealtimeNode", "ExperienceNode", "PlannerNode");
    }
}
