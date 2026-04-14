package tech.lemnova.continuum.infra.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import tech.lemnova.continuum.application.service.AuthService;
import tech.lemnova.continuum.controller.dto.auth.AuthResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);
    private final AuthService authService;
    private final String frontendUrl;

    public OAuth2AuthenticationSuccessHandler(
            AuthService authService,
            @Value("${frontend.url:http://localhost:5173}") String frontendUrl) {
        this.authService = authService;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        try {
            Object principal = authentication.getPrincipal();
            if (!(principal instanceof OidcUser oidcUser)) {
                logger.warn("Authentication principal is not OidcUser: {}", principal.getClass().getName());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed");
                return;
            }

            String googleId = oidcUser.getSubject();
            String email = oidcUser.getEmail();
            String name = oidcUser.getFullName();
            Boolean emailVerified = oidcUser.getEmailVerified();
            String picture = (String) oidcUser.getAttributes().get("picture");

            logger.debug("OAuth2 login successful for user: {} (email: {})", googleId, email);

            // Gerar JWT após autenticação bem-sucedida do Google
            AuthResponse authResponse = authService.googleAuth(
                    googleId,
                    email,
                    name,
                    emailVerified,
                    picture
            );

            // Construir URL de redirecionamento com token no query parameter
            String encodedToken = URLEncoder.encode(authResponse.accessToken(), StandardCharsets.UTF_8);
            String redirectUrl = String.format("%s/login-success?token=%s",
                    frontendUrl.replaceAll("/$", ""),  // Remove trailing slash se existir
                    encodedToken
            );

            logger.debug("Redirecting to: {}", redirectUrl.replaceAll("\\?token=.*", "?token=***"));
            response.sendRedirect(redirectUrl);
        } catch (Exception e) {
            logger.error("OAuth2 authentication success handler error", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Authentication processing failed");
        }
    }
}
