package com.example.renma.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
@CompoundIndexes(
        {
                @CompoundIndex(
                        name = "unique_canonical_email_prod",
                        def = "{'canonicalEmail': 1}",
                        unique = true,
                        partialFilter = "{'testAccount': false, 'canonicalEmail': {'$exists': true}}"
                ),
                @CompoundIndex(
                        name = "unique_canonical_mobile_prod",
                        def = "{'canonicalMobileNumber': 1}",
                        unique = true,
                        partialFilter = "{'testAccount': false, 'canonicalMobileNumber': {'$exists': true}}"
                )
        }
)
public class User {

    @Id
    private String id;
    
    @Indexed(unique = true)
    @Field("userName")
    private String username;
    
    private String displayName;
    private String email;
    private String canonicalEmail;
    private String mobileNumber;
    private String canonicalMobileNumber;
    private String plan;
    private boolean testAccount;
    private String password;
    private String profilePic;
    private String bio;
    private boolean emailVerified;
    private boolean mobileVerified;
    
    @Builder.Default
    private List<String> savedTemplates = new ArrayList<>();
    
    @Builder.Default
    private List<String> publishedTemplates = new ArrayList<>();

    @Builder.Default
    private List<String> pastCreations = new ArrayList<>();
    private Date createdAt;
}
