package com.example.renma.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MobileOtpResponse {
    private String verificationId;
    private String message;
}
