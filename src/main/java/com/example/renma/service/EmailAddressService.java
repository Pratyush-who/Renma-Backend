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

        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex != email.lastIndexOf('@') || atIndex == email.length() - 1) {
            return null;
        }

        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex + 1);
        String canonicalDomain = "googlemail.com".equals(domain) ? "gmail.com" : domain;
        String canonicalLocalPart = localPart;

        if ("gmail.com".equals(canonicalDomain)) {
            int plusIndex = canonicalLocalPart.indexOf('+');
            if (plusIndex >= 0) {
                canonicalLocalPart = canonicalLocalPart.substring(0, plusIndex);
            }
            canonicalLocalPart = canonicalLocalPart.replace(".", "");
        }

        if (canonicalLocalPart.isBlank()) {
            return null;
        }

        String canonicalEmail = canonicalLocalPart + "@" + canonicalDomain;
        return new NormalizedEmail(email, canonicalEmail, TEST_EMAIL.equals(canonicalEmail));
    }

    public record NormalizedEmail(String email, String canonicalEmail, boolean testEmail) {
    }
}
