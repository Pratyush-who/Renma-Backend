package com.example.renma.repository;

import com.example.renma.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByCanonicalEmail(String canonicalEmail);
    Optional<User> findFirstByCanonicalEmailOrderByCreatedAtDesc(String canonicalEmail);
    Optional<User> findFirstByCanonicalEmailAndEmailVerifiedFalseOrderByCreatedAtDesc(String canonicalEmail);
    List<User> findAllByCanonicalEmail(String canonicalEmail);
    Optional<User> findByCanonicalMobileNumberAndTestAccountFalse(String canonicalMobileNumber);
    Optional<User> findFirstByCanonicalMobileNumberOrderByCreatedAtDesc(String canonicalMobileNumber);
    Optional<User> findByUsername(String username);
    Optional<User> findByCanonicalMobileNumber(String canonicalMobileNumber);
}
