package com.example.renma.service;

import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class EmailAddressService {

    private static final String TEST_EMAIL = "test@gmail.com";

    public NormalizedEmail normalize(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            return null;
        }

        String normalizedEmail = rawEmail.trim().toLowerCase(Locale.ROOT);
        int atIndex = normalizedEmail.indexOf('@');
        if (atIndex <= 0 || atIndex != normalizedEmail.lastIndexOf('@') || atIndex == normalizedEmail.length() - 1) {
            return null;
        }

        String localPart = normalizedEmail.substring(0, atIndex);
        String domain = normalizedEmail.substring(atIndex + 1);

        if (localPart.isBlank() || domain.isBlank()) {
            return null;
        }

        return new NormalizedEmail(normalizedEmail, normalizedEmail, TEST_EMAIL.equals(normalizedEmail));
    }

    public record NormalizedEmail(String normalizedEmail, String canonicalEmail, boolean testEmail) {
    }
}
