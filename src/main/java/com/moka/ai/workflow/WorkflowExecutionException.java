package com.moka.ai.workflow;

/**
 * Workflow 执行异常。
 * <p>
 * 当 Workflow 中必选节点执行失败时抛出，终止整个 Pipeline。
 */
public class WorkflowExecutionException extends RuntimeException {

    public WorkflowExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
