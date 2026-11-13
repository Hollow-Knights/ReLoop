package br.com.fiap.reloop.security;

import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@Profile("bootstrap")
public class JwtConfiguration {

    private final String issuer = "urn:reloop:bootstrap:" + UUID.randomUUID();
    private final SecretKey signingKey;

    public JwtConfiguration(@Value("${reloop.jwt.secret}") String secret) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException invalidBase64) {
            throw new IllegalArgumentException("JWT_SECRET deve estar em Base64.");
        }
        if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET deve conter pelo menos 32 bytes aleatórios codificados em Base64.");
        }
        signingKey = new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtService jwtService() {
        return new JwtService(new NimbusJwtEncoder(new ImmutableSecret<>(signingKey)), issuer);
    }

    @Bean
    JwtDecoder jwtDecoder() {
        var decoder = NimbusJwtDecoder.withSecretKey(signingKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtIssuerValidator(issuer),
                new JwtTimestampValidator(Duration.ZERO),
                jwt -> {
                    if (jwt.getExpiresAt() != null && jwt.getIssuedAt() != null && isUserId(jwt.getSubject())) {
                        return OAuth2TokenValidatorResult.success();
                    }
                    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
                }));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    private static boolean isUserId(String subject) {
        if (subject == null) {
            return false;
        }
        try {
            long id = Long.parseLong(subject);
            return id > 0 && Long.toString(id).equals(subject);
        } catch (NumberFormatException invalidSubject) {
            return false;
        }
    }
}
