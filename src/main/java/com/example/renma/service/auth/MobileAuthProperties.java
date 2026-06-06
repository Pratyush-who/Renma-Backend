package com.example.renma.service.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "auth.mobile")
public class MobileAuthProperties {
    private boolean testBypassEnabled = false;
    private String testMobileNumber = "1234567890";
    private String testOtp = "777777";
}
