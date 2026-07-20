package com.moka.ai.context;

/**
 * 体验可能性的确定性级别。
 * <p>
 * 依据架构文档第 4.2 节：所有推理保持不确定性，不允许输出确定性结论。
 * 因此 LOW 和 MEDIUM 可用，HIGH 不可用（不在枚举中定义，从类型层面防止误用）。
 */
public enum ConfidenceLevel {
    LOW,
    MEDIUM
}
