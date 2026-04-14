package tech.lemnova.continuum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tech.lemnova.continuum.application.exception.BadRequestException;
import tech.lemnova.continuum.application.service.AuthService;
import tech.lemnova.continuum.controller.dto.auth.*;
import tech.lemnova.continuum.infra.google.GoogleOAuthService;
import tech.lemnova.continuum.infra.security.CustomUserDetails;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Endpoints for user authentication and authorization")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final GoogleOAuthService googleOAuthService;

    public AuthController(AuthService authService, GoogleOAuthService googleOAuthService) {
        this.authService        = authService;
        this.googleOAuthService = googleOAuthService;
    }

    /**
     * DEPRECATED: Password-based registration is disabled.
     * Clients must use Google OAuth2 instead.
     * @see <a href="/oauth2/authorization/google">Google OAuth2</a>
     */
    @PostMapping("/register")
    @Deprecated(forRemoval = true)
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest req) {
        throw new BadRequestException(
            "Password-based registration is disabled. Please use Google OAuth2 login: POST /oauth2/authorization/google"
        );
    }

    /**
     * DEPRECATED: Password-based login is disabled.
     * Clients must use Google OAuth2 instead.
     * @see <a href="/oauth2/authorization/google">Google OAuth2</a>
     */
    @PostMapping("/login")
    @Deprecated(forRemoval = true)
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        throw new BadRequestException(
            "Password-based login is disabled. Please use Google OAuth2 login: POST /oauth2/authorization/google"
        );
    }

    @PostMapping("/google/callback")
    @Operation(summary = "Google OAuth callback", description = "Processes Google OAuth authentication and returns tokens")
    public ResponseEntity<AuthResponse> googleCallback(@RequestBody Map<String, String> body) {
        String idToken = body.get("idToken");
        if (idToken == null || idToken.isBlank()) throw new BadRequestException("idToken is required");
        return ResponseEntity.ok(authService.googleAuth(googleOAuthService.verify(idToken)));
    }

    @GetMapping("/verify-email")
    @Operation(summary = "Verify email address", description = "Verifies user email using the verification token sent to their inbox")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(Map.of("message", "Email verified successfully"));
    }

    /**
     * DEPRECATED: Email verification is automatic with Google OAuth2.
     * @deprecated No longer needed - Google OAuth2 provides email verification
     */
    @PostMapping("/resend-verification")
    @Deprecated(forRemoval = true)
    public ResponseEntity<Map<String, String>> resendVerification(@RequestBody Map<String, String> body) {
        throw new BadRequestException(
            "Email verification is automatic with Google OAuth2. No manual verification needed."
        );
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Uses refresh token to obtain a new access token (implements token rotation)")
    public ResponseEntity<AuthResponse> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) throw new BadRequestException("refreshToken is required");
        return ResponseEntity.ok(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Revokes the user's access and refresh tokens, invalidating all sessions")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal CustomUserDetails user,
                                      @RequestBody(required = false) Map<String, String> body) {
        if (user != null) {
            String accessToken = null;
            String refreshToken = null;
            
            if (body != null) {
                accessToken = body.get("accessToken");
                refreshToken = body.get("refreshToken");
            }
            
            // Se tokens forem fornecidos, revogar especificamente
            if (accessToken != null || refreshToken != null) {
                authService.logout(user.getUserId(), accessToken, refreshToken);
            } else {
                // Caso contrário, revogar todos os tokens do usuário
                authService.logout(user.getUserId());
            }
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/verify")
    @Operation(summary = "Verify email (alias)", description = "Alternative endpoint for email verification")
    public ResponseEntity<Map<String, String>> verify(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(Map.of("message", "Email verified successfully"));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user context", description = "Returns authenticated user's profile and authorization details")
    public ResponseEntity<UserContextResponse> me(@AuthenticationPrincipal CustomUserDetails user) {
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try { return ResponseEntity.ok(authService.getContext(user.getUserId())); }
        catch (Exception e) {
            log.error("Failed to build user context for {}: {}", user.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
