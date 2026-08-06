package com.moka.biz.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 场景接口的 Access Token 鉴权服务。
 * <p>
 * 业务系统以 appKey + appSecret 换取 token（默认 30 分钟有效），
 * 调用业务接口时在请求头携带 {@code AccessToken: <token>}。
 * v1 使用内存存储，生产可按需替换为 Redis/DB。
 */
@Component
public class ApiTokenService {

    /** Token 有效期：30 分钟 */
    private static final long TTL_MS = 30 * 60 * 1000L;

    private final String appKey;
    private final String appSecret;

    /** token -> 过期时间戳（毫秒） */
    private final Map<String, Long> tokens = new ConcurrentHashMap<>();

    public ApiTokenService(@Value("${moka.api.app-key:mantan-app}") String appKey,
                           @Value("${moka.api.app-secret:mantan-secret}") String appSecret) {
        this.appKey = appKey;
        this.appSecret = appSecret;
    }

    /**
     * 校验 appKey/appSecret，通过则签发 token，失败返回 null。
     */
    public String issue(String key, String secret) {
        if (key == null || secret == null || !key.equals(appKey) || !secret.equals(appSecret)) {
            return null;
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, System.currentTimeMillis() + TTL_MS);
        return token;
    }

    /**
     * 校验 token 是否有效且未过期。
     */
    public boolean isValid(String token) {
        if (token == null) {
            return false;
        }
        Long expiry = tokens.get(token);
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() > expiry) {
            tokens.remove(token);
            return false;
        }
        return true;
    }
}
