package com.example.renma.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "obligr.otp")
public class ObligrOtpProperties {
    private boolean enabled = true;
    private String baseUrl = "https://obligr.io";
    private String apiKey;
    private String senderId;
    private String headerName;
    private String message = "Your OTP Verification Code is : ##OTP##";
    private String dltTemplateId;
    private String dltPeId;
    private String callerId;
    private String emailId;
    private int expireTime = 180;
    private int otpLength = 6;
}
