package com.iot.platform.security;

import com.iot.platform.config.IotProperties;
import com.iot.platform.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final IotProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && jwtTokenProvider.validate(token)) {
            try {
                Claims claims = jwtTokenProvider.parse(token);
                Long userId = claims.get("uid", Long.class);
                String username = claims.get("username", String.class);
                Long tenantId = claims.get("tenantId", Long.class);

                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) claims.get("roles");
                @SuppressWarnings("unchecked")
                List<String> perms = (List<String>) claims.get("perms");

                List<String> auths = (roles == null ? List.<String>of() : roles).stream()
                        .map(r -> "ROLE_" + r)
                        .collect(Collectors.toList());
                if (perms != null) auths.addAll(perms);

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(username, null,
                                auths.stream().map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                                        .collect(Collectors.toList()));
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);

                TenantContext.setTenantId(tenantId);
                TenantContext.setUserId(userId);
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(properties.getJwt().getHeader());
        String prefix = properties.getJwt().getPrefix();
        if (header != null && header.startsWith(prefix)) {
            return header.substring(prefix.length());
        }
        return null;
    }
}
