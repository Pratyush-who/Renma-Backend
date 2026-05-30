package com.example.renma.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;

@Service
public class OtpService {

    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final String OTP_KEY_PREFIX = "auth:otp:";

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public OtpService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String createOtp(String userId) {
        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        redisTemplate.opsForValue().set(key(userId), hash(userId, otp), OTP_TTL);
        return otp;
    }

    public boolean verifyOtp(String userId, String otp) {
        if (userId == null || otp == null || otp.isBlank()) {
            return false;
        }

        String storedHash = redisTemplate.opsForValue().get(key(userId));
        return storedHash != null && MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8),
                hash(userId, otp.trim()).getBytes(StandardCharsets.UTF_8)
        );
    }

    public void clearOtp(String userId) {
        if (userId != null) {
            redisTemplate.delete(key(userId));
        }
    }

    private String key(String userId) {
        return OTP_KEY_PREFIX + userId;
    }

    private String hash(String userId, String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((userId + ":" + otp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
