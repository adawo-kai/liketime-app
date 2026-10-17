package com.liketime.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
