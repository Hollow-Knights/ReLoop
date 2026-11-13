package br.com.fiap.reloop.security;

import br.com.fiap.reloop.iot.security.DeviceKeyFilter;
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
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
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
        var photoRequests = new OrRequestMatcher(
                paths.matcher(HttpMethod.POST, "/api/v1/uploads"),
                paths.matcher(HttpMethod.GET, "/api/v1/uploads/{photoId}"));
        var disposalRequests = new OrRequestMatcher(
                paths.matcher(HttpMethod.POST, "/api/v1/disposals"),
                paths.matcher(HttpMethod.GET, "/api/v1/disposals/{id}"),
                paths.matcher(HttpMethod.DELETE, "/api/v1/disposals/{id}"),
                paths.matcher(HttpMethod.GET, "/api/v1/users/me/disposals"));
        var profileRequests = new OrRequestMatcher(
                paths.matcher(HttpMethod.GET, "/api/v1/users/me"),
                paths.matcher(HttpMethod.PUT, "/api/v1/users/me"));
        var privateRequests = new OrRequestMatcher(photoRequests, disposalRequests, profileRequests);
        var publicAuth = new OrRequestMatcher(
                paths.matcher(HttpMethod.POST, "/api/v1/auth/register"),
                paths.matcher(HttpMethod.POST, "/api/v1/auth/login"));
        var iotEvents = paths.matcher(HttpMethod.POST, "/api/v1/iot/events");
        AuthenticationEntryPoint unauthorized = (request, response, failure) -> {
            boolean invalidToken = failure instanceof OAuth2AuthenticationException;
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            if (privateRequests.matches(request) || catalogReads.matches(request)) {
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                        invalidToken ? "Bearer error=\"invalid_token\"" : "Bearer");
            }
            objectMapper.writeValue(response.getOutputStream(), ApiError.of(HttpStatus.UNAUTHORIZED,
                    invalidToken ? "INVALID_TOKEN" : "AUTHENTICATION_REQUIRED",
                    invalidToken ? "Token de acesso inválido ou expirado." : "Autenticação necessária."));
        };
        if (bootstrap) {
            http.addFilterBefore(new DeviceKeyFilter(iotEvents, objectMapper,
                    environment.getRequiredProperty("reloop.iot.device-id"),
                    environment.getRequiredProperty("reloop.iot.device-key")), AuthorizationFilter.class);
            var bearerTokens = new DefaultBearerTokenResolver();
            http.oauth2ResourceServer(resourceServer -> resourceServer
                    .bearerTokenResolver(request -> privateRequests.matches(request) ? bearerTokens.resolve(request) : null)
                    .authenticationEntryPoint(unauthorized)
                    .jwt(Customizer.withDefaults()));
        }

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
                        // O filtro IoT exige X-Device-Key antes que a requisição alcance o controller.
                        authorize.requestMatchers(catalogReads, publicAuth, iotEvents).permitAll();
                    } else {
                        authorize.requestMatchers(catalogReads).authenticated();
                    }
                    authorize.requestMatchers(privateRequests).authenticated();
                    authorize.anyRequest().denyAll();
                })
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(), ApiError.of(
                                    HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Acesso negado."));
                        }))
                .build();
    }
}
