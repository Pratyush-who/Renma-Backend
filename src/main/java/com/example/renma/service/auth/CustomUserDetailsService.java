package com.example.renma.service.auth;

import com.example.renma.model.User;
import com.example.renma.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String subject) throws UsernameNotFoundException {
        User user = userRepository.findById(subject)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + subject));
        String password = user.getPassword() == null ? "" : user.getPassword();
        return new org.springframework.security.core.userdetails.User(user.getId(), password, new ArrayList<>());
    }
}
