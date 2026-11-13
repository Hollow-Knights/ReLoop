package br.com.fiap.reloop.user.model;

public record User(Long id, String name, String email, String passwordHash) {

    public User withProfile(String updatedName, String updatedEmail) {
        return new User(id, updatedName, updatedEmail, passwordHash);
    }
}
