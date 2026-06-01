package com.example.renma.controller;

import com.example.renma.dto.AuthRequest;
import com.example.renma.dto.AuthResponse;
import com.example.renma.dto.ChangePasswordRequest;
import com.example.renma.dto.ForgotPasswordRequest;
import com.example.renma.dto.RegisterRequest;
import com.example.renma.dto.ResetPasswordRequest;
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
import java.util.Optional;
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
    private static final String VERIFICATION_EMAIL_RESPONSE = "If an account exists, we've sent a verification email.";
    private static final String PASSWORD_RESET_EMAIL_RESPONSE = "If an account exists, we've sent a password reset email.";
    private static final String FREE_PLAN = "free";

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
        if (!isValidPassword(password)) {
            return ResponseEntity.badRequest().body("Password must be at least 8 characters long");
        }

        try {
            rateLimitService.checkRegistrationLimit(deviceKey(request), email.canonicalEmail(), email.testEmail());

            String finalUsername = email.testEmail() ? uniqueUsername(username) : username;
            if (!email.testEmail() && userRepository.findByUsername(finalUsername).isPresent()) {
                return ResponseEntity.badRequest().body("Username is already taken!");
            }
            if (!email.testEmail() && userRepository.findByCanonicalEmail(email.canonicalEmail()).isPresent()) {
                return ResponseEntity.badRequest().body("Email is already taken!");
            }

            User user = User.builder()
                    .id(UUID.randomUUID().toString())
                    .username(finalUsername)
                    .email(email.normalizedEmail())
                    .canonicalEmail(email.canonicalEmail())
                    .testAccount(email.testEmail())
                    .password(passwordEncoder.encode(password))
                    .profilePic(clean(registerRequest.getProfilePic()))
                    .interests(normalizeInterests(registerRequest.getInterests()))
                    .plan(FREE_PLAN)
                    .isVerified(false)
                    .createdAt(new Date())
                    .build();

            if (email.testEmail()) {
                userRepository.save(user);
                return ResponseEntity.ok("Test user registered successfully! Use OTP 777777 to verify.");
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
            Optional<User> optionalUser = findUserForVerification(email);
            if (optionalUser.isEmpty()) {
                return ResponseEntity.badRequest().body("Invalid or expired OTP!");
            }
            User user = optionalUser.get();
            String otp = clean(verifyRequest.getOtp());

            if (email.testEmail() && "777777".equals(otp)) {
                user.setVerified(true);
                userRepository.save(user);
                return ResponseEntity.ok("Email verified successfully!");
            }

            if (otpService.verifyOtp(user.getId(), otp)) {
                user.setVerified(true);
                userRepository.save(user);
                return ResponseEntity.ok("Email verified successfully!");
            }
        } catch (RateLimitExceededException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage());
        } catch (RedisConnectionFailureException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Verification service is temporarily unavailable.");
        }

        return ResponseEntity.badRequest().body("Invalid or expired OTP!");
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestBody VerifyRequest verifyRequest, HttpServletRequest request) {
        NormalizedEmail email = emailAddressService.normalize(verifyRequest.getEmail());
        if (email == null) {
            return ResponseEntity.badRequest().body("Valid email is required");
        }

        try {
            rateLimitService.checkVerificationLimit(deviceKey(request), email.canonicalEmail());
            userRepository.findByCanonicalEmail(email.canonicalEmail())
                    .filter(user -> !user.isVerified())
                    .filter(user -> !user.isTestAccount())
                    .ifPresent(user -> {
                        String otp = otpService.createOtp(user.getId());
                        emailService.sendOtp(user.getEmail(), otp);
                    });
            return ResponseEntity.ok(VERIFICATION_EMAIL_RESPONSE);
        } catch (RateLimitExceededException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage());
        } catch (RedisConnectionFailureException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Verification service is temporarily unavailable.");
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest forgotPasswordRequest, HttpServletRequest request) {
        NormalizedEmail email = emailAddressService.normalize(forgotPasswordRequest.getEmail());
        if (email == null) {
            return ResponseEntity.badRequest().body("Valid email is required");
        }

        try {
            rateLimitService.checkVerificationLimit(deviceKey(request), email.canonicalEmail());
            userRepository.findByCanonicalEmail(email.canonicalEmail())
                    .filter(User::isVerified)
                    .filter(user -> !user.isTestAccount())
                    .ifPresent(user -> {
                        String otp = otpService.createPasswordResetOtp(user.getId());
                        emailService.sendOtp(user.getEmail(), otp);
                    });
            return ResponseEntity.ok(PASSWORD_RESET_EMAIL_RESPONSE);
        } catch (RateLimitExceededException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage());
        } catch (RedisConnectionFailureException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Password reset service is temporarily unavailable.");
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest resetPasswordRequest, HttpServletRequest request) {
        NormalizedEmail email = emailAddressService.normalize(resetPasswordRequest.getEmail());
        if (email == null) {
            return ResponseEntity.badRequest().body("Valid email is required");
        }
        if (!isValidPassword(resetPasswordRequest.getNewPassword())) {
            return ResponseEntity.badRequest().body("Password must be at least 8 characters long");
        }
        if (!resetPasswordRequest.getNewPassword().equals(resetPasswordRequest.getConfirmPassword())) {
            return ResponseEntity.badRequest().body("Passwords do not match");
        }

        try {
            rateLimitService.checkVerificationLimit(deviceKey(request), email.canonicalEmail());
            Optional<User> optionalUser = userRepository.findByCanonicalEmail(email.canonicalEmail())
                    .filter(User::isVerified)
                    .filter(user -> !user.isTestAccount());
            if (optionalUser.isEmpty()) {
                return ResponseEntity.badRequest().body("Invalid or expired OTP!");
            }

            User user = optionalUser.get();
            if (!otpService.verifyPasswordResetOtp(user.getId(), clean(resetPasswordRequest.getOtp()))) {
                return ResponseEntity.badRequest().body("Invalid or expired OTP!");
            }

            user.setPassword(passwordEncoder.encode(resetPasswordRequest.getNewPassword()));
            userRepository.save(user);
            return ResponseEntity.ok("Password reset successfully.");
        } catch (RateLimitExceededException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage());
        } catch (RedisConnectionFailureException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Password reset service is temporarily unavailable.");
        }
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
                return ResponseEntity.ok(new AuthResponse(issueToken(user)));
            }
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest changePasswordRequest, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required");
        }
        if (changePasswordRequest.getOldPassword() == null || changePasswordRequest.getOldPassword().isBlank()) {
            return ResponseEntity.badRequest().body("Old password is required");
        }
        if (!isValidPassword(changePasswordRequest.getNewPassword())) {
            return ResponseEntity.badRequest().body("Password must be at least 8 characters long");
        }
        if (!changePasswordRequest.getNewPassword().equals(changePasswordRequest.getConfirmPassword())) {
            return ResponseEntity.badRequest().body("Passwords do not match");
        }

        User user = userRepository.findById(principal.getName())
                .orElse(null);
        if (user == null || user.getPassword() == null || !passwordEncoder.matches(changePasswordRequest.getOldPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }

        user.setPassword(passwordEncoder.encode(changePasswordRequest.getNewPassword()));
        userRepository.save(user);
        return ResponseEntity.ok("Password changed successfully.");
    }

    private Optional<User> findUserForVerification(NormalizedEmail email) {
        if (email.testEmail()) {
            return userRepository.findFirstByCanonicalEmailAndVerifiedFalseOrderByCreatedAtDesc(email.canonicalEmail())
                    .or(() -> userRepository.findFirstByCanonicalEmailOrderByCreatedAtDesc(email.canonicalEmail()));
        }

        return userRepository.findByCanonicalEmail(email.canonicalEmail());
    }

    private String issueToken(User user) {
        return jwtUtil.generateToken(new org.springframework.security.core.userdetails.User(
                user.getId(),
                user.getPassword() == null ? "" : user.getPassword(),
                new java.util.ArrayList<>()
        ));
    }

    private String uniqueUsername(String preferredUsername) {
        String baseUsername = clean(preferredUsername);
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

    private boolean isValidPassword(String password) {
        return password != null && password.length() >= 8;
    }

    private List<String> normalizeInterests(List<String> interests) {
        if (interests == null) {
            return List.of();
        }

        return interests.stream()
                .map(this::clean)
                .filter(interest -> interest != null && interest.length() <= 40)
                .distinct()
                .limit(20)
                .toList();
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
