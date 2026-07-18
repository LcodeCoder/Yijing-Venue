package com.fieldrealm.game.service;

import com.fieldrealm.game.domain.MailSettings;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmailVerificationService {
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration SEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;

    private final MailSettingsService settingsService;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, VerificationCode> codes = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastSentAt = new ConcurrentHashMap<>();

    public EmailVerificationService(MailSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public void sendCode(String email, String purpose) {
        MailSettings settings = requireConfiguredSettings(true);
        String normalizedEmail = normalizeEmail(email);
        String normalizedPurpose = normalizePurpose(purpose);
        String key = key(normalizedEmail, normalizedPurpose);
        Instant now = Instant.now();
        Instant last = lastSentAt.get(key);
        if (last != null && now.isBefore(last.plus(SEND_COOLDOWN))) {
            long seconds = Math.max(1, Duration.between(now, last.plus(SEND_COOLDOWN)).toSeconds());
            throw new IllegalArgumentException("发送过于频繁，请 " + seconds + " 秒后重试");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        send(
                settings,
                normalizedEmail,
                normalizedBrandName(settings.getFromName()) + " 邮箱验证码",
                buildCodeContent(code, normalizedPurpose, settings.getFromName())
        );

        // 与参考项目保持一致：只有 SMTP 真正发送成功后才保存验证码。
        codes.put(key, new VerificationCode(code, now.plus(CODE_TTL), 0));
        lastSentAt.put(key, now);
    }

    public boolean verify(String email, String purpose, String code) {
        String key = key(normalizeEmail(email), normalizePurpose(purpose));
        VerificationCode stored = codes.get(key);
        if (stored == null) return false;
        if (Instant.now().isAfter(stored.expiresAt()) || stored.attempts() >= MAX_ATTEMPTS) {
            codes.remove(key);
            return false;
        }
        if (!stored.code().equals(code == null ? "" : code.trim())) {
            codes.put(key, new VerificationCode(stored.code(), stored.expiresAt(), stored.attempts() + 1));
            return false;
        }
        codes.remove(key);
        return true;
    }

    public void sendTest(String email) {
        MailSettings settings = requireConfiguredSettings(false);
        send(
                settings,
                normalizeEmail(email),
                normalizedBrandName(settings.getFromName()) + " SMTP 测试",
                "【" + normalizedBrandName(settings.getFromName()) + "】\n\n邮件通道配置成功。\n现在可以在登录页使用邮箱验证码登录。\n\n测试时间：" + Instant.now()
        );
    }

    private String buildCodeContent(String code, String purpose, String fromName) {
        String action = "REGISTER".equals(purpose) ? "注册账号" : "登录账号";
        return "【" + normalizedBrandName(fromName) + "】\n\n"
                + "您好！\n"
                + "您正在使用邮箱验证码" + action + "。\n\n"
                + "本次验证码：" + code + "\n\n"
                + "有效期：5 分钟\n"
                + "请勿将验证码泄露给他人，如非本人操作请忽略本邮件。";
    }

    private String normalizedBrandName(String fromName) {
        return fromName == null || fromName.isBlank() ? "场地弈境" : fromName.trim();
    }

    private MailSettings requireConfiguredSettings(boolean requireEnabled) {
        MailSettings settings = settingsService.current();
        if (requireEnabled && !settings.isEnabled()) {
            throw new IllegalArgumentException("管理员尚未启用邮箱验证码登录");
        }
        if (!settingsService.isConfigured(settings)) {
            throw new IllegalArgumentException("SMTP 邮件配置不完整，请填写服务器、发件邮箱和授权码");
        }
        return settings;
    }

    private void send(MailSettings settings, String recipient, String subject, String content) {
        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(settings.getHost());
            sender.setPort(settings.getPort());
            sender.setUsername(settings.getUsername());
            sender.setPassword(settings.getPassword());
            sender.setDefaultEncoding("UTF-8");

            Properties properties = sender.getJavaMailProperties();
            properties.put("mail.transport.protocol", "smtp");
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtp.connectiontimeout", "10000");
            properties.put("mail.smtp.timeout", "10000");
            properties.put("mail.smtp.writetimeout", "10000");
            properties.put("mail.smtp.ssl.enable", String.valueOf(settings.isSsl()));
            properties.put("mail.smtp.ssl.required", String.valueOf(settings.isSsl()));
            if (!settings.isSsl()) {
                properties.put("mail.smtp.starttls.enable", "true");
            }

            SimpleMailMessage message = new SimpleMailMessage();
            // QQ 邮箱要求 From 与 SMTP 登录账号保持一致；显示名放在正文和主题中，兼容性更稳。
            message.setFrom(settings.getUsername());
            message.setTo(recipient);
            message.setSubject(subject);
            message.setText(content);
            sender.send(message);
        } catch (Exception e) {
            throw new IllegalArgumentException("邮件发送失败，请检查 SMTP 地址、端口、发件邮箱和授权码");
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePurpose(String purpose) {
        String value = purpose == null ? "" : purpose.trim().toUpperCase(Locale.ROOT);
        if (!value.equals("LOGIN") && !value.equals("REGISTER")) {
            throw new IllegalArgumentException("不支持的验证码用途");
        }
        return value;
    }

    private String key(String email, String purpose) {
        return purpose + ":" + email;
    }

    private record VerificationCode(String code, Instant expiresAt, int attempts) { }
}
