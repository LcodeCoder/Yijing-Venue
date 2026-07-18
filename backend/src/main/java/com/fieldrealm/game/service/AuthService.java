package com.fieldrealm.game.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fieldrealm.game.domain.UserAccount;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private final Map<String, UserAccount> usersById = new LinkedHashMap<>();
    private final Map<String, String> idByUsername = new HashMap<>();
    private final Map<String, String> idByEmail = new HashMap<>();
    private final Map<String, String> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Path storage = Paths.get("data", "users.json");
    private final SecureRandom random = new SecureRandom();

    public AuthService() {
        load();
        if (usersById.isEmpty()) {
            UserAccount admin = new UserAccount("admin", "admin", hash("admin123"), "\u5f08\u5883\u7ba1\u7406\u5458", "ADMIN");
            admin.setRating(2400); admin.setTitle("\u79e9\u5e8f\u5b88\u95e8\u4eba"); admin.setAvatar("\u7ba1");
            put(admin);
            seedPlayer("seed-1", "boundary", "\u65e0\u754c\u884c\u8005", 2486, 53, 72, "\u754c");
            seedPlayer("seed-2", "stargazer", "\u767d\u663c\u89c2\u661f", 2392, 48, 69, "\u661f");
            seedPlayer("seed-3", "rock", "\u7384\u5ca9\u4e0d\u52a8", 2318, 44, 66, "\u5ca9");
            save();
        }
    }

    public synchronized Map<String, Object> register(String username, String password, String displayName, String email) {
        String key = normalize(username);
        if (idByUsername.containsKey(key)) throw new IllegalArgumentException("\u7528\u6237\u540d\u5df2\u5b58\u5728");
        String normalizedEmail = normalizeEmail(email);
        if (!normalizedEmail.isBlank() && idByEmail.containsKey(normalizedEmail)) throw new IllegalArgumentException("\u8be5\u90ae\u7bb1\u5df2\u7ed1\u5b9a\u5176\u4ed6\u8d26\u53f7");
        UserAccount user = new UserAccount(UUID.randomUUID().toString(), key, hash(password), displayName.trim(), "USER");
        user.setEmail(normalizedEmail.isBlank() ? null : normalizedEmail);
        put(user); save();
        return sessionResponse(user);
    }

    public synchronized Map<String, Object> login(String username, String password) {
        UserAccount user = findByUsername(username);
        if (user == null || !verify(password, user.getPasswordHash())) throw new IllegalArgumentException("\u7528\u6237\u540d\u6216\u5bc6\u7801\u9519\u8bef");
        return sessionResponse(user);
    }

    public synchronized Map<String, Object> loginByEmail(String email) {
        UserAccount user = findByEmail(email);
        if (user == null) throw new IllegalArgumentException("\u8be5\u90ae\u7bb1\u5c1a\u672a\u7ed1\u5b9a\u8d26\u53f7");
        return sessionResponse(user);
    }

    public synchronized boolean existsByEmail(String email) { return findByEmail(email) != null; }

    public synchronized List<Map<String, Object>> adminUsers() {
        return usersById.values().stream()
                .sorted(Comparator.comparing(UserAccount::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(user -> {
                    Map<String, Object> row = new LinkedHashMap<>(publicView(user));
                    row.put("winRate", user.getGames() == 0 ? 0 : Math.round(user.getWins() * 100f / user.getGames()));
                    return row;
                }).toList();
    }

    public UserAccount optional(String authorization) {
        String token = bearer(authorization);
        String userId = token == null ? null : sessions.get(token);
        return userId == null ? null : usersById.get(userId);
    }

    public UserAccount require(String authorization) {
        UserAccount user = optional(authorization);
        if (user == null) throw new IllegalArgumentException("\u8bf7\u5148\u767b\u5f55");
        return user;
    }

    public UserAccount requireAdmin(String authorization) {
        UserAccount user = require(authorization);
        if (!"ADMIN".equals(user.getRole())) throw new IllegalArgumentException("\u9700\u8981\u7ba1\u7406\u5458\u6743\u9650");
        return user;
    }

    public synchronized Map<String, Object> update(String authorization, String displayName, String avatar, String title) {
        UserAccount user = require(authorization);
        user.setDisplayName(displayName.trim());
        if (avatar != null && !avatar.isBlank()) user.setAvatar(avatar.trim());
        if (title != null && !title.isBlank()) user.setTitle(title.trim());
        save();
        return publicView(user);
    }

    public synchronized void recordRankedResult(String winnerAccountId, String loserAccountId) {
        UserAccount winner = usersById.get(winnerAccountId), loser = usersById.get(loserAccountId);
        if (winner == null || loser == null || winner == loser) return;
        double expectedWinner = 1d / (1d + Math.pow(10d, (loser.getRating() - winner.getRating()) / 400d));
        int delta = Math.max(8, (int) Math.round(28 * (1 - expectedWinner)));
        winner.setRating(winner.getRating() + delta); winner.setWins(winner.getWins() + 1); winner.setGames(winner.getGames() + 1);
        loser.setRating(Math.max(0, loser.getRating() - delta)); loser.setGames(loser.getGames() + 1);
        save();
    }

    public synchronized List<Map<String, Object>> rankings() {
        List<UserAccount> sorted = usersById.values().stream().sorted(Comparator.comparingInt(UserAccount::getRating).reversed()).toList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            UserAccount user = sorted.get(i);
            Map<String, Object> row = new LinkedHashMap<>(publicView(user));
            row.put("rank", i + 1);
            row.put("tier", tier(user.getRating()));
            row.put("winRate", user.getGames() == 0 ? 0 : Math.round(user.getWins() * 100f / user.getGames()));
            result.add(row);
        }
        return result;
    }

    public Map<String, Object> publicView(UserAccount user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.getId()); view.put("username", user.getUsername()); view.put("email", user.getEmail()); view.put("name", user.getDisplayName());
        view.put("displayName", user.getDisplayName()); view.put("role", user.getRole()); view.put("rating", user.getRating());
        view.put("wins", user.getWins()); view.put("games", user.getGames()); view.put("avatar", user.getAvatar());
        view.put("title", user.getTitle()); view.put("rank", tier(user.getRating())); view.put("createdAt", user.getCreatedAt());
        return view;
    }

    private Map<String, Object> sessionResponse(UserAccount user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, user.getId());
        return Map.of("token", token, "user", publicView(user));
    }

    private void seedPlayer(String id, String username, String name, int rating, int wins, int games, String avatar) {
        UserAccount user = new UserAccount(id, username, hash(UUID.randomUUID().toString()), name, "USER");
        user.setRating(rating); user.setWins(wins); user.setGames(games); user.setAvatar(avatar); user.setTitle("\u8d5b\u5b63\u6267\u68cb\u8005"); put(user);
    }
    private void put(UserAccount user) { usersById.put(user.getId(), user); idByUsername.put(normalize(user.getUsername()), user.getId()); if (user.getEmail() != null && !user.getEmail().isBlank()) idByEmail.put(normalizeEmail(user.getEmail()), user.getId()); }
    private UserAccount findByUsername(String username) { String id = idByUsername.get(normalize(username)); return id == null ? null : usersById.get(id); }
    private UserAccount findByEmail(String email) { String id = idByEmail.get(normalizeEmail(email)); return id == null ? null : usersById.get(id); }
    private String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private String normalizeEmail(String value) { return normalize(value); }
    private String bearer(String header) { return header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null; }
    private String tier(int rating) { if (rating >= 2400) return "\u5b97\u5e08"; if (rating >= 2000) return "\u66dc\u77f3 I"; if (rating >= 1700) return "\u66dc\u77f3 III"; if (rating >= 1400) return "\u661f\u94f6"; return "\u9752\u94dc"; }

    private String hash(String password) {
        try {
            byte[] salt = new byte[16]; random.nextBytes(salt);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 120_000, 256);
            byte[] value = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(value);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private boolean verify(String password, String encoded) {
        try {
            String[] parts = encoded.split(":", 2); byte[] salt = Base64.getDecoder().decode(parts[0]);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 120_000, 256);
            byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(actual, Base64.getDecoder().decode(parts[1]));
        } catch (Exception e) { return false; }
    }
    private void load() {
        try {
            if (!Files.exists(storage)) return;
            List<UserAccount> users = mapper.readValue(storage.toFile(), new TypeReference<>() {});
            users.forEach(this::put);
        } catch (Exception ignored) { usersById.clear(); idByUsername.clear(); idByEmail.clear(); }
    }
    private void save() {
        try {
            Files.createDirectories(storage.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(storage.toFile(), new ArrayList<>(usersById.values()));
        } catch (Exception e) { throw new IllegalStateException("Failed to save users", e); }
    }
}
