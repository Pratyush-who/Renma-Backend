package com.example.renma.service.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
public class MobileOtpSessionService {

    private static final Duration OTP_SESSION_TTL = Duration.ofMinutes(5);
    private static final Duration REGISTRATION_TOKEN_TTL = Duration.ofMinutes(15);
    private static final String OTP_SESSION_KEY_PREFIX = "auth:mobile:otp-session:";
    private static final String REGISTRATION_TOKEN_KEY_PREFIX = "auth:mobile:registration:";
    private static final String SEPARATOR = "\n";

    private final StringRedisTemplate redisTemplate;

    public MobileOtpSessionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String createOtpSession(String canonicalMobileNumber, String verifyKey) {
        String verificationId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                otpSessionKey(verificationId),
                canonicalMobileNumber + SEPARATOR + verifyKey,
                OTP_SESSION_TTL
        );
        return verificationId;
    }

    public Optional<String> getVerifyKey(String verificationId, String canonicalMobileNumber) {
        if (verificationId == null || verificationId.isBlank()) {
            return Optional.empty();
        }

        String key = otpSessionKey(verificationId.trim());
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return Optional.empty();
        }

        String[] parts = value.split(SEPARATOR, 2);
        if (parts.length != 2 || !parts[0].equals(canonicalMobileNumber)) {
            return Optional.empty();
        }

        return Optional.of(parts[1]);
    }

    public void clearOtpSession(String verificationId) {
        if (verificationId != null && !verificationId.isBlank()) {
            redisTemplate.delete(otpSessionKey(verificationId.trim()));
        }
    }

    public String createRegistrationToken(String canonicalMobileNumber) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(registrationTokenKey(token), canonicalMobileNumber, REGISTRATION_TOKEN_TTL);
        return token;
    }

    public Optional<String> consumeRegistrationToken(String registrationToken) {
        if (registrationToken == null || registrationToken.isBlank()) {
            return Optional.empty();
        }

        String key = registrationTokenKey(registrationToken.trim());
        String canonicalMobileNumber = redisTemplate.opsForValue().get(key);
        if (canonicalMobileNumber != null) {
            redisTemplate.delete(key);
        }
        return Optional.ofNullable(canonicalMobileNumber);
    }

    public Optional<String> getRegistrationMobileNumber(String registrationToken) {
        if (registrationToken == null || registrationToken.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(redisTemplate.opsForValue().get(registrationTokenKey(registrationToken.trim())));
    }

    public void clearRegistrationToken(String registrationToken) {
        if (registrationToken != null && !registrationToken.isBlank()) {
            redisTemplate.delete(registrationTokenKey(registrationToken.trim()));
        }
    }

    private String otpSessionKey(String verificationId) {
        return OTP_SESSION_KEY_PREFIX + verificationId;
    }

    private String registrationTokenKey(String registrationToken) {
        return REGISTRATION_TOKEN_KEY_PREFIX + registrationToken;
    }
}
