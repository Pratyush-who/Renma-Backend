package com.example.renma.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class RegisterRequest {
    @JsonAlias("userName")
    private String username;
    private String email;
    private String password;
    private String profilePic;
}

