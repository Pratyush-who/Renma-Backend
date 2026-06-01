package com.example.renma.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

@Data
public class MobileRegisterRequest {
    private String registrationToken;
    @JsonAlias("userName")
    private String username;
    private String email;
    private String profilePic;
    private List<String> interests;
}
