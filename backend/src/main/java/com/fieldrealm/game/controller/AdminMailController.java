package com.fieldrealm.game.controller;

import com.fieldrealm.game.dto.MailSettingsRequest;
import com.fieldrealm.game.dto.MailTestRequest;
import com.fieldrealm.game.service.AuthService;
import com.fieldrealm.game.service.EmailVerificationService;
import com.fieldrealm.game.service.MailSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/mail")
public class AdminMailController {
    private final AuthService auth;
    private final MailSettingsService settings;
    private final EmailVerificationService email;

    public AdminMailController(AuthService auth, MailSettingsService settings, EmailVerificationService email) {
        this.auth = auth; this.settings = settings; this.email = email;
    }

    @GetMapping
    public Map<String, Object> get(@RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.requireAdmin(authorization); return settings.publicView();
    }

    @PutMapping
    public Map<String, Object> update(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @Valid @RequestBody MailSettingsRequest request) {
        auth.requireAdmin(authorization); return settings.update(request);
    }

    @PostMapping("/test")
    public Map<String, String> test(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @Valid @RequestBody MailTestRequest request) {
        auth.requireAdmin(authorization); email.sendTest(request.email());
        return Map.of("message", "\u6d4b\u8bd5\u90ae\u4ef6\u5df2\u53d1\u9001\uff0c\u8bf7\u68c0\u67e5\u6536\u4ef6\u7bb1");
    }
}
