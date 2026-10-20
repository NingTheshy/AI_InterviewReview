package com.interview.auth.config;

import com.interview.auth.entity.User;
import com.interview.auth.mapper.UserMapper;
import com.interview.auth.service.RbacService;
import com.interview.common.constant.UserStatus;
import com.interview.common.utils.JwtUtil;
import com.interview.common.utils.RedisUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器
 * <p>
 * 拦截请求，验证 JWT Token 的有效性（签名、黑名单），
 * 并将用户信息设置到 Spring Security 上下文中。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final UserMapper userMapper;
    private final RbacService rbacService;

    /**
     * 将有效 Bearer Token 转换为当前请求的认证上下文。
     *
     * <p>认证顺序固定为：跳过 CORS 预检、校验 JWT 签名与过期时间、校验 Redis 注销黑名单、
     * 从数据库读取当前账户状态和角色，最后写入 {@link SecurityContextHolder}。角色和禁用状态
     * 不从 Token 读取，确保管理员调整权限或禁用用户后可在下一次请求立即生效。</p>
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应，用于直接返回认证失败信息
     * @param filterChain 后续过滤器链
     * @throws ServletException 过滤器链执行失败时抛出
     * @throws IOException 写入响应或继续过滤器链失败时抛出
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 放行 OPTIONS 预检请求，由 CorsFilter 处理
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            if (!jwtUtil.validateToken(token)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"Token 无效或已过期\"}");
                return;
            }

            // 检查 Token 是否在 Redis 黑名单中
            Claims claims = jwtUtil.parseToken(token);
            String jti = claims.getId();
            if (jti != null && Boolean.TRUE.equals(redisUtil.hasKey("token:blacklist:" + jti))) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"Token 已失效\"}");
                return;
            }

            Long userId = jwtUtil.getUserId(token);
            String username = jwtUtil.getUsername(token);

            User user = userMapper.selectById(userId);
            if (user == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"用户不存在\"}");
                return;
            }
            if (user.getStatus() != null && user.getStatus() == UserStatus.DISABLED.getCode()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":403,\"message\":\"账户已被禁用\"}");
                return;
            }
            // JWT 只证明身份，功能权限始终以数据库中的 RBAC 关系为准。
            List<SimpleGrantedAuthority> authorities = rbacService.getPermissionCodes(userId).stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, username, authorities);
            authentication.setDetails(username);

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
