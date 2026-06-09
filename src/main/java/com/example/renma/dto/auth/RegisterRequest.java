package com.example.renma.dto.auth;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class RegisterRequest {
    @JsonAlias("userName")
    private String username;
    private String displayName;
    private String email;
    private String mobileNumber;
    private String password;
    private String profilePic;
}
