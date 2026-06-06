package com.example.renma.controller.auth;

import com.example.renma.dto.auth.MobileOtpRequest;
import com.example.renma.dto.auth.MobileOtpVerifyRequest;
import com.example.renma.dto.auth.MobileRegisterRequest;
import com.example.renma.service.auth.MobileAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/mobile")
public class MobileAuthController {

    private final MobileAuthService mobileAuthService;

    public MobileAuthController(MobileAuthService mobileAuthService) {
        this.mobileAuthService = mobileAuthService;
    }

    @PostMapping("/request-otp")
    public ResponseEntity<?> requestOtp(@RequestBody MobileOtpRequest mobileOtpRequest, HttpServletRequest request) {
        return mobileAuthService.requestOtp(mobileOtpRequest, request);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody MobileOtpVerifyRequest verifyRequest, HttpServletRequest request) {
        return mobileAuthService.verifyOtp(verifyRequest, request);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody MobileRegisterRequest registerRequest, HttpServletRequest request) {
        return mobileAuthService.register(registerRequest, request);
    }
}
