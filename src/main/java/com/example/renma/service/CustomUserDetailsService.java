package com.example.renma.service;

import com.example.renma.model.User;
import com.example.renma.repository.UserRepository;
import com.example.renma.service.EmailAddressService.NormalizedEmail;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final EmailAddressService emailAddressService;

    public CustomUserDetailsService(UserRepository userRepository, EmailAddressService emailAddressService) {
        this.userRepository = userRepository;
        this.emailAddressService = emailAddressService;
    }

    @Override
    public UserDetails loadUserByUsername(String subject) throws UsernameNotFoundException {
        User user = findUser(subject);
        String password = user.getPassword() == null ? "" : user.getPassword();
        return new org.springframework.security.core.userdetails.User(subject, password, new ArrayList<>());
    }

    private User findUser(String subject) {
        return userRepository.findById(subject)
                .or(() -> {
                    NormalizedEmail email = emailAddressService.normalize(subject);
                    if (email == null || email.testEmail()) {
                        return java.util.Optional.empty();
                    }
                    return userRepository.findByCanonicalEmail(email.canonicalEmail());
                })
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + subject));
    }
}
