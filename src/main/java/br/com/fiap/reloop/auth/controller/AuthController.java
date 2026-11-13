package br.com.fiap.reloop.auth.controller;

import br.com.fiap.reloop.auth.dto.LoginRequest;
import br.com.fiap.reloop.auth.dto.LoginResponse;
import br.com.fiap.reloop.auth.dto.RegisterUserRequest;
import br.com.fiap.reloop.auth.service.AuthService;
import br.com.fiap.reloop.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/auth", consumes = MediaType.APPLICATION_JSON_VALUE)
@Profile("bootstrap")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterUserRequest request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }
}
