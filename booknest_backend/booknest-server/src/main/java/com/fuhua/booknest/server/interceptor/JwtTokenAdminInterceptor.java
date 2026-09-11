package com.fuhua.booknest.server.interceptor;

import com.fuhua.booknest.common.constant.JwtClaimsConstant;
import com.fuhua.booknest.common.constant.MessageConstant;
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

/**
 * JWT令牌校验拦截器 - 管理后台端
 * 从管理端专属请求头（adminTokenName）取令牌，使用管理端专属密钥（adminSecretKey）验签，
 * 再校验令牌中的角色是否为 ADMIN，非管理员拒绝访问。
 * 与用户端拦截器完全隔离：用户端令牌无法通过此处校验。
 */
@Component
@Slf4j
public class JwtTokenAdminInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 校验JWT令牌并校验管理员角色
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 判断当前拦截到的是Controller的方法还是其他资源
        if (!(handler instanceof HandlerMethod)) {
            // 当前拦截到的不是动态方法，直接放行
            return true;
        }

        // 1、从管理端专属请求头中获取令牌（不打印 token 明文）
        String token = request.getHeader(jwtProperties.getAdminTokenName());

        try {
            // 2、使用管理端专属密钥解析令牌（与用户端密钥完全隔离）
            Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);

            // 3、校验角色必须为管理员
            Object role = claims.get(JwtClaimsConstant.ROLE);
            if (role == null || !MessageConstant.ROLE_ADMIN.equals(role.toString())) {
                log.warn("管理后台鉴权失败：当前角色非管理员");
                response.setStatus(403);
                return false;
            }

            // 4、将用户ID存入ThreadLocal
            BaseContext.setCurrentId(claims.get(JwtClaimsConstant.USER_ID).toString());

            // 5、放行
            return true;
        } catch (Exception ex) {
            // 6、令牌解析失败，响应401状态码
            log.error("管理后台JWT校验失败: {}", ex.getMessage());
            response.setStatus(401);
            return false;
        }
    }

    /**
     * 请求结束后清理 ThreadLocal，防止内存泄漏
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        BaseContext.removeCurrentId();
    }
}
