package com.moka.biz.controller;

/**
 * Tool API 标准化响应格式。
 * <p>
 * 所有 Tool 端点统一使用此格式返回。
 *
 * @param status       "success" / "degraded" / "error"
 * @param data         工具返回的数据（成功时）
 * @param errorMessage 错误描述（失败时）
 */
public record ToolResponse(
        String status,
        Object data,
        String errorMessage
) {

    /** 快速构建成功响应。 */
    public static ToolResponse success(Object data) {
        return new ToolResponse("success", data, null);
    }

    /** 快速构建降级响应（工具可用但结果不完整）。 */
    public static ToolResponse degraded(Object data, String message) {
        return new ToolResponse("degraded", data, message);
    }

    /** 快速构建错误响应。 */
    public static ToolResponse error(String message) {
        return new ToolResponse("error", null, message);
    }
}
