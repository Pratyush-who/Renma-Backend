package com.example.renma.service;

import com.example.renma.exception.RateLimitExceededException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@Service
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void checkRegistrationLimit(String deviceKey, String canonicalEmail, boolean testEmail) {
        assertUnderLimit("auth:rl:register:device:minute:" + deviceKey, 5, Duration.ofMinutes(1));
        assertUnderLimit("auth:rl:register:device:hour:" + deviceKey, 25, Duration.ofHours(1));

        if (!testEmail) {
            assertUnderLimit("auth:rl:register:email:hour:" + hash(canonicalEmail), 3, Duration.ofHours(1));
        }
    }

    public void checkVerificationLimit(String deviceKey, String canonicalEmail) {
        assertUnderLimit("auth:rl:verify:device:minute:" + deviceKey, 10, Duration.ofMinutes(1));
        assertUnderLimit("auth:rl:verify:email:minute:" + hash(canonicalEmail), 5, Duration.ofMinutes(1));
    }

    public String fingerprint(String value) {
        return hash(value == null ? "unknown" : value);
    }

    private void assertUnderLimit(String key, long limit, Duration ttl) {
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(key, ttl);
        }
        if (attempts != null && attempts > limit) {
            throw new RateLimitExceededException("Too many requests. Please try again later.");
        }
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
