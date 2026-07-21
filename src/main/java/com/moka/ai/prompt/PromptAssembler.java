package com.moka.ai.prompt;

import com.moka.ai.context.ContextAssembler;
import com.moka.ai.context.RuntimePrompt;
import com.moka.ai.context.WorkflowContext;
import org.springframework.stereotype.Component;

/**
 * Runtime Prompt 组装器。
 * <p>
 * 将 Static System Prompt 和 Dynamic Context（三层）合并为完整的 Runtime Prompt，
 * 最终传递给火山引擎 Voice Agent。
 * <p>
 * 合并策略（方案 B，第 3.1 节）：
 * Runtime Prompt = Static System Prompt + 分隔符 + Dynamic Context
 */
@Component
public class PromptAssembler {

    private static final String SEPARATOR = "\n\n---\n\n以下是用餐背景信息：\n\n";

    private final SystemPromptLoader systemPromptLoader;
    private final ContextAssembler contextAssembler;

    public PromptAssembler(SystemPromptLoader systemPromptLoader, ContextAssembler contextAssembler) {
        this.systemPromptLoader = systemPromptLoader;
        this.contextAssembler = contextAssembler;
    }

    /**
     * 组装最终 Runtime Prompt。
     *
     * @param ctx 工作流上下文
     * @return 封装好的 Runtime Prompt，包含完整文本和元数据
     */
    public RuntimePrompt assemble(WorkflowContext ctx) {
        long start = System.currentTimeMillis();

        String systemPrompt = systemPromptLoader.load();
        String dynamicContext = contextAssembler.assemble(ctx);
        String finalPrompt = systemPrompt + SEPARATOR + dynamicContext;

        long duration = System.currentTimeMillis() - start;

        return new RuntimePrompt(
                finalPrompt,
                "v1",
                System.currentTimeMillis(),
                (int) duration
        );
    }
}
