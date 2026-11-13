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
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
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
        var implementedRoutes = new OrRequestMatcher(catalogReads, photoRequests, disposalRequests);
        var iotEvents = paths.matcher(HttpMethod.POST, "/api/v1/iot/events");
        if (bootstrap) {
            http.addFilterBefore(new DeviceKeyFilter(iotEvents, objectMapper,
                    environment.getRequiredProperty("reloop.iot.device-id"),
                    environment.getRequiredProperty("reloop.iot.device-key")), AuthorizationFilter.class);
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
                        authorize.requestMatchers(implementedRoutes, iotEvents).permitAll();
                    } else {
                        authorize.requestMatchers(implementedRoutes).authenticated();
                    }
                    authorize.anyRequest().denyAll();
                })
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            if (implementedRoutes.matches(request)) {
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
