package br.com.fiap.reloop.user.model;

public record User(Long id, String name, String email, String passwordHash, String document) {

    public User withProfile(String updatedName, String updatedEmail) {
        return new User(id, updatedName, updatedEmail, passwordHash, document);
    }

    @Override public String toString() { return "User[id=" + id + "]"; }
}
