package com.moka.demo;

import com.moka.ai.context.RuntimePrompt;
import com.moka.ai.context.WorkflowContext;
import com.moka.ai.workflow.NodeOrderProvider;
import com.moka.ai.workflow.WorkflowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

/**
 * Demo 专用端点。
 * <p>
 * 按 moka-workflow.yml 配置的节点顺序执行完整 Pipeline，
 * 返回每一步的中间产物供前端可视化展示。
 * 节点顺序与 ContextPreparationWorkflow 保持完全一致（共享同一份配置）。
 * <p>
 * 仅在 {@code moka.llm.mock=false} 时生效。
 */
@RestController
@RequestMapping("/api/v1/calls")
@ConditionalOnProperty(name = "moka.llm.mock", havingValue = "false")
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    private final NodeOrderProvider nodeOrderProvider;
    private final ApplicationContext applicationContext;

    public DemoController(NodeOrderProvider nodeOrderProvider,
                          ApplicationContext applicationContext) {
        this.nodeOrderProvider = nodeOrderProvider;
        this.applicationContext = applicationContext;
    }

    /**
     * 执行完整 Demo Pipeline。
     * 按配置顺序遍历所有节点，自动收集每一步的中间结果。
     */
    @PostMapping("/demo")
    public ResponseEntity<DemoResponse> demo(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            String base64 = Base64.getEncoder().encodeToString(file.getBytes());
            WorkflowContext ctx = new WorkflowContext();
            ctx.setPhotoBase64(base64);

            log.info("===== Demo Pipeline 开始（配置驱动）=====");
            long pipelineStart = System.currentTimeMillis();

            for (String nodeName : nodeOrderProvider.getNodeOrder()) {
                Object bean = applicationContext.getBean(nodeName);
                if (!(bean instanceof WorkflowNode workflowNode)) {
                    log.warn("[Demo] {} 不是 WorkflowNode，跳过", nodeName);
                    continue;
                }

                long start = System.currentTimeMillis();
                log.info("[Demo] [{}] 开始执行...", nodeName);

                ctx = workflowNode.execute(ctx);

                long elapsed = System.currentTimeMillis() - start;
                log.info("[Demo] [{}] 完成 ({}ms)", nodeName, elapsed);
            }

            long totalElapsed = System.currentTimeMillis() - pipelineStart;
            log.info("===== Demo Pipeline 完成（{}ms）=====", totalElapsed);

            // 从 metadata 读取 AssemblyNode 存放的完整 RuntimePrompt
            RuntimePrompt prompt = (RuntimePrompt) ctx.getMetadata().get("fullRuntimePrompt");

            DemoResponse response = new DemoResponse(
                    true,
                    ctx.getOrder(),
                    ctx.getDishes(),
                    ctx.getRealtime(),
                    ctx.getExperience(),
                    ctx.getPlan(),
                    prompt
            );
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("[Demo] 文件读取失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("[Demo] Pipeline 执行失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
