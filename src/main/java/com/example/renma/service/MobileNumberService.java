package com.example.renma.service;

import org.springframework.stereotype.Service;

@Service
public class MobileNumberService {

    public NormalizedMobileNumber normalize(String rawMobileNumber) {
        if (rawMobileNumber == null || rawMobileNumber.isBlank()) {
            return null;
        }

        String digits = rawMobileNumber.trim().replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }

        if (!digits.matches("\\d{10}")) {
            return null;
        }

        return new NormalizedMobileNumber(digits, digits);
    }

    public record NormalizedMobileNumber(String mobileNumber, String canonicalMobileNumber) {
    }
}
