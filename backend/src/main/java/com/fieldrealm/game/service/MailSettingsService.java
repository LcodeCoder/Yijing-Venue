package com.fieldrealm.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldrealm.game.domain.MailSettings;
import com.fieldrealm.game.dto.MailSettingsRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MailSettingsService {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Path storage = Paths.get("data", "mail-settings.json");
    private MailSettings settings;

    public MailSettingsService(
            @Value("${fieldrealm.mail.enabled:false}") boolean enabled,
            @Value("${fieldrealm.mail.host:smtp.qq.com}") String host,
            @Value("${fieldrealm.mail.port:465}") int port,
            @Value("${fieldrealm.mail.ssl:true}") boolean ssl,
            @Value("${fieldrealm.mail.username:}") String username,
            @Value("${fieldrealm.mail.password:}") String password,
            @Value("${fieldrealm.mail.from-name:Field Realm}") String fromName
    ) {
        settings = load();
        if (settings == null) {
            settings = new MailSettings();
            settings.setEnabled(enabled);
            settings.setHost(host);
            settings.setPort(port);
            settings.setSsl(ssl);
            settings.setUsername(username);
            settings.setPassword(password);
            settings.setFromName(fromName);
        }
    }

    public synchronized MailSettings current() {
        return copy(settings);
    }

    /** 登录/注册入口只在“已启用且配置完整”时开放。 */
    public synchronized boolean isEnabled() {
        return settings.isEnabled() && isConfigured(settings);
    }

    public boolean isConfigured(MailSettings value) {
        return value != null
                && value.getHost() != null && !value.getHost().isBlank()
                && value.getPort() > 0
                && value.getUsername() != null && !value.getUsername().isBlank()
                && value.getPassword() != null && !value.getPassword().isBlank();
    }

    public synchronized Map<String, Object> publicView() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("enabled", settings.isEnabled());
        view.put("configured", isConfigured(settings));
        view.put("host", settings.getHost());
        view.put("port", settings.getPort());
        view.put("ssl", settings.isSsl());
        view.put("username", settings.getUsername());
        view.put("passwordConfigured", settings.getPassword() != null && !settings.getPassword().isBlank());
        view.put("fromName", settings.getFromName());
        view.put("providerHint", "QQ 邮箱推荐 smtp.qq.com:465 + SSL，密码请填写 SMTP 授权码，而不是邮箱登录密码");
        return view;
    }

    public synchronized Map<String, Object> update(MailSettingsRequest request) {
        MailSettings next = new MailSettings();
        next.setEnabled(request.enabled());
        next.setHost(request.host().trim());
        next.setPort(request.port());
        next.setSsl(request.ssl());
        next.setUsername(request.username() == null ? "" : request.username().trim());
        next.setPassword(request.password() == null || request.password().isBlank()
                ? settings.getPassword()
                : request.password().trim());
        next.setFromName(request.fromName().trim());

        if (next.isEnabled() && !isConfigured(next)) {
            throw new IllegalArgumentException("启用邮箱验证码前，请填写 SMTP 服务器、发件邮箱和授权码");
        }

        settings = next;
        save();
        return publicView();
    }

    private MailSettings load() {
        try {
            return Files.exists(storage) ? mapper.readValue(storage.toFile(), MailSettings.class) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void save() {
        try {
            Files.createDirectories(storage.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(storage.toFile(), settings);
        } catch (Exception e) {
            throw new IllegalStateException("保存邮件配置失败", e);
        }
    }

    private MailSettings copy(MailSettings source) {
        MailSettings target = new MailSettings();
        target.setEnabled(source.isEnabled());
        target.setHost(source.getHost());
        target.setPort(source.getPort());
        target.setSsl(source.isSsl());
        target.setUsername(source.getUsername());
        target.setPassword(source.getPassword());
        target.setFromName(source.getFromName());
        return target;
    }
}
