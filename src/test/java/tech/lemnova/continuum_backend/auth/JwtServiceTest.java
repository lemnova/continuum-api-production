package tech.lemnova.continuum_backend.auth;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtService Tests")
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", 
            "mySecretKeyForTestingPurposesOnlyAndShouldBeLongEnoughForHS256");
        ReflectionTestUtils.setField(jwtService, "expiration", 3600000L);
    }

    @Test
    @DisplayName("Should generate valid JWT token")
    void shouldGenerateValidToken() {
        String userId = "user123";
        String username = "testuser";

        String token = jwtService.generateToken(userId, username);

        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3);
    }

    @Test
    @DisplayName("Should extract username from token")
    void shouldExtractUsernameFromToken() {
        String userId = "user123";
        String username = "testuser";
        String token = jwtService.generateToken(userId, username);

        String extractedUsername = jwtService.extractUsername(token);

        assertEquals(username, extractedUsername);
    }

    @Test
    @DisplayName("Should extract userId from token")
    void shouldExtractUserIdFromToken() {
        String userId = "user123";
        String username = "testuser";
        String token = jwtService.generateToken(userId, username);

        String extractedUserId = jwtService.extractUserId(token);

        assertEquals(userId, extractedUserId);
    }

    @Test
    @DisplayName("Should validate token successfully")
    void shouldValidateTokenSuccessfully() {
        String token = jwtService.generateToken("user123", "testuser");

        boolean isValid = jwtService.isTokenValid(token);

        assertTrue(isValid);
    }

    @Test
    @DisplayName("Should invalidate malformed token")
    void shouldInvalidateMalformedToken() {
        String malformedToken = "invalid.token.here";

        boolean isValid = jwtService.isTokenValid(malformedToken);

        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should extract all claims from token")
    void shouldExtractAllClaimsFromToken() {
        String userId = "user123";
        String username = "testuser";
        String token = jwtService.generateToken(userId, username);

        Claims claims = jwtService.extractAllClaims(token);

        assertNotNull(claims);
        assertEquals(username, claims.getSubject());
        assertEquals(userId, claims.get("userId", String.class));
    }
}
