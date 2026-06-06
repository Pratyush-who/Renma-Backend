package com.example.renma.service.auth;

import com.example.renma.exception.RateLimitExceededException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

@Service
public class OtpService {

    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration OTP_RESEND_COOLDOWN = Duration.ofSeconds(30);
    private static final String OTP_KEY_PREFIX = "auth:otp:";
    private static final String OTP_COOLDOWN_KEY_PREFIX = "auth:otp:cooldown:";
    private static final String EMAIL_VERIFICATION_PURPOSE = "email-verification";
    private static final String PASSWORD_RESET_PURPOSE = "password-reset";

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public OtpService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String createOtp(String userId) {
        return createOtp(userId, EMAIL_VERIFICATION_PURPOSE);
    }

    public String createPasswordResetOtp(String userId) {
        return createOtp(userId, PASSWORD_RESET_PURPOSE);
    }

    private String createOtp(String userId, String purpose) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User id is required");
        }

        if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(cooldownKey(purpose, userId), "1", OTP_RESEND_COOLDOWN))) {
            Long seconds = redisTemplate.getExpire(cooldownKey(purpose, userId), TimeUnit.SECONDS);
            long waitSeconds = seconds == null || seconds <= 0 ? OTP_RESEND_COOLDOWN.toSeconds() : seconds;
            throw new RateLimitExceededException("Please wait " + waitSeconds + " seconds before requesting another OTP.");
        }

        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        redisTemplate.opsForValue().set(key(purpose, userId), hash(purpose, userId, otp), OTP_TTL);
        return otp;
    }

    public boolean verifyOtp(String userId, String otp) {
        return verifyOtp(userId, otp, EMAIL_VERIFICATION_PURPOSE);
    }

    public boolean verifyPasswordResetOtp(String userId, String otp) {
        return verifyOtp(userId, otp, PASSWORD_RESET_PURPOSE);
    }

    private boolean verifyOtp(String userId, String otp, String purpose) {
        if (userId == null || otp == null || otp.isBlank()) {
            return false;
        }

        String storedHash = redisTemplate.opsForValue().get(key(purpose, userId));
        boolean valid = storedHash != null && MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8),
                hash(purpose, userId, otp.trim()).getBytes(StandardCharsets.UTF_8)
        );
        if (valid) {
            clearOtp(purpose, userId);
        }
        return valid;
    }

    public void clearOtp(String userId) {
        if (userId != null) {
            clearOtp(EMAIL_VERIFICATION_PURPOSE, userId);
        }
    }

    private void clearOtp(String purpose, String userId) {
        redisTemplate.delete(key(purpose, userId));
    }

    private String key(String purpose, String userId) {
        return OTP_KEY_PREFIX + purpose + ":" + userId;
    }

    private String cooldownKey(String purpose, String userId) {
        return OTP_COOLDOWN_KEY_PREFIX + purpose + ":" + userId;
    }

    private String hash(String purpose, String userId, String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((purpose + ":" + userId + ":" + otp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
