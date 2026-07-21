package com.moka.ai.agent;

import com.moka.ai.context.OrderData;
import com.moka.common.mock.MockLlmService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock 订单理解服务。
 * <p>
 * 当 {@code moka.llm.mock=true} 时生效，返回预设的订单数据。
 */
@Component
@ConditionalOnProperty(name = "moka.llm.mock", havingValue = "true")
public class MockOrderUnderstandingService implements OrderUnderstandingService {

    private final MockLlmService mockLlmService;

    public MockOrderUnderstandingService(MockLlmService mockLlmService) {
        this.mockLlmService = mockLlmService;
    }

    @Override
    public OrderData analyzeOrder(String photoBase64) {
        return mockLlmService.mockOrderData();
    }
}
