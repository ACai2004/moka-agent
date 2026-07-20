package com.moka.ai.context;

/**
 * 单条体验可能性。
 * <p>
 * 每条可能性必须保持低确定性，禁止将推测当作事实。
 *
 * @param description      可能性描述，如"可能存在多人聚餐场景，用户可能更关注整体氛围"
 * @param confidenceLevel  确定性级别，只允许 LOW 或 MEDIUM
 * @param evidenceSource   依据来源，如"订单显示 3 人用餐"
 */
public record ExperiencePossibility(
        String description,
        ConfidenceLevel confidenceLevel,
        String evidenceSource
) {
}
