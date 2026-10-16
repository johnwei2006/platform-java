package com.iot.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 统一鉴权过滤器：校验 Bearer 令牌，白名单路径跳过。
 * 白名单：登录、健康检查、actuator、静态资源。
 */
@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AntPathMatcher matcher = new AntPathMatcher();
    private final List<String> whiteList = Arrays.asList(
            "/api/auth/**", "/health", "/actuator/**", "/error", "/favicon.ico");

    public JwtFilter(@Value("${app.jwt.secret}") String secret) {
        this.jwtUtil = new JwtUtil(secret);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        for (String p : whiteList) {
            if (matcher.match(p, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        try {
            if (header == null || !header.startsWith("Bearer ")) {
                throw new RuntimeException("missing Authorization header");
            }
            String subject = jwtUtil.verify(header.substring(7));
            request.setAttribute("subject", subject);
            chain.doFilter(request, response);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            ApiResponse<Void> r = ApiResponse.fail(401, "unauthorized: " + e.getMessage());
            response.getWriter().write(new ObjectMapper().writeValueAsString(r));
        }
    }
}
