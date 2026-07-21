package com.moka.ai.agent;

import com.moka.ai.context.OrderData;

/**
 * 订单理解服务。
 * <p>
 * 接收小票照片（base64 或 MultipartFile），返回结构化订单数据。
 * <p>
 * Mock 模式：返回预设数据。
 * 真实模式（Phase 3）：通过 @AiService 调用视觉 LLM 解析。
 */
public interface OrderUnderstandingService {

    /**
     * 分析订单照片，返回结构化订单数据。
     *
     * @param photoBase64 小票照片的 base64 编码
     * @return 结构化订单数据
     */
    OrderData analyzeOrder(String photoBase64);
}
