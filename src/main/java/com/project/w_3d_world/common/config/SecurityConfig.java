package com.project.w_3d_world.common.config;

import com.project.w_3d_world.common.response.ApiResponse;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.features.auth.service.AuthSessionService;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public CorsConfigurationSource corsConfigurationSource(Environment environment) {
        boolean development = environment.acceptsProfiles(Profiles.of("dev"));
        List<String> origins = List.of();
        if (!development) {
            String configured = environment.getProperty("APP_CORS_ALLOWED_ORIGINS", "");
            origins = Arrays.stream(configured.split(","))
                    .map(String::trim).filter(value -> !value.isEmpty()).toList();
            if (origins.stream().anyMatch(origin -> origin.contains("*"))) {
                throw new IllegalStateException("Production CORS origins must be explicit.");
            }
        }
        return corsConfigurationSource(origins, development);
    }

    private static CorsConfigurationSource corsConfigurationSource(
            List<String> origins, boolean development) {
        CorsConfiguration configuration = new CorsConfiguration();
        if (development) {
            configuration.setAllowedOriginPatterns(List.of("*"));
        }
        else configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtService jwtService, ObjectMapper objectMapper,
            AuthSessionService sessionService,
            @Qualifier("corsConfigurationSource") CorsConfigurationSource corsSource) throws Exception {
        var jwtFilter = new JwtAuthenticationFilter(jwtService, objectMapper, sessionService);
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsSource))
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
                        requests -> requests
                                .requestMatchers(HttpMethod.GET,
                                        "/api/storefronts/{storefrontId:[0-9]+}",
                                        "/api/storefronts/{storefrontId:[0-9]+}/products").permitAll()
                                .requestMatchers(
                                                "/api/catalog", "/api/catalog/**",
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
                                                "/api/payments/zalopay/callback",
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
