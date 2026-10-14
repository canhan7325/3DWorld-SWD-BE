package com.project.w_3d_world.common.config;

import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.common.service.JwtService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtService jwtService, ObjectMapper objectMapper) throws Exception {
        var jwtFilter = new JwtAuthenticationFilter(jwtService, objectMapper);
        return http.csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        errors ->
                                errors.authenticationEntryPoint(
                                                (request, response, exception) -> {
                                                    response.setStatus(
                                                            HttpStatus.UNAUTHORIZED.value());
                                                    response.setContentType(
                                                            MediaType.APPLICATION_JSON_VALUE);
                                                    objectMapper.writeValue(
                                                            response.getOutputStream(),
                                                            ApiResponse.error(
                                                                    HttpStatus.UNAUTHORIZED.value(),
                                                                    "A valid Bearer token is"
                                                                            + " required."));
                                                })
                                        .accessDeniedHandler(
                                                (request, response, exception) -> {
                                                    response.setStatus(
                                                            HttpStatus.FORBIDDEN.value());
                                                    response.setContentType(
                                                            MediaType.APPLICATION_JSON_VALUE);
                                                    objectMapper.writeValue(
                                                            response.getOutputStream(),
                                                            ApiResponse.error(
                                                                    HttpStatus.FORBIDDEN.value(),
                                                                    "Permission denied."));
                                                }))
                .authorizeHttpRequests(
                        requests ->
                                requests.requestMatchers(
                                                "/api/auth/register",
                                                "/api/auth/login",
                                                "/api/auth/verify-email",
                                                "/api/auth/resend-verification",
                                                "/api/auth/forgot-password",
                                                "/api/auth/reset-password",
                                                "/verify-email",
                                                "/reset-password",
                                                "/verify-email.html",
                                                "/reset-password.html",
                                                "/swagger-ui.html",
                                                "/swagger-ui/**",
                                                "/v3/api-docs/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
