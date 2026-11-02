package com.insurance.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            if (jwtUtil.validateToken(token)) {
                String username = jwtUtil.extractUsername(token);
                String role = jwtUtil.extractRole(token);
                Integer userId = jwtUtil.extractUserId(token);

                var auth = new UsernamePasswordAuthenticationToken(
                        username,
                        userId,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                );

                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("Authenticated request: username={}, role={}, path={}", username, role, request.getRequestURI());
            } else {
                // Never log the token itself; just note that one was presented and rejected.
                log.debug("Rejected invalid/expired JWT on {} {}", request.getMethod(), request.getRequestURI());
            }
        }

        chain.doFilter(request, response);
    }
}
