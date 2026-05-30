package com.example.renma.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    @Id
    private String id;
    @Indexed(unique = true)
    @Field("userName")
    private String username;
    private String email;
    @Indexed(unique = true)
    private String canonicalEmail;
    private boolean testAccount;
    private String password;
    private String profilePic;
    private List<String> interests;
    private boolean isVerified;
    private Date createdAt;
}
