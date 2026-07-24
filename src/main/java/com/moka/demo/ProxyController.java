package com.moka.demo;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 * 代理端点，用于转发通话创建请求到技术部 API。
 * <p>
 * 解决前端直接调用时的跨域（CORS）问题。
 * 前端将完整请求（含 Authorization 和 X-API-Key 头）发到本代理，本代理原样转发给技术部。
 */
@RestController
@RequestMapping("/api/v1/proxy")
@Profile("demo")
public class ProxyController {

    private static final String TARGET_URL = "https://voice-dialog.bangbangyouxin.cn/api/v1/sessions";

    private final RestTemplate restTemplate;

    public ProxyController() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * 转发创建通话请求到技术部 API。
     *
     * @param body          请求体（JSON 字符串）
     * @param authHeader    客户端传入的 Authorization 头
     * @param apiKeyHeader  客户端传入的 X-API-Key 头
     * @return 技术部 API 的原始响应
     */
    @PostMapping("/sessions")
    public ResponseEntity<String> proxySession(
            @RequestBody String body,
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader("X-API-Key") String apiKeyHeader) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", authHeader);
        headers.set("X-API-Key", apiKeyHeader);

        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(TARGET_URL, entity, String.class);

        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(response.getBody());
    }
}
