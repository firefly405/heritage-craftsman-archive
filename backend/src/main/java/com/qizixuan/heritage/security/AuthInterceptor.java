package com.qizixuan.heritage.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qizixuan.heritage.common.ApiResponse;
import com.qizixuan.heritage.common.UserSession;
import com.qizixuan.heritage.entity.SysUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final TokenService tokens;
    private final ObjectMapper objectMapper;
    public AuthInterceptor(TokenService tokens, ObjectMapper objectMapper) {
        this.tokens = tokens;
        this.objectMapper = objectMapper;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        SysUser user = tokens.find(token);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            response.setStatus(401);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail("登录状态已失效，请重新登录")));
            return false;
        }
        UserSession.set(user);
        return true;
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserSession.clear();
    }
}
