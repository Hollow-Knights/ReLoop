package br.com.fiap.reloop.user.controller;

import br.com.fiap.reloop.user.dto.UpdateUserRequest;
import br.com.fiap.reloop.user.dto.UserResponse;
import br.com.fiap.reloop.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@Profile("bootstrap")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    public UserResponse profile() {
        return users.profile();
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public UserResponse updateProfile(@Valid @RequestBody UpdateUserRequest request) {
        return users.updateProfile(request);
    }
}
