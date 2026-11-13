package br.com.fiap.reloop.security;

import br.com.fiap.reloop.shared.web.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            Environment environment,
            @Value("${springdoc.swagger-ui.enabled:false}") boolean documentationEnabled) throws Exception {
        boolean bootstrap = environment.acceptsProfiles(Profiles.of("bootstrap"));
        var paths = PathPatternRequestMatcher.withDefaults();
        var catalogReads = new OrRequestMatcher(
                paths.matcher(HttpMethod.GET, "/api/v1/collection-points"),
                paths.matcher(HttpMethod.GET, "/api/v1/collection-points/{id}"),
                paths.matcher(HttpMethod.GET, "/api/v1/electronic-categories"));

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers("/actuator/health").permitAll();
                    if (documentationEnabled) {
                        authorize.requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/swagger-config",
                                "/docs/openapi.yaml").permitAll();
                    }
                    if (bootstrap) {
                        authorize.requestMatchers(catalogReads).permitAll();
                    } else {
                        authorize.requestMatchers(catalogReads).authenticated();
                    }
                    authorize.anyRequest().denyAll();
                })
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            if (catalogReads.matches(request)) {
                                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                            }
                            objectMapper.writeValue(response.getOutputStream(), ApiError.of(
                                    HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Autenticação necessária."));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(), ApiError.of(
                                    HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Acesso negado."));
                        }))
                .build();
    }
}
