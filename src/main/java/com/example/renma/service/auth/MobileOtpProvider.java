package com.example.renma.service.auth;

public interface MobileOtpProvider {
    String sendOtp(String mobileNumber);

    boolean verifyOtp(String mobileNumber, String verifyKey, String otp);
}
