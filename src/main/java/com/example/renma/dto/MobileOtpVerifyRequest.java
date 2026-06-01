package com.example.renma.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class MobileOtpVerifyRequest {
    @JsonAlias({"phoneNumber", "mobile", "mobileNumber", "pno"})
    private String mobileNumber;
    private String verificationId;
    private String otp;
}
