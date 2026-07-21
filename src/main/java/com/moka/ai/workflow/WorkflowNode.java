package com.moka.ai.workflow;

import com.moka.ai.context.WorkflowContext;

/**
 * Workflow 节点接口。
 * <p>
 * 每个节点封装一个独立的处理步骤，接收当前 WorkflowContext，
 * 填充自己的输出字段后返回。
 * <p>
 * 节点通过 nodeName() 标识自己，用于日志和错误追踪。
 */
public interface WorkflowNode {

    /** 节点名称，用于日志标识 */
    String nodeName();

    /** 执行节点逻辑，从 ctx 读取输入，写入输出 */
    WorkflowContext execute(WorkflowContext ctx);

    /** 该节点是否可跳过（失败时走 fallback） */
    default boolean isOptional() { return false; }

    /** 可选节点失败时的降级处理 */
    default WorkflowContext fallback(WorkflowContext ctx) { return ctx; }
}
