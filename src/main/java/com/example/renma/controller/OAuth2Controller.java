package com.example.renma.controller;

import com.example.renma.dto.AuthResponse;
import com.example.renma.model.User;
import com.example.renma.repository.UserRepository;
import com.example.renma.security.JwtUtil;
import com.example.renma.service.EmailAddressService;
import com.example.renma.service.EmailAddressService.NormalizedEmail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class OAuth2Controller {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final EmailAddressService emailAddressService;

    public OAuth2Controller(UserRepository userRepository, JwtUtil jwtUtil, EmailAddressService emailAddressService) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.emailAddressService = emailAddressService;
    }

    @GetMapping("/google")
    public ResponseEntity<?> google(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return ResponseEntity.badRequest().body("No principal found");
        }

        NormalizedEmail email = emailAddressService.normalize(principal.getAttribute("email"));
        if (email == null) {
            return ResponseEntity.badRequest().body("Google account did not provide a valid email");
        }

        Optional<User> optionalUser = email.testEmail()
                ? userRepository.findFirstByCanonicalEmailOrderByCreatedAtDesc(email.canonicalEmail())
                : userRepository.findByCanonicalEmail(email.canonicalEmail());
        User user;

        if (optionalUser.isEmpty()) {
            String username = uniqueUsername(principal.getAttribute("name"), email.normalizedEmail());
            user = User.builder()
                    .id(UUID.randomUUID().toString())
                    .email(email.normalizedEmail())
                    .canonicalEmail(email.canonicalEmail())
                    .testAccount(email.testEmail())
                    .username(username)
                    .profilePic(principal.getAttribute("picture"))
                    .plan("free")
                    .isVerified(true)
                    .createdAt(new Date())
                    .build();
            userRepository.save(user);
        } else {
            user = optionalUser.get();
        }

        String token = jwtUtil.generateToken(new org.springframework.security.core.userdetails.User(user.getId(), "", new java.util.ArrayList<>()));
        return ResponseEntity.ok(new AuthResponse(token));
    }

    private String uniqueUsername(String preferredName, String email) {
        String baseUsername = clean(preferredName);
        if (baseUsername == null) {
            baseUsername = clean(email != null ? email.split("@", 2)[0] : null);
        }
        if (baseUsername == null) {
            baseUsername = "user";
        }

        String candidate = baseUsername;
        int suffix = 1;
        while (userRepository.findByUsername(candidate).isPresent()) {
            candidate = baseUsername + suffix;
            suffix++;
        }
        return candidate;
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
