package com.example.renma.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MobileAuthResponse {
    private boolean registrationRequired;
    private String registrationToken;
    private String token;
}
