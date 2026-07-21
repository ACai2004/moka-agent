package com.moka.biz.controller;

import com.moka.ai.agent.OrderUnderstandingService;
import com.moka.ai.context.OrderData;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

/**
 * 订单相关 API。
 * <p>
 * 提供订单照片上传接口，触发 OrderUnderstandingService 解析小票。
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderUnderstandingService orderUnderstandingService;

    public OrderController(OrderUnderstandingService orderUnderstandingService) {
        this.orderUnderstandingService = orderUnderstandingService;
    }

    /**
     * 上传小票照片文件进行解析。
     */
    @PostMapping("/upload-photo")
    public ResponseEntity<OrderData> uploadPhoto(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String base64;
        try {
            base64 = Base64.getEncoder().encodeToString(file.getBytes());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }

        OrderData orderData = orderUnderstandingService.analyzeOrder(base64);
        return ResponseEntity.ok(orderData);
    }

    /**
     * 直接传入 base64 编码的小票照片（方便测试）。
     */
    @PostMapping("/upload-base64")
    public ResponseEntity<OrderData> uploadBase64(@RequestBody Base64Request request) {
        if (request.base64() == null || request.base64().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        OrderData orderData = orderUnderstandingService.analyzeOrder(request.base64());
        return ResponseEntity.ok(orderData);
    }

    public record Base64Request(String base64) {}
}
