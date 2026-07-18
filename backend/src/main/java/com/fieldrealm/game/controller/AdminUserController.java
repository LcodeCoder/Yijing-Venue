package com.fieldrealm.game.controller;

import com.fieldrealm.game.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AuthService auth;
    public AdminUserController(AuthService auth) { this.auth = auth; }

    @GetMapping
    public List<Map<String, Object>> all(@RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.requireAdmin(authorization);
        return auth.adminUsers();
    }
}
