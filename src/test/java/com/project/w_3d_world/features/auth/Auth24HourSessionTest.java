package com.project.w_3d_world.features.auth;

import com.project.w_3d_world.common.config.AuthenticatedUser;
import com.project.w_3d_world.common.config.SecurityConfig;
import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.exception.GlobalExceptionHandler;
import com.project.w_3d_world.common.service.JwtService;
import com.project.w_3d_world.features.ai.controller.AiGenerationController;
import com.project.w_3d_world.features.ai.dto.response.AiConfigOptionsResponse;
import com.project.w_3d_world.features.ai.service.AiGenerationService;
import com.project.w_3d_world.features.auth.dto.request.LoginRequest;
import com.project.w_3d_world.features.auth.dto.response.LoginResponse;
import com.project.w_3d_world.features.auth.entity.Role;
import com.project.w_3d_world.features.auth.entity.UserRole;
import com.project.w_3d_world.features.auth.enums.Capability;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.auth.repository.RoleRepository;
import com.project.w_3d_world.features.auth.repository.UserRoleRepository;
import com.project.w_3d_world.features.auth.service.AuthSessionService;
import com.project.w_3d_world.features.auth.service.CapabilityService;
import com.project.w_3d_world.features.auth.service.EmailTokenService;
import com.project.w_3d_world.features.auth.service.impl.AuthServiceImpl;
import com.project.w_3d_world.features.auth.service.impl.AuthSessionServiceImpl;
import com.project.w_3d_world.features.studio.controller.StudioModelController;
import com.project.w_3d_world.features.studio.dto.response.StudioProjectSummaryResponse;
import com.project.w_3d_world.features.studio.service.StudioModelService;
import com.project.w_3d_world.features.user.entity.User;
import com.project.w_3d_world.features.user.enums.AccountStatus;
import com.project.w_3d_world.features.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Auth24HourSessionTest.Config.class)
@WebAppConfiguration
public class Auth24HourSessionTest {

    private static final long TWENTY_FOUR_HOURS_MS = 86_400_000L;
    private static final String SECRET_A = Base64.getEncoder().encodeToString("01234567890123456789012345678901234567890123456789".getBytes());
    private static final String SECRET_B = Base64.getEncoder().encodeToString("abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz".getBytes());

    @Autowired private WebApplicationContext context;
    @Autowired private AiGenerationService aiService;
    @Autowired private StudioModelService studioService;
    @Autowired private AuthSessionService authSessionService;

    private MockMvc mvc;
    private JwtService jwtService24h;

    @BeforeEach
    void setup() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        jwtService24h = new JwtService(SECRET_A, TWENTY_FOUR_HOURS_MS);
        reset(aiService, studioService, authSessionService);
    }

    private User createTestUser(int id, String email, String passwordHash, long authVersion, AccountStatus status) throws Exception {
        User user = new User(email, passwordHash, "Test User", "0901234567");
        user.changeStatus(status);

        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, id);

        Field authVersionField = User.class.getDeclaredField("authVersion");
        authVersionField.setAccessible(true);
        authVersionField.set(user, authVersion);

        return user;
    }

    // --- GATE 1: Login returns a valid JWT with 24-hour expiration ---
    @Test
    @DisplayName("Gate 1: Login returns a valid JWT with configured 24-hour expiration")
    void testGate1_LoginReturnsValidJwtWith24HourExpiration() throws Exception {
        UserRepository userRepo = mock(UserRepository.class);
        RoleRepository roleRepo = mock(RoleRepository.class);
        UserRoleRepository userRoleRepo = mock(UserRoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        EmailTokenService emailTokenService = mock(EmailTokenService.class);
        AuthSessionService sessionService = mock(AuthSessionService.class);
        CapabilityService capService = mock(CapabilityService.class);

        User user = createTestUser(101, "customer@3dworld.com", "$2a$10$hashedPassword", 0L, AccountStatus.ACTIVE);
        Role customerRole = mock(Role.class);
        when(customerRole.getCode()).thenReturn(RoleCode.CUSTOMER);
        UserRole userRole = new UserRole(user, customerRole);
        user.getUserRoles().add(userRole);

        when(userRepo.lockByNormalizedEmail("customer@3dworld.com")).thenReturn(Optional.of(user));
        when(encoder.matches("SecurePassword123!", "$2a$10$hashedPassword")).thenReturn(true);
        when(capService.getCapabilities(user)).thenReturn(Set.of(Capability.PROFILE_READ));

        AuthServiceImpl authService = new AuthServiceImpl(
                userRepo, roleRepo, userRoleRepo, encoder, jwtService24h,
                emailTokenService, sessionService, capService
        );

        Instant beforeLogin = Instant.now();
        LoginResponse response = authService.login(new LoginRequest("customer@3dworld.com", "SecurePassword123!"));
        Instant afterLogin = Instant.now();

        assertNotNull(response);
        assertNotNull(response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertNotNull(response.expiresAt());

        // Validate token lifetime is 24 hours (86,400,000 ms) within a 2-second tolerance
        long lifetimeMillis = Duration.between(beforeLogin, response.expiresAt()).toMillis();
        assertTrue(lifetimeMillis >= TWENTY_FOUR_HOURS_MS - 2000 && lifetimeMillis <= TWENTY_FOUR_HOURS_MS + 2000,
                "Expected expiration to be ~86,400,000ms from now, but got: " + lifetimeMillis);

        // Verify session creation called
        verify(sessionService).createSession(eq(user), any(Jwt.class));
    }

    // --- GATE 2: JWT expiration matches exactly 86,400,000 milliseconds ---
    @Test
    @DisplayName("Gate 2: JWT expiration matches configured 86,400,000 ms lifetime exactly")
    void testGate2_JwtExpirationMatchesConfiguredLifetime() {
        Jwt token = jwtService24h.generateToken(202, Set.of("CUSTOMER"), "0901234567", "user202@3dworld.com", 0L);

        assertNotNull(token);
        assertNotNull(token.getIssuedAt());
        assertNotNull(token.getExpiresAt());

        long diff = Duration.between(token.getIssuedAt(), token.getExpiresAt()).toMillis();
        assertEquals(TWENTY_FOUR_HOURS_MS, diff, "JWT duration must equal exactly 86,400,000 ms (24 hours)");
    }

    // --- GATE 3: Token remains valid before expiry ---
    @Test
    @DisplayName("Gate 3: Token remains valid before expiry and carries expected claims")
    void testGate3_TokenRemainsValidBeforeExpiry() {
        Jwt token = jwtService24h.generateToken(303, Set.of("CUSTOMER"), "0901234567", "user303@3dworld.com", 5L);

        Jwt validated = jwtService24h.validateToken(token.getTokenValue());
        assertNotNull(validated);
        assertEquals("303", validated.getSubject());
        assertEquals("ACCESS", validated.getClaimAsString("purpose"));
        assertEquals(5L, ((Number) validated.getClaim("authVersion")).longValue());
        assertEquals("user303@3dworld.com", validated.getClaimAsString("email"));
        assertTrue(validated.getExpiresAt().isAfter(Instant.now().plus(Duration.ofHours(23))));
    }

    // --- GATE 4: Token is rejected after expiry ---
    @Test
    @DisplayName("Gate 4: Token is strictly rejected after expiry")
    void testGate4_TokenRejectedAfterExpiry() throws InterruptedException {
        // Create a JwtService with 20ms TTL to test expiration deterministically without waiting 24 hours
        JwtService shortLivedJwtService = new JwtService(SECRET_A, 20L);
        Jwt token = shortLivedJwtService.generateToken(404, Set.of("CUSTOMER"), "0901234567", "user404@3dworld.com", 0L);

        Thread.sleep(60L); // wait for 20ms TTL to expire

        assertThrows(JwtException.class, () -> shortLivedJwtService.validateToken(token.getTokenValue()),
                "Expired token must be rejected with JwtException");
    }

    // --- GATE 5: Invalid signature is rejected ---
    @Test
    @DisplayName("Gate 5: Invalid signature is rejected")
    void testGate5_InvalidSignatureRejected() {
        Jwt tokenWithSecretA = jwtService24h.generateToken(505, Set.of("CUSTOMER"), "0901234567", "user505@3dworld.com", 0L);

        // Attempt validation with a JwtService using Secret B
        JwtService jwtServiceWithSecretB = new JwtService(SECRET_B, TWENTY_FOUR_HOURS_MS);

        assertThrows(JwtException.class, () -> jwtServiceWithSecretB.validateToken(tokenWithSecretA.getTokenValue()),
                "Token with invalid signature must be rejected");
    }

    // --- GATE 6: Logout / session revocation invalidates token via authVersion ---
    @Test
    @DisplayName("Gate 6: Logout revokes session by incrementing authVersion")
    void testGate6_LogoutRevokesSession() throws Exception {
        UserRepository userRepo = mock(UserRepository.class);
        AuthSessionServiceImpl sessionService = new AuthSessionServiceImpl(userRepo);

        User user = createTestUser(606, "user606@3dworld.com", "hash", 0L, AccountStatus.ACTIVE);
        when(userRepo.findById(606)).thenReturn(Optional.of(user));
        when(userRepo.lockById(606)).thenReturn(Optional.of(user));

        Jwt token = jwtService24h.generateToken(606, Set.of("CUSTOMER"), "0901234567", "user606@3dworld.com", user.getAuthVersion());
        UUID sessionId = UUID.fromString(token.getId());

        // Session is valid before logout
        assertDoesNotThrow(() -> sessionService.validateSession(606, sessionId, token.getIssuedAt()));

        // User logs out -> revokeSession increments authVersion
        sessionService.revokeSession(606, sessionId);
        assertEquals(1L, user.getAuthVersion(), "authVersion must increment after revokeSession");

        // Old token has sessionId with MSB == 0, but user now has authVersion == 1 -> REJECTED
        ApiException ex = assertThrows(ApiException.class, () -> sessionService.validateSession(606, sessionId, token.getIssuedAt()));
        assertEquals(401, ex.getStatus().value());
    }

    // --- GATE 7: Disabled accounts cannot retain access ---
    @Test
    @DisplayName("Gate 7: Suspended/inactive account cannot retain access even with unexpired token")
    void testGate7_DisabledAccountCannotRetainAccess() throws Exception {
        UserRepository userRepo = mock(UserRepository.class);
        AuthSessionServiceImpl sessionService = new AuthSessionServiceImpl(userRepo);

        User user = createTestUser(707, "user707@3dworld.com", "hash", 0L, AccountStatus.BANNED);
        when(userRepo.findById(707)).thenReturn(Optional.of(user));

        Jwt token = jwtService24h.generateToken(707, Set.of("CUSTOMER"), "0901234567", "user707@3dworld.com", user.getAuthVersion());
        UUID sessionId = UUID.fromString(token.getId());

        // Account is not active -> REJECTED with 401
        ApiException ex = assertThrows(ApiException.class, () -> sessionService.validateSession(707, sessionId, token.getIssuedAt()));
        assertEquals(401, ex.getStatus().value());
    }

    // --- GATE 8: Password reset invalidates active sessions ---
    @Test
    @DisplayName("Gate 8: Password reset revokes active sessions via authVersion increment")
    void testGate8_PasswordResetInvalidatesActiveSessions() throws Exception {
        UserRepository userRepo = mock(UserRepository.class);
        AuthSessionServiceImpl sessionService = new AuthSessionServiceImpl(userRepo);

        User user = createTestUser(808, "user808@3dworld.com", "oldHash", 0L, AccountStatus.ACTIVE);
        when(userRepo.findById(808)).thenReturn(Optional.of(user));

        Jwt token = jwtService24h.generateToken(808, Set.of("CUSTOMER"), "0901234567", "user808@3dworld.com", user.getAuthVersion());
        UUID sessionId = UUID.fromString(token.getId());

        // Valid before reset
        assertDoesNotThrow(() -> sessionService.validateSession(808, sessionId, token.getIssuedAt()));

        // Simulate BE1 Password Reset behavior: changePassword and revokeSessions
        user.changePassword("newHash");
        user.revokeSessions();
        assertEquals(1L, user.getAuthVersion());

        // Active token is now revoked
        ApiException ex = assertThrows(ApiException.class, () -> sessionService.validateSession(808, sessionId, token.getIssuedAt()));
        assertEquals(401, ex.getStatus().value());
    }

    // --- GATE 9: AI Studio accepts the 24-hour BE1 JWT ---
    @Test
    @DisplayName("Gate 9: AI Studio endpoints accept the 24-hour BE1 JWT")
    void testGate9_AiStudioAcceptsBe1Jwt() throws Exception {
        Jwt token = jwtService24h.generateToken(909, Set.of("CUSTOMER"), "0901234567", "aiuser@3dworld.com", 0L);

        when(aiService.configOptions()).thenReturn(new AiConfigOptionsResponse(
                "meshy", "v1.0", List.of(), Map.of(), Map.of()
        ));

        mvc.perform(get("/api/ai/config-options")
                        .header("Authorization", "Bearer " + token.getTokenValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.defaultProvider").value("meshy"));
    }

    // --- GATE 10: 3D Studio accepts the same 24-hour BE1 JWT ---
    @Test
    @DisplayName("Gate 10: 3D Studio endpoints accept the same 24-hour BE1 JWT")
    void testGate10_StudioAcceptsBe1Jwt() throws Exception {
        Jwt token = jwtService24h.generateToken(1010, Set.of("CUSTOMER"), "0901234567", "studiouser@3dworld.com", 0L);

        when(studioService.listProjects(1010)).thenReturn(List.of(
                new StudioProjectSummaryResponse(5001, "Castle", "WEB_EDITOR", "EDITOR", 1, Instant.now(), Instant.now())
        ));

        mvc.perform(get("/api/studio/projects")
                        .header("Authorization", "Bearer " + token.getTokenValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].modelId").value(5001));

        verify(studioService).listProjects(1010);
    }

    // --- GATE 11: Configured lifetime supports environment override ---
    @Test
    @DisplayName("Gate 11: Configured lifetime supports environment override without code changes")
    void testGate11_ConfiguredLifetimeSupportsOverride() {
        long customOverrideMs = 7_200_000L; // 2 hours
        JwtService customService = new JwtService(SECRET_A, customOverrideMs);
        Jwt token = customService.generateToken(1111, Set.of("CUSTOMER"), "0901234567", "custom@3dworld.com", 0L);

        long diff = Duration.between(token.getIssuedAt(), token.getExpiresAt()).toMillis();
        assertEquals(customOverrideMs, diff);
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, AiGenerationController.class, StudioModelController.class, GlobalExceptionHandler.class})
    static class Config implements WebMvcConfigurer {
        @Bean AuthSessionService authSessionService() {
            return mock(AuthSessionService.class);
        }
        @Bean AiGenerationService aiGenerationService() { return mock(AiGenerationService.class); }
        @Bean StudioModelService studioModelService() { return mock(StudioModelService.class); }
        @Bean JsonMapper mapper() {
            return JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        }
        @Bean JwtService jwt() {
            return new JwtService(SECRET_A, TWENTY_FOUR_HOURS_MS);
        }
        @Override public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
            builder.withJsonConverter(new JacksonJsonHttpMessageConverter(mapper()));
        }
    }
}
