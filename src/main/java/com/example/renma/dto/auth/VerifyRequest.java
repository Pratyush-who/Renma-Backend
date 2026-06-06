package com.example.renma.dto.auth;

import lombok.Data;

@Data
public class VerifyRequest {
    private String email;
    private String otp;
}

