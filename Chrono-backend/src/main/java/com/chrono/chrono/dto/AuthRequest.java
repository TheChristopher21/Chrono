package com.chrono.chrono.dto;

public class AuthRequest {
    // The current users.username column and the JPA default support 255 characters.
    public static final int MAX_USERNAME_LENGTH = 255;
    private String username;
    private String password;

    public AuthRequest() {
    }

    public AuthRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    // Getter / Setter
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean hasValidUsernameLength() {
        return username != null && !username.isEmpty()
                && username.length() <= MAX_USERNAME_LENGTH * 2
                && username.codePointCount(0, username.length()) <= MAX_USERNAME_LENGTH;
    }
}
