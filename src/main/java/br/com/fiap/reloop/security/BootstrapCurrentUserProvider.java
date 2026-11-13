package br.com.fiap.reloop.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("bootstrap")
public class BootstrapCurrentUserProvider implements CurrentUserProvider {

    private static final long DEMO_USER_ID = 1L;

    @Override
    public Long userId() {
        return DEMO_USER_ID;
    }
}
