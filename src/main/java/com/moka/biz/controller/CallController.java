package com.moka.biz.controller;

import com.moka.ai.context.RuntimePrompt;
import com.moka.ai.workflow.ContextPreparationWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通话相关 API。
 * <p>
 * 提供 Runtime Prompt 预览接口，内部委托给 ContextPreparationWorkflow 执行完整 Pipeline。
 * 仅当 {@code moka.llm.mock=false} 时可用。
 */
@RestController
@ConditionalOnProperty(name = "moka.llm.mock", havingValue = "false")
@RequestMapping("/api/v1/calls")
public class CallController {

    private static final Logger log = LoggerFactory.getLogger(CallController.class);

    private final ContextPreparationWorkflow workflow;

    public CallController(ContextPreparationWorkflow workflow) {
        this.workflow = workflow;
    }

    /**
     * 预览完整 Runtime Prompt（使用预设测试数据）。
     */
    @PostMapping("/preview")
    public ResponseEntity<RuntimePrompt> preview() {
        try {
            RuntimePrompt prompt = workflow.execute("");
            return ResponseEntity.ok(prompt);
        } catch (Exception e) {
            log.error("预览 Runtime Prompt 失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
