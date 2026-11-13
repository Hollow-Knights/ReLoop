package br.com.fiap.reloop.security;

import java.time.Duration;
import java.time.Instant;

import br.com.fiap.reloop.auth.dto.LoginResponse;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

public class JwtService {

    private static final Duration TOKEN_LIFETIME = Duration.ofHours(1);

    private final JwtEncoder encoder;
    private final String issuer;

    public JwtService(JwtEncoder encoder, String issuer) {
        this.encoder = encoder;
        this.issuer = issuer;
    }

    public LoginResponse issue(Long userId) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(TOKEN_LIFETIME))
                .build();
        var headers = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = encoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", TOKEN_LIFETIME.toSeconds());
    }
}
