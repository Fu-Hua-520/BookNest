package com.fuhua.booknest.server.websocket;

import com.fuhua.booknest.common.constant.JwtClaimsConstant;
import com.fuhua.booknest.common.properties.JwtProperties;
import com.fuhua.booknest.common.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 私信 WebSocket 握手拦截器
 * 在握手阶段校验 JWT，并将 userId 注入会话属性
 */
@Component
@Slf4j
public class ChatWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 握手前校验 JWT 并注入 userId
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        // 从查询参数中解析 token
        Map<String, String> params = parseQuery(request.getURI().getQuery());
        String token = params.get("token");
        if (token == null || token.isEmpty()) {
            log.warn("WebSocket 握手失败：缺少 token");
            return false;
        }
        try {
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
            String userId = claims.get(JwtClaimsConstant.USER_ID).toString();
            attributes.put("userId", userId);
            return true;
        } catch (Exception ex) {
            log.warn("WebSocket 握手失败：JWT 校验异常 {}", ex.getMessage());
            return false;
        }
    }

    /**
     * 握手后处理（空实现）
     */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需额外处理
    }

    /**
     * 解析查询字符串为键值对
     * @param query 查询字符串
     * @return 参数键值对
     */
    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }
        for (String pair : query.split("&")) {
            int idx = pair.indexOf("=");
            if (idx <= 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        return params;
    }
}
