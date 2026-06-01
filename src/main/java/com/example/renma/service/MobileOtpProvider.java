package com.example.renma.service;

public interface MobileOtpProvider {
    String sendOtp(String mobileNumber);

    boolean verifyOtp(String mobileNumber, String verifyKey, String otp);
}
