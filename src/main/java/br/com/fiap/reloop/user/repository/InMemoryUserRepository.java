package br.com.fiap.reloop.user.repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import br.com.fiap.reloop.user.model.User;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("bootstrap")
public class InMemoryUserRepository implements UserRepository {

    private final Map<Long, User> users = new HashMap<>();
    private final Map<String, Long> idsByEmail = new HashMap<>();
    private long nextId = 1L;

    @Override
    public synchronized User create(String name, String normalizedEmail, String passwordHash) {
        if (idsByEmail.containsKey(normalizedEmail)) {
            throw new EmailAlreadyInUse();
        }
        User user = new User(nextId++, name, normalizedEmail, passwordHash);
        users.put(user.id(), user);
        idsByEmail.put(user.email(), user.id());
        return user;
    }

    @Override
    public synchronized Optional<User> findById(Long id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public synchronized Optional<User> findByEmail(String normalizedEmail) {
        return findById(idsByEmail.get(normalizedEmail));
    }

    @Override
    public synchronized Optional<User> updateProfile(Long id, String name, String normalizedEmail) {
        User user = users.get(id);
        if (user == null) {
            return Optional.empty();
        }
        Long emailOwner = idsByEmail.get(normalizedEmail);
        if (emailOwner != null && !emailOwner.equals(id)) {
            throw new EmailAlreadyInUse();
        }
        User updated = user.withProfile(name, normalizedEmail);
        idsByEmail.remove(user.email());
        idsByEmail.put(updated.email(), id);
        users.put(id, updated);
        return Optional.of(updated);
    }
}
