package com.moka.ai.context;

import java.util.List;

/**
 * 订单解析结果。
 * <p>
 * 来源：OrderUnderstandingAgent [1] 通过 LLM 视觉能力解析小票照片后输出。
 * 时间与时长字段使用 String 类型，因为 LLM OCR 输出可能是非标准格式（如"周五 19:20"、"2h15min"）。
 *
 * @param restaurant 餐厅名称
 * @param time       用餐时间，如"周五 19:20"
 * @param people     用餐人数
 * @param items      菜品列表（保留完整小票信息，不做压缩）
 * @param duration   用餐时长，如"2h15min"
 */
public record OrderData(
        String restaurant,
        String time,
        int people,
        List<DishItem> items,
        String duration
) {
}
