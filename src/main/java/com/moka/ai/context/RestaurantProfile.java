package com.moka.ai.context;

import java.util.List;

/**
 * 餐厅基本信息。
 * <p>
 * 来源：结构化数据库（非 RAG），信息稳定。
 * MVP 阶段可能还没有真实餐厅数据，此字段为可选。
 *
 * @param restaurantName       餐厅名称
 * @param address              地址，如"北京市朝阳区三里屯 T+MALL 负一层"
 * @param positioning          定位，如"社区型家常川菜"
 * @param experienceTags       体验标签，如["家庭聚餐", "轻松氛围"]
 * @param environmentFeatures  环境特征，如["暖色灯光", "门口炒瓜子"]
 * @param serviceFeatures      服务特征，如["主动添茶", "稳定上菜节奏"]
 */
public record RestaurantProfile(
        String restaurantName,
        String address,
        String positioning,
        List<String> experienceTags,
        List<String> environmentFeatures,
        List<String> serviceFeatures
) {
}
