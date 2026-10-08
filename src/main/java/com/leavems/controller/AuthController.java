package com.leavems.controller;

import com.leavems.dto.AuthResponse;
import com.leavems.dto.LoginRequest;
import com.leavems.dto.SignupRequest;
import com.leavems.dto.UserResponse;
import com.leavems.entity.User;
import com.leavems.security.CurrentUser;
import com.leavems.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;

    public AuthController(AuthService authService, CurrentUser currentUser) {
        this.authService = authService;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    /** Lets the client rehydrate its session after a page refresh. */
    @GetMapping("/me")
    public UserResponse me() {
        User user = currentUser.require();
        return AuthService.toResponse(user);
    }
}