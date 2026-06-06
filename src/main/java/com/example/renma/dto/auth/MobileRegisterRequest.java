package com.example.renma.dto.auth;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class MobileRegisterRequest {
    private String registrationToken;
    @JsonAlias("userName")
    private String username;
    private String displayName;
    private String email;
    private String profilePic;
}
