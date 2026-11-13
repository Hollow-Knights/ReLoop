package br.com.fiap.reloop.user.repository;

import java.util.Optional;

import br.com.fiap.reloop.user.model.User;

public interface UserRepository {

    /** Cria o usuário e sua carteira vazia na mesma unidade de trabalho. */
    User create(String name, String normalizedEmail, String passwordHash);

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String normalizedEmail);

    Optional<User> updateProfile(Long id, String name, String normalizedEmail);

    class EmailAlreadyInUse extends RuntimeException {
    }
}
