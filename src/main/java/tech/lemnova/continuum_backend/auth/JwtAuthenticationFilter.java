package tech.lemnova.continuum_backend.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(
        JwtAuthenticationFilter.class
    );

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(
        JwtService jwtService,
        UserDetailsService userDetailsService
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");

        // ✅ Verificar se header existe e é Bearer
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // ✅ Extrair token
            final String jwt = authHeader.substring(7);
            final String username = jwtService.extractUsername(jwt);

            // ✅ Verificar se username existe e não há autenticação prévia
            if (
                username != null &&
                SecurityContextHolder.getContext().getAuthentication() == null
            ) {
                // ✅ Carregar UserDetails (que contém User.active)
                UserDetails userDetails =
                    this.userDetailsService.loadUserByUsername(username);

                // ✅ Validar token
                if (jwtService.isTokenValid(jwt)) {
                    // ✅ IMPORTANTE: Spring Security automaticamente rejeita se userDetails.isEnabled() == false
                    // CustomUserDetails.isEnabled() retorna User.active
                    // Portanto, usuários inativos NÃO conseguirão autenticar mesmo com token válido

                    UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities() // ✅ Authorities baseadas em User.role
                        );

                    authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(
                            request
                        )
                    );

                    SecurityContextHolder.getContext().setAuthentication(
                        authToken
                    );

                    logger.debug(
                        "User {} authenticated successfully",
                        username
                    );
                }
            }
        } catch (Exception e) {
            // ✅ Token inválido, expirado ou usuário não encontrado
            logger.error("JWT validation error: {}", e.getMessage());
            // Não seta autenticação - request continua não autenticado
        }

        filterChain.doFilter(request, response);
    }
}
