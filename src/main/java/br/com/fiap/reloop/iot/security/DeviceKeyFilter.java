package br.com.fiap.reloop.iot.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import br.com.fiap.reloop.shared.web.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

public class DeviceKeyFilter extends OncePerRequestFilter {

    public static final String DEVICE_ID_ATTRIBUTE = "authenticatedIotDeviceId";

    private final RequestMatcher route;
    private final ObjectMapper mapper;
    private final String deviceId;
    private final byte[] expectedKey;

    public DeviceKeyFilter(RequestMatcher route, ObjectMapper mapper, String deviceId, String key) {
        this.route = route;
        this.mapper = mapper;
        this.deviceId = deviceId;
        this.expectedKey = key.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !route.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var keys = request.getHeaders("X-Device-Key");
        String key = keys.hasMoreElements() ? keys.nextElement() : "";
        if (expectedKey.length == 0 || key.isBlank() || keys.hasMoreElements()
                || !MessageDigest.isEqual(expectedKey, key.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(), ApiError.of(
                    HttpStatus.UNAUTHORIZED, "INVALID_DEVICE_KEY", "Credencial do coletor ausente ou inválida."));
            return;
        }
        request.setAttribute(DEVICE_ID_ATTRIBUTE, deviceId);
        chain.doFilter(request, response);
    }
}
