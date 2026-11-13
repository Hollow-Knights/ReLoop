package br.com.fiap.reloop.auth.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) {
}
