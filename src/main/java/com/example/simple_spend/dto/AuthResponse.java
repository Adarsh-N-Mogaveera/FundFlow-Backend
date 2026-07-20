package com.example.simple_spend.dto;

public class AuthResponse {
    private String token;
    private String username;
    private String userId;

    public AuthResponse(String token, String username, String userId) {
        this.token = token;
        this.username = username;
        this.userId = userId;
    }

    public String getToken() { return token; }
    public String getUsername() { return username; }
    public String getUserId() { return userId; }
}