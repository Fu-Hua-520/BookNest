package com.fuhua.booknest.server.interceptor;

import com.fuhua.booknest.common.constant.JwtClaimsConstant;
import com.fuhua.booknest.common.context.BaseContext;
import com.fuhua.booknest.common.properties.JwtProperties;
import com.fuhua.booknest.common.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Set;

/**
 * JWT令牌校验拦截器 - 用户端
 */
@Component
@Slf4j
public class JwtTokenUserInterceptor implements HandlerInterceptor {

    /**
     * 允许游客只读访问的路径前缀：对应论坛的「浏览」行为——看帖子、看书单、看分类与标签。
     * 只对这些前缀下的安全方法（GET/HEAD/OPTIONS）放行，POST/PUT/DELETE 等写操作仍要求登录。
     * 注：/book/** 本就不在拦截器覆盖范围内、天然公开，故无需列入。
     */
    private static final List<String> PUBLIC_READ_PREFIXES = List.of(
            "/post", "/booklist", "/category", "/tag");

    /**
     * 即使是 GET 也必须登录的路径：语义上属于「我的」，游客访问只会拿到空结果
     */
    private static final Set<String> LOGIN_REQUIRED_READ_PATHS = Set.of("/booklist/my", "/post/collect/list");

    /** 安全方法：不改动服务端状态 */
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 校验JWT令牌
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.info("JWT拦截器执行，URI: {}", request.getRequestURI());

        // 判断当前拦截到的是Controller的方法还是其他资源
        if (!(handler instanceof HandlerMethod)) {
            // 当前拦截到的不是动态方法，直接放行
            return true;
        }

        String uri = request.getRequestURI();
        // 公开只读 = 安全方法 + 命中公开前缀 + 不属于「我的」类接口
        boolean publicRead = SAFE_METHODS.contains(request.getMethod())
                && matchPrefix(uri, PUBLIC_READ_PREFIXES)
                && !LOGIN_REQUIRED_READ_PATHS.contains(uri);

        // 1、从请求头中获取令牌
        String token = request.getHeader(jwtProperties.getUserTokenName());

        // 游客浏览：公开只读接口不带令牌时直接放行，BaseContext 保持为空，下游按匿名处理
        if (publicRead && (token == null || token.isEmpty())) {
            return true;
        }

        // 2、校验令牌
        try {
            log.info("JWT校验开始，token是否存在: {}", token != null);
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
            String userId = claims.get(JwtClaimsConstant.USER_ID).toString();
            log.info("当前用户ID: {}", userId);

            // 3、将用户ID存入ThreadLocal
            BaseContext.setCurrentId(userId);

            // 4、放行
            return true;
        } catch (Exception ex) {
            // 公开只读接口在令牌过期/无效时降级为游客放行，
            // 否则用户令牌一过期连首页都打不开（白屏），体验上不可接受
            if (publicRead) {
                log.warn("公开只读接口携带无效令牌，按游客放行，URI: {}", uri);
                BaseContext.removeCurrentId();
                return true;
            }
            // 5、验证失败，响应401状态码
            log.error("JWT校验失败: {}", ex.getMessage());
            response.setStatus(401);
            return false;
        }
    }

    /**
     * 判断 URI 是否命中任一前缀：/post 匹配 /post 与 /post/list，但不匹配 /postman
     */
    private boolean matchPrefix(String uri, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (uri.equals(prefix) || uri.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 请求结束后清理 ThreadLocal，防止内存泄漏
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        BaseContext.removeCurrentId();
    }
}
