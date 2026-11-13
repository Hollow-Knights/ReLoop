package br.com.fiap.reloop.shared.web;

import java.time.Instant;

import org.springframework.http.HttpStatusCode;

public record ApiError(int status, String code, String message, Instant timestamp) {

    public static ApiError of(HttpStatusCode status, String code, String message) {
        return new ApiError(status.value(), code, message, Instant.now());
    }
}
