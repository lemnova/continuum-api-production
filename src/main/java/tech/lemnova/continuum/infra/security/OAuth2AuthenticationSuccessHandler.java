package tech.lemnova.continuum.infra.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy; // Importação essencial
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import tech.lemnova.continuum.application.service.AuthService;

import java.io.IOException;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthService authService;

    // Adicionamos o @Lazy aqui também porque esta classe faz parte do nó
    // de dependência que o Spring Config tenta montar no início.
    public OAuth2AuthenticationSuccessHandler(@Lazy AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        // Seu código de redirecionamento ou geração de token após o sucesso do login
        // Exemplo:
        // String targetUrl = determineTargetUrl(request, response, authentication);
        // getRedirectStrategy().sendRedirect(request, response, targetUrl);
        
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
