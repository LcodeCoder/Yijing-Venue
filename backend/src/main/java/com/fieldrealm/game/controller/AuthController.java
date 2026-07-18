package com.fieldrealm.game.controller;

import com.fieldrealm.game.dto.*;
import com.fieldrealm.game.service.AuthService;
import com.fieldrealm.game.service.EmailVerificationService;
import com.fieldrealm.game.service.MailSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final EmailVerificationService email;
    private final MailSettingsService mailSettings;

    public AuthController(AuthService auth, EmailVerificationService email, MailSettingsService mailSettings) {
        this.auth = auth; this.email = email; this.mailSettings = mailSettings;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request) {
        String verifiedEmail = null;
        if (mailSettings.isEnabled()) {
            if (request.email() == null || request.email().isBlank() || request.code() == null || request.code().isBlank()) {
                throw new IllegalArgumentException("\u8bf7\u8f93\u5165\u90ae\u7bb1\u548c\u9a8c\u8bc1\u7801");
            }
            if (auth.existsByEmail(request.email())) throw new IllegalArgumentException("\u8be5\u90ae\u7bb1\u5df2\u7ed1\u5b9a\u5176\u4ed6\u8d26\u53f7");
            if (!email.verify(request.email(), "REGISTER", request.code())) throw new IllegalArgumentException("\u90ae\u7bb1\u9a8c\u8bc1\u7801\u9519\u8bef\u6216\u5df2\u8fc7\u671f");
            verifiedEmail = request.email();
        }
        return auth.register(request.username(), request.password(), request.displayName(), verifiedEmail);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) { return auth.login(request.username(), request.password()); }

    @PostMapping("/email-code")
    public Map<String, Object> sendEmailCode(@Valid @RequestBody EmailCodeRequest request) {
        String purpose = request.purpose().trim().toUpperCase();
        if ("LOGIN".equals(purpose) && !auth.existsByEmail(request.email())) throw new IllegalArgumentException("\u8be5\u90ae\u7bb1\u5c1a\u672a\u7ed1\u5b9a\u8d26\u53f7");
        if ("REGISTER".equals(purpose) && auth.existsByEmail(request.email())) throw new IllegalArgumentException("\u8be5\u90ae\u7bb1\u5df2\u7ed1\u5b9a\u5176\u4ed6\u8d26\u53f7");
        email.sendCode(request.email(), purpose);
        return Map.of("message", "\u9a8c\u8bc1\u7801\u5df2\u53d1\u9001", "expiresIn", 300, "resendIn", 60);
    }

    @PostMapping("/login/email")
    public Map<String, Object> emailLogin(@Valid @RequestBody EmailCodeLoginRequest request) {
        if (!email.verify(request.email(), "LOGIN", request.code())) throw new IllegalArgumentException("\u90ae\u7bb1\u9a8c\u8bc1\u7801\u9519\u8bef\u6216\u5df2\u8fc7\u671f");
        return auth.loginByEmail(request.email());
    }

    @GetMapping("/email-status")
    public Map<String, Object> emailStatus() { return Map.of("enabled", mailSettings.isEnabled()); }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return auth.publicView(auth.require(authorization));
    }

    @PutMapping("/me")
    public Map<String, Object> update(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @Valid @RequestBody ProfileUpdateRequest request) {
        return auth.update(authorization, request.displayName(), request.avatar(), request.title());
    }
}
