package com.example.renma.service;

import com.example.renma.dto.AuthResponse;
import com.example.renma.dto.MobileAuthResponse;
import com.example.renma.dto.MobileOtpRequest;
import com.example.renma.dto.MobileOtpResponse;
import com.example.renma.dto.MobileOtpVerifyRequest;
import com.example.renma.dto.MobileRegisterRequest;
import com.example.renma.exception.RateLimitExceededException;
import com.example.renma.model.User;
import com.example.renma.repository.UserRepository;
import com.example.renma.security.JwtUtil;
import com.example.renma.service.EmailAddressService.NormalizedEmail;
import com.example.renma.service.MobileNumberService.NormalizedMobileNumber;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class MobileAuthService {

    private static final String FREE_PLAN = "free";
    private static final long LOCAL_TEST_SESSION_TTL_SECONDS = 300;
    private static final long LOCAL_TEST_REGISTRATION_TTL_SECONDS = 900;

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final EmailAddressService emailAddressService;
    private final MobileNumberService mobileNumberService;
    private final MobileOtpProvider mobileOtpProvider;
    private final MobileOtpSessionService mobileOtpSessionService;
    private final RateLimitService rateLimitService;
    private final MobileAuthProperties properties;
    private final ConcurrentMap<String, LocalTestSession> localTestOtpSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LocalTestSession> localTestRegistrationTokens = new ConcurrentHashMap<>();

    public MobileAuthService(
            JwtUtil jwtUtil,
            UserRepository userRepository,
            EmailAddressService emailAddressService,
            MobileNumberService mobileNumberService,
            MobileOtpProvider mobileOtpProvider,
            MobileOtpSessionService mobileOtpSessionService,
            RateLimitService rateLimitService,
            MobileAuthProperties properties
    ) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.emailAddressService = emailAddressService;
        this.mobileNumberService = mobileNumberService;
        this.mobileOtpProvider = mobileOtpProvider;
        this.mobileOtpSessionService = mobileOtpSessionService;
        this.rateLimitService = rateLimitService;
        this.properties = properties;
    }

    public ResponseEntity<?> requestOtp(MobileOtpRequest request, HttpServletRequest httpRequest) {
        NormalizedMobileNumber mobile = mobileNumberService.normalize(request.getMobileNumber());
        if (mobile == null) {
            return ResponseEntity.badRequest().body("Valid 10 digit mobile number is required");
        }

        boolean localTestMobile = isLocalTestMobile(mobile);
        try {
            if (localTestMobile) {
                String verificationId = createLocalTestToken(localTestOtpSessions, mobile.canonicalMobileNumber(), LOCAL_TEST_SESSION_TTL_SECONDS);
                return ResponseEntity.ok(new MobileOtpResponse(verificationId, "OTP sent successfully."));
            }

            rateLimitService.checkMobileOtpRequestLimit(deviceKey(httpRequest), mobile.canonicalMobileNumber(), false);
            String verifyKey = mobileOtpProvider.sendOtp(mobile.mobileNumber());
            String verificationId = mobileOtpSessionService.createOtpSession(mobile.canonicalMobileNumber(), verifyKey);
            return ResponseEntity.ok(new MobileOtpResponse(verificationId, "OTP sent successfully."));
        } catch (RateLimitExceededException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage());
        } catch (RedisConnectionFailureException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Mobile verification service is temporarily unavailable.");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("OTP provider is temporarily unavailable.");
        }
    }

    public ResponseEntity<?> verifyOtp(MobileOtpVerifyRequest request, HttpServletRequest httpRequest) {
        NormalizedMobileNumber mobile = mobileNumberService.normalize(request.getMobileNumber());
        if (mobile == null) {
            return ResponseEntity.badRequest().body("Valid 10 digit mobile number is required");
        }

        boolean localTestMobile = isLocalTestMobile(mobile);
        try {
            boolean verified = localTestMobile
                    ? verifyLocalTestOtp(request, mobile)
                    : verifyProviderOtp(request, mobile, httpRequest);

            if (!verified) {
                return ResponseEntity.badRequest().body("Invalid or expired OTP!");
            }

            if (!localTestMobile) {
                Optional<User> existingUser = userRepository.findByCanonicalMobileNumberAndTestAccountFalse(mobile.canonicalMobileNumber())
                        .filter(User::isVerified);
                if (existingUser.isPresent()) {
                    return ResponseEntity.ok(new MobileAuthResponse(false, null, issueToken(existingUser.get())));
                }
            }

            String registrationToken = localTestMobile
                    ? createLocalTestToken(localTestRegistrationTokens, mobile.canonicalMobileNumber(), LOCAL_TEST_REGISTRATION_TTL_SECONDS)
                    : mobileOtpSessionService.createRegistrationToken(mobile.canonicalMobileNumber());
            return ResponseEntity.ok(new MobileAuthResponse(true, registrationToken, null));
        } catch (RateLimitExceededException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage());
        } catch (RedisConnectionFailureException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Mobile verification service is temporarily unavailable.");
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("OTP provider is temporarily unavailable.");
        }
    }

    public ResponseEntity<?> register(MobileRegisterRequest request, HttpServletRequest httpRequest) {
        RegistrationMobile registrationMobile;
        try {
            registrationMobile = resolveRegistrationMobile(request.getRegistrationToken());
        } catch (RedisConnectionFailureException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Mobile registration service is temporarily unavailable.");
        }

        if (registrationMobile == null) {
            return ResponseEntity.badRequest().body("Invalid or expired registration token.");
        }

        NormalizedMobileNumber mobile = mobileNumberService.normalize(registrationMobile.canonicalMobileNumber());
        if (mobile == null) {
            return ResponseEntity.badRequest().body("Invalid or expired registration token.");
        }

        String username = clean(request.getUsername());
        NormalizedEmail email = emailAddressService.normalize(request.getEmail());
        if (username == null) {
            return ResponseEntity.badRequest().body("Username is required");
        }
        if (email == null) {
            return ResponseEntity.badRequest().body("Valid email is required");
        }

        try {
            if (!registrationMobile.localTestMobile()) {
                rateLimitService.checkRegistrationLimit(deviceKey(httpRequest), email.canonicalEmail(), email.testEmail());
                if (userRepository.findByCanonicalMobileNumberAndTestAccountFalse(mobile.canonicalMobileNumber()).isPresent()) {
                    return ResponseEntity.badRequest().body("Mobile number is already registered!");
                }
                if (userRepository.findByCanonicalEmail(email.canonicalEmail()).isPresent()) {
                    return ResponseEntity.badRequest().body("Email is already taken!");
                }
                if (userRepository.findByUsername(username).isPresent()) {
                    return ResponseEntity.badRequest().body("Username is already taken!");
                }
            }

            User user = User.builder()
                    .id(UUID.randomUUID().toString())
                    .username(registrationMobile.localTestMobile() ? uniqueUsername(username) : username)
                    .email(email.normalizedEmail())
                    .canonicalEmail(registrationMobile.localTestMobile() ? null : email.canonicalEmail())
                    .mobileNumber(mobile.mobileNumber())
                    .canonicalMobileNumber(mobile.canonicalMobileNumber())
                    .testAccount(registrationMobile.localTestMobile() || email.testEmail())
                    .profilePic(clean(request.getProfilePic()))
                    .interests(normalizeInterests(request.getInterests()))
                    .plan(FREE_PLAN)
                    .isVerified(true)
                    .createdAt(new Date())
                    .build();

            userRepository.save(user);
            clearRegistrationToken(request.getRegistrationToken(), registrationMobile.localTestMobile());
            return ResponseEntity.ok(new AuthResponse(issueToken(user)));
        } catch (RateLimitExceededException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage());
        } catch (DuplicateKeyException e) {
            return ResponseEntity.badRequest().body("Username, email, or mobile number is already taken!");
        }
    }

    private boolean verifyProviderOtp(MobileOtpVerifyRequest request, NormalizedMobileNumber mobile, HttpServletRequest httpRequest) {
        rateLimitService.checkMobileOtpVerifyLimit(deviceKey(httpRequest), mobile.canonicalMobileNumber(), false);
        Optional<String> verifyKey = mobileOtpSessionService.getVerifyKey(
                request.getVerificationId(),
                mobile.canonicalMobileNumber()
        );
        if (verifyKey.isEmpty()) {
            return false;
        }

        boolean verified = mobileOtpProvider.verifyOtp(mobile.mobileNumber(), verifyKey.get(), clean(request.getOtp()));
        if (verified) {
            mobileOtpSessionService.clearOtpSession(request.getVerificationId());
        }
        return verified;
    }

    private boolean verifyLocalTestOtp(MobileOtpVerifyRequest request, NormalizedMobileNumber mobile) {
        if (!properties.getTestOtp().equals(clean(request.getOtp()))) {
            return false;
        }

        String verificationId = clean(request.getVerificationId());
        if (verificationId == null) {
            return true;
        }

        LocalTestSession session = localTestOtpSessions.remove(verificationId);
        return session != null
                && !session.expired()
                && session.canonicalMobileNumber().equals(mobile.canonicalMobileNumber());
    }

    private RegistrationMobile resolveRegistrationMobile(String registrationToken) {
        String token = clean(registrationToken);
        if (token == null) {
            return null;
        }

        LocalTestSession localTestSession = localTestRegistrationTokens.get(token);
        if (localTestSession != null) {
            if (localTestSession.expired()) {
                localTestRegistrationTokens.remove(token);
                return null;
            }
            return new RegistrationMobile(localTestSession.canonicalMobileNumber(), true);
        }

        return mobileOtpSessionService.getRegistrationMobileNumber(token)
                .map(mobileNumber -> new RegistrationMobile(mobileNumber, false))
                .orElse(null);
    }

    private void clearRegistrationToken(String registrationToken, boolean localTestMobile) {
        String token = clean(registrationToken);
        if (token == null) {
            return;
        }

        if (localTestMobile) {
            localTestRegistrationTokens.remove(token);
        } else {
            mobileOtpSessionService.clearRegistrationToken(token);
        }
    }

    private boolean isLocalTestMobile(NormalizedMobileNumber mobile) {
        return properties.isTestBypassEnabled()
                && mobile.canonicalMobileNumber().equals(properties.getTestMobileNumber());
    }

    private String createLocalTestToken(ConcurrentMap<String, LocalTestSession> sessions, String canonicalMobileNumber, long ttlSeconds) {
        removeExpired(sessions);
        String token = UUID.randomUUID().toString();
        sessions.put(token, new LocalTestSession(canonicalMobileNumber, Instant.now().plusSeconds(ttlSeconds)));
        return token;
    }

    private void removeExpired(ConcurrentMap<String, LocalTestSession> sessions) {
        sessions.entrySet().removeIf(entry -> entry.getValue().expired());
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

    private record LocalTestSession(String canonicalMobileNumber, Instant expiresAt) {
        boolean expired() {
            return !Instant.now().isBefore(expiresAt);
        }
    }

    private record RegistrationMobile(String canonicalMobileNumber, boolean localTestMobile) {
    }
}
