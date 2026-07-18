package com.fieldrealm.game.domain;

import java.time.Instant;

public class UserAccount {
    private String id;
    private String username;
    private String passwordHash;
    private String email;
    private String displayName;
    private String role = "USER";
    private int rating = 1200;
    private int wins;
    private int games;
    private String avatar = "\u5f08";
    private String title = "\u521d\u5165\u5f08\u5883";
    private Instant createdAt = Instant.now();

    public UserAccount() { }
    public UserAccount(String id, String username, String passwordHash, String displayName, String role) {
        this.id = id; this.username = username; this.passwordHash = passwordHash;
        this.displayName = displayName; this.role = role;
        this.avatar = displayName == null || displayName.isBlank() ? "\u5f08" : displayName.substring(0, 1);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }
    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }
    public int getGames() { return games; }
    public void setGames(int games) { this.games = games; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
