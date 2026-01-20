package tech.lemnova.continuum_backend.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import tech.lemnova.continuum_backend.exception.UnauthorizedException;

public class SecurityUtils {

    private SecurityUtils() {
        // Utility class
    }

    /**
     * Extrai o username do usuário autenticado do contexto de segurança
     */
    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }

        if (principal instanceof String) {
            return (String) principal;
        }

        throw new UnauthorizedException("Invalid authentication principal");
    }

    /**
     * Verifica se há um usuário autenticado
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
            && !(authentication.getPrincipal() instanceof String &&
                 authentication.getPrincipal().equals("anonymousUser"));
    }
}
