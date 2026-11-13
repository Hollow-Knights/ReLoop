package br.com.fiap.reloop.security;

import br.com.fiap.reloop.shared.web.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@Profile("bootstrap")
public class JwtCurrentUserProvider implements CurrentUserProvider {

    @Override
    public Long userId() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication) {
            return Long.valueOf(authentication.getToken().getSubject());
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Autenticação necessária.");
    }
}
