package com.example.renma.service;

import com.example.renma.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void createsOtpWithFiveMinuteTtlAndThirtySecondCooldown() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("auth:otp:cooldown:email-verification:user-1", "1", Duration.ofSeconds(30))).thenReturn(true);

        OtpService otpService = new OtpService(redisTemplate);
        String otp = otpService.createOtp("user-1");

        assertThat(otp).matches("\\d{6}");
        verify(valueOperations).setIfAbsent("auth:otp:cooldown:email-verification:user-1", "1", Duration.ofSeconds(30));
        verify(valueOperations).set(eq("auth:otp:email-verification:user-1"), org.mockito.ArgumentMatchers.anyString(), eq(Duration.ofMinutes(5)));
    }

    @Test
    void rejectsOtpCreationDuringCooldown() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("auth:otp:cooldown:email-verification:user-1", "1", Duration.ofSeconds(30))).thenReturn(false);
        when(redisTemplate.getExpire("auth:otp:cooldown:email-verification:user-1", TimeUnit.SECONDS)).thenReturn(30L);

        OtpService otpService = new OtpService(redisTemplate);

        assertThatThrownBy(() -> otpService.createOtp("user-1"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessage("Please wait 30 seconds before requesting another OTP.");
    }

    @Test
    void deletesOtpAfterSuccessfulVerification() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("auth:otp:cooldown:email-verification:user-1", "1", Duration.ofSeconds(30))).thenReturn(true);
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);

        OtpService otpService = new OtpService(redisTemplate);
        String otp = otpService.createOtp("user-1");

        verify(valueOperations).set(eq("auth:otp:email-verification:user-1"), hashCaptor.capture(), eq(Duration.ofMinutes(5)));
        when(valueOperations.get("auth:otp:email-verification:user-1")).thenReturn(hashCaptor.getValue());

        assertThat(otpService.verifyOtp("user-1", otp)).isTrue();
        verify(redisTemplate).delete("auth:otp:email-verification:user-1");
    }

    @Test
    void createsPasswordResetOtpInSeparateNamespace() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("auth:otp:cooldown:password-reset:user-1", "1", Duration.ofSeconds(30))).thenReturn(true);

        OtpService otpService = new OtpService(redisTemplate);
        String otp = otpService.createPasswordResetOtp("user-1");

        assertThat(otp).matches("\\d{6}");
        verify(valueOperations).setIfAbsent("auth:otp:cooldown:password-reset:user-1", "1", Duration.ofSeconds(30));
        verify(valueOperations).set(eq("auth:otp:password-reset:user-1"), org.mockito.ArgumentMatchers.anyString(), eq(Duration.ofMinutes(5)));
    }
}
