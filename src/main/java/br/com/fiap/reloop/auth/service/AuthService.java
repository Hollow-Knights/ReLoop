package br.com.fiap.reloop.auth.service;

import java.util.UUID;

import br.com.fiap.reloop.auth.dto.LoginRequest;
import br.com.fiap.reloop.auth.dto.LoginResponse;
import br.com.fiap.reloop.auth.dto.RegisterUserRequest;
import br.com.fiap.reloop.security.JwtService;
import br.com.fiap.reloop.shared.web.ApiException;
import br.com.fiap.reloop.user.dto.UserResponse;
import br.com.fiap.reloop.user.model.User;
import br.com.fiap.reloop.user.service.UserService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Profile("bootstrap")
public class AuthService {

    private final UserService users;
    private final PasswordEncoder passwords;
    private final JwtService tokens;
    private final String dummyHash;

    public AuthService(UserService users, PasswordEncoder passwords, JwtService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public UserResponse register(RegisterUserRequest request) {
        return users.register(request.name(), request.email(), passwords.encode(request.password()));
    }

    public LoginResponse login(LoginRequest request) {
        var user = users.findByEmail(request.email());
        String hash = user.map(User::passwordHash).orElse(dummyHash);
        if (!passwords.matches(request.password(), hash) || user.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "E-mail ou senha inválidos.");
        }
        return tokens.issue(user.orElseThrow().id());
    }
}
