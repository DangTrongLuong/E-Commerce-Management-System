package com.example.ecommerce.common.security;

import com.example.ecommerce.user.entity.AppUser;
import com.example.ecommerce.user.enums.UserStatus;
import com.example.ecommerce.user.repository.AppUserRepository;
import com.example.ecommerce.common.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AppUserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            if (jwtUtil.validateToken(jwt)) {
                String email = jwtUtil.extractEmail(jwt);
                String type = jwtUtil.extractType(jwt);

                if ("access".equals(type) && email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    AppUser user = userRepository.findByEmail(email).orElse(null);

                    if (user != null && user.getStatus() == UserStatus.ACTIVE) {
                        Integer tokenVersion = jwtUtil.extractTokenVersion(jwt);
                        if (tokenVersion != null && tokenVersion.equals(user.getTokenVersion())) {
                            String roleName = user.getRole().name().startsWith("ROLE_")
                                    ? user.getRole().name()
                                    : "ROLE_" + user.getRole().name();

                            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                    user.getEmail(),
                                    null,
                                    List.of(new SimpleGrantedAuthority(roleName))
                            );

                            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                        } else {
                            log.warn("JWT token version mismatch or missing for user {}", email);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("JWT authentication processing error: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
