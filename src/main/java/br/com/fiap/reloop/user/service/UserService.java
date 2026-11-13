package br.com.fiap.reloop.user.service;

import java.util.Locale;
import java.util.Optional;

import br.com.fiap.reloop.security.CurrentUserProvider;
import br.com.fiap.reloop.shared.web.ApiException;
import br.com.fiap.reloop.user.dto.UpdateUserRequest;
import br.com.fiap.reloop.user.dto.UserResponse;
import br.com.fiap.reloop.user.model.User;
import br.com.fiap.reloop.user.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Profile("bootstrap")
public class UserService {

    private final UserRepository users;
    private final CurrentUserProvider currentUser;

    public UserService(UserRepository users, CurrentUserProvider currentUser) {
        this.users = users;
        this.currentUser = currentUser;
    }

    public UserResponse register(String name, String email, String passwordHash) {
        try {
            return UserResponse.from(users.create(name.strip(), normalizeEmail(email), passwordHash));
        } catch (UserRepository.EmailAlreadyInUse conflict) {
            throw emailConflict();
        }
    }

    public Optional<User> findByEmail(String email) {
        return users.findByEmail(normalizeEmail(email));
    }

    public UserResponse profile() {
        return users.findById(currentUser.userId()).map(UserResponse::from).orElseThrow(this::invalidIdentity);
    }

    public UserResponse updateProfile(UpdateUserRequest request) {
        try {
            return users.updateProfile(currentUser.userId(), request.name().strip(), normalizeEmail(request.email()))
                    .map(UserResponse::from).orElseThrow(this::invalidIdentity);
        } catch (UserRepository.EmailAlreadyInUse conflict) {
            throw emailConflict();
        }
    }

    private String normalizeEmail(String email) {
        String normalized = email.toLowerCase(Locale.ROOT);
        if (normalized.length() > 254) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "O e-mail excede o tamanho permitido.");
        }
        return normalized;
    }

    private ApiException emailConflict() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_IN_USE", "E-mail já cadastrado.");
    }

    private ApiException invalidIdentity() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Identidade do usuário indisponível.");
    }
}
