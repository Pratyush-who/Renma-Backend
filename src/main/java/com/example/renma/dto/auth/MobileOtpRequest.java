package com.example.renma.dto.auth;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class MobileOtpRequest {
    @JsonAlias({"phoneNumber", "mobile", "mobileNumber", "pno"})
    private String mobileNumber;
}
