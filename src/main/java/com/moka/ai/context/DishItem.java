package com.moka.ai.context;

/**
 * 单道菜品的完整信息。
 * <p>
 * 来源：OrderUnderstandingAgent [1] 解析小票照片后输出。
 * 保留小票原始细节，不做压缩，为对话提供丰富的自然切入点。
 * 所有可选字段（spiceLevel, notes, category, price）保留 null 语义——小票没写就是 null。
 *
 * @param name        菜品名称（必填）
 * @param quantity    数量，默认 1
 * @param spiceLevel  辣度要求（可选），如"不可免辣"、"微辣"
 * @param notes       备注（可选），如"加牛肉"、"不要香菜"
 * @param category    品类（可选），如"主食"、"饮品"、"小菜"
 * @param price       单价（可选），String 类型，LLM OCR 输出可能为"38"或"38.00"
 */
public record DishItem(
        String name,
        int quantity,
        String spiceLevel,
        String notes,
        String category,
        String price
) {
    public DishItem {
        quantity = quantity <= 0 ? 1 : quantity;
    }

    /**
     * 快捷构造：只有菜名时的简化创建方式。
     */
    public DishItem(String name) {
        this(name, 1, null, null, null, null);
    }
}
