package com.moka.ai.workflow;

import com.moka.ai.context.RuntimePrompt;
import com.moka.ai.context.WorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Context 准备工作流。
 * <p>
 * 从 NodeOrderProvider 读取配置的节点顺序，按序执行。
 * 每个节点从 ApplicationContext 按名称获取。
 * 启动时校验数据依赖关系。
 * <p>
 * 仅在 {@code moka.llm.mock=false} 时生效。
 * Mock 模式下由 ContextPreparationDemo 替代。
 */
@Component
@ConditionalOnProperty(name = "moka.llm.mock", havingValue = "false")
public class ContextPreparationWorkflow {

    private static final Logger log = LoggerFactory.getLogger(ContextPreparationWorkflow.class);

    private final List<WorkflowNode> nodes;

    public ContextPreparationWorkflow(
            NodeOrderProvider nodeOrderProvider,
            ApplicationContext applicationContext) {

        List<String> nodeNames = nodeOrderProvider.getNodeOrder();
        this.nodes = nodeNames.stream()
                .map(name -> {
                    Object bean = applicationContext.getBean(name);
                    if (!(bean instanceof WorkflowNode)) {
                        throw new IllegalStateException(
                                "Bean '" + name + "' 不是 WorkflowNode 类型");
                    }
                    return (WorkflowNode) bean;
                })
                .toList();

        validateDependencies(nodes);
        log.info("Workflow 节点加载完成（{} 个）: {}", nodes.size(), nodeNames);
    }

    /**
     * 执行完整 Workflow，生成 Runtime Prompt。
     *
     * @param photoBase64 小票照片的 base64 编码
     * @return 完整的 Runtime Prompt
     */
    public RuntimePrompt execute(String photoBase64) {
        log.info("===== ContextPreparationWorkflow 开始 =====");
        long start = System.currentTimeMillis();

        WorkflowContext ctx = new WorkflowContext();
        ctx.setPhotoBase64(photoBase64);

        for (WorkflowNode node : nodes) {
            try {
                log.debug("执行 Node: {}", node.nodeName());
                ctx = node.execute(ctx);
                log.debug("Node {} 完成", node.nodeName());
            } catch (Exception e) {
                if (node.isOptional()) {
                    log.warn("Node {} 失败，使用 fallback: {}", node.nodeName(), e.getMessage());
                    ctx = node.fallback(ctx);
                } else {
                    log.error("Node {} 失败，终止 Workflow", node.nodeName(), e);
                    throw new WorkflowExecutionException(
                            "Node " + node.nodeName() + " 失败", e);
                }
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("===== ContextPreparationWorkflow 完成（{}ms）=====", elapsed);

        // 从 metadata 读取 AssemblyNode 存放的完整 RuntimePrompt
        RuntimePrompt prompt = (RuntimePrompt) ctx.getMetadata().get("fullRuntimePrompt");
        if (prompt == null) {
            // fallback：用字符串重建（正常情况下不会到达这里）
            prompt = new RuntimePrompt(
                    ctx.getRuntimePrompt(), "v1",
                    System.currentTimeMillis(), (int) elapsed);
        }
        return prompt;
    }

    /**
     * 启动时校验：每个节点的 dependsOn() 声明的依赖节点必须在其之前执行。
     */
    private void validateDependencies(List<WorkflowNode> nodes) {
        List<String> executed = new ArrayList<>();
        boolean allValid = true;

        for (WorkflowNode node : nodes) {
            String nodeName = node.nodeName();
            for (String dep : node.dependsOn()) {
                if (!executed.contains(dep)) {
                    log.error("依赖校验失败: 节点 '{}' 依赖 '{}'，但 '{}' 尚未执行。"
                                    + "当前顺序: {}",
                            nodeName, dep, dep,
                            nodes.stream().map(WorkflowNode::nodeName).toList());
                    allValid = false;
                }
            }
            executed.add(nodeName);
        }

        if (!allValid) {
            throw new IllegalStateException(
                    "Workflow 节点顺序不满足数据依赖关系，请检查 moka-workflow.yml 配置");
        }

        log.info("Workflow 数据依赖校验通过");
    }
}
