package br.com.fiap.reloop.user.dto;

import br.com.fiap.reloop.user.model.User;

public record UserResponse(Long id, String name, String email) {

    public static UserResponse from(User user) {
        return new UserResponse(user.id(), user.name(), user.email());
    }
}
