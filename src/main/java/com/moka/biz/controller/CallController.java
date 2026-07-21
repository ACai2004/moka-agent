package com.moka.biz.controller;

import com.moka.ai.context.RuntimePrompt;
import com.moka.ai.workflow.ContextPreparationWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

/**
 * 通话相关 API。
 * <p>
 * 提供 Runtime Prompt 预览和通话触发接口，内部委托给 ContextPreparationWorkflow 执行完整 Pipeline。
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
     * 预览完整 Runtime Prompt（使用预设 fallback 数据，无需上传照片）。
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

    /**
     * 上传小票照片，触发完整 Pipeline，返回运行时 Prompt。
     */
    @PostMapping("/prepare")
    public ResponseEntity<RuntimePrompt> prepare(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            String base64 = Base64.getEncoder().encodeToString(file.getBytes());
            RuntimePrompt prompt = workflow.execute(base64);
            return ResponseEntity.ok(prompt);
        } catch (IOException e) {
            log.error("读取上传文件失败", e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("生成 Runtime Prompt 失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
