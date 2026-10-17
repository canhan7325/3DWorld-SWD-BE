package com.project.w_3d_world.common.config;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.features.auth.service.AuthSessionService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final AuthSessionService sessionService;

    public JwtAuthenticationFilter(
            JwtService jwtService, ObjectMapper objectMapper, AuthSessionService sessionService) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            try {
                if (!header.startsWith("Bearer ")) {
                    throw ApiException.invalidCredentials();
                }
                var jwt = jwtService.validateToken(header.substring(7));
                int userId = Integer.parseInt(jwt.getSubject());
                if (jwt.getId() == null) {
                    throw ApiException.invalidSession();
                }
                UUID sessionId = UUID.fromString(jwt.getId());
                sessionService.validateSession(userId, sessionId, jwt.getIssuedAt());
                var roleClaims = jwt.getClaimAsStringList("roles");
                Set<String> roles = roleClaims == null ? Set.of() : Set.copyOf(roleClaims);
                var principal = new AuthenticatedUser(userId, roles, sessionId);
                var authorities =
                        roles.stream()
                                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                                .toList();
                var authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | ApiException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(
                        response.getOutputStream(),
                        ApiResponse.error(
                                HttpStatus.UNAUTHORIZED.value(),
                                "Access token or session is invalid, expired or revoked."));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
