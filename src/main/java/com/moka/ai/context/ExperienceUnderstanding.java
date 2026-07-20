package com.moka.ai.context;

import java.util.List;

/**
 * 体验理解输出（Layer 2）。
 * <p>
 * 来源：ExperienceUnderstandingAgent [4] 的输出。
 * 将事实转换为体验可能性列表，所有可能性保持低确定性。
 *
 * @param possibilities 所有体验可能性
 */
public record ExperienceUnderstanding(
        List<ExperiencePossibility> possibilities
) {
}
