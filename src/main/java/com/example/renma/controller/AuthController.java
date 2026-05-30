package com.example.renma.controller;

import com.example.renma.dto.AuthRequest;
import com.example.renma.dto.AuthResponse;
import com.example.renma.dto.RegisterRequest;
import com.example.renma.dto.VerifyRequest;
import com.example.renma.exception.RateLimitExceededException;
import com.example.renma.model.User;
import com.example.renma.repository.UserRepository;
import com.example.renma.security.JwtUtil;
import com.example.renma.service.EmailAddressService;
import com.example.renma.service.EmailAddressService.NormalizedEmail;
import com.example.renma.service.EmailService;
import com.example.renma.service.OtpService;
import com.example.renma.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final EmailAddressService emailAddressService;
    private final OtpService otpService;
    private final RateLimitService rateLimitService;

    public AuthController(
            JwtUtil jwtUtil,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            EmailAddressService emailAddressService,
            OtpService otpService,
            RateLimitService rateLimitService
    ) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.emailAddressService = emailAddressService;
        this.otpService = otpService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest registerRequest, HttpServletRequest request) {
        String username = clean(registerRequest.getUsername());
        NormalizedEmail email = emailAddressService.normalize(registerRequest.getEmail());
        String password = registerRequest.getPassword();

        if (username == null) {
            return ResponseEntity.badRequest().body("Username is required");
        }
        if (email == null) {
            return ResponseEntity.badRequest().body("Valid email is required");
        }
        if (password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body("Password is required");
        }

        try {
            rateLimitService.checkRegistrationLimit(deviceKey(request), email.canonicalEmail(), email.testEmail());

            if (userRepository.findByUsername(username).isPresent()) {
                return ResponseEntity.badRequest().body("Username is already taken!");
            }
            if (!email.testEmail() && userRepository.findByCanonicalEmail(email.canonicalEmail()).isPresent()) {
                return ResponseEntity.badRequest().body("Email is already taken!");
            }

            User user = User.builder()
                    .id(UUID.randomUUID().toString())
                    .username(username)
                    .email(email.email())
                    .canonicalEmail(email.canonicalEmail())
                    .testAccount(email.testEmail())
                    .password(passwordEncoder.encode(password))
                    .profilePic(registerRequest.getProfilePic())
                    .isVerified(false)
                    .createdAt(new Date())
                    .build();

            if (email.testEmail()) {
                userRepository.save(user);
                return ResponseEntity.ok("Test user registered successfully! Use OTP 123456 to verify.");
            }

            String otp = otpService.createOtp(user.getId());
            userRepository.save(user);
            emailService.sendOtp(user.getEmail(), otp);
            return ResponseEntity.ok("User registered successfully! Check your email for OTP.");
        } catch (RateLimitExceededException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage());
        } catch (DuplicateKeyException e) {
            return ResponseEntity.badRequest().body("Username or email is already taken!");
        } catch (RedisConnectionFailureException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Verification service is temporarily unavailable.");
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody VerifyRequest verifyRequest, HttpServletRequest request) {
        NormalizedEmail email = emailAddressService.normalize(verifyRequest.getEmail());
        if (email == null) {
            return ResponseEntity.badRequest().body("Valid email is required");
        }

        try {
            rateLimitService.checkVerificationLimit(deviceKey(request), email.canonicalEmail());
            User user = findUserForVerification(email);
            String otp = clean(verifyRequest.getOtp());

            if (email.testEmail() && "123456".equals(otp)) {
                user.setVerified(true);
                userRepository.save(user);
                return ResponseEntity.ok("Email verified successfully!");
            }

            if (otpService.verifyOtp(user.getId(), otp)) {
                user.setVerified(true);
                userRepository.save(user);
                otpService.clearOtp(user.getId());
                return ResponseEntity.ok("Email verified successfully!");
            }
        } catch (RateLimitExceededException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage());
        } catch (RedisConnectionFailureException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Verification service is temporarily unavailable.");
        }

        return ResponseEntity.badRequest().body("Invalid or expired OTP!");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest authRequest) {
        NormalizedEmail email = emailAddressService.normalize(authRequest.getEmail());
        if (email == null || authRequest.getPassword() == null || authRequest.getPassword().isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }

        List<User> candidates = email.testEmail()
                ? userRepository.findAllByCanonicalEmail(email.canonicalEmail())
                : userRepository.findByCanonicalEmail(email.canonicalEmail()).stream().toList();

        for (User user : candidates) {
            if (user.getPassword() != null && passwordEncoder.matches(authRequest.getPassword(), user.getPassword())) {
                if (!user.isVerified()) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Email is not verified");
                }
                String token = jwtUtil.generateToken(new org.springframework.security.core.userdetails.User(
                        user.getId(),
                        user.getPassword(),
                        new java.util.ArrayList<>()
                ));
                return ResponseEntity.ok(new AuthResponse(token));
            }
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
    }

    private User findUserForVerification(NormalizedEmail email) {
        if (email.testEmail()) {
            return userRepository.findFirstByCanonicalEmailAndVerifiedFalseOrderByCreatedAtDesc(email.canonicalEmail())
                    .orElseGet(() -> userRepository.findFirstByCanonicalEmailOrderByCreatedAtDesc(email.canonicalEmail())
                            .orElseThrow(() -> new RuntimeException("User not found")));
        }

        return userRepository.findByCanonicalEmail(email.canonicalEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String deviceKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = forwardedFor == null || forwardedFor.isBlank()
                ? request.getRemoteAddr()
                : forwardedFor.split(",", 2)[0].trim();
        String userAgent = request.getHeader("User-Agent");
        return rateLimitService.fingerprint(ip + "|" + (userAgent == null ? "" : userAgent));
    }

    @GetMapping("/hello")
    public String hello(Principal principal) {
        return "Hello, " + principal.getName() + "! Your token is valid.";
    }
}
