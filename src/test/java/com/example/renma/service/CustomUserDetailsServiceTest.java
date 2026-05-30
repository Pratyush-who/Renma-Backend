package com.example.renma.service;

import com.example.renma.model.User;
import com.example.renma.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void loadsJwtSubjectByUserId() {
        User user = User.builder()
                .id("user-id-1")
                .password("encoded-password")
                .build();
        when(userRepository.findById("user-id-1")).thenReturn(Optional.of(user));

        CustomUserDetailsService service = new CustomUserDetailsService(userRepository);

        assertThat(service.loadUserByUsername("user-id-1").getUsername()).isEqualTo("user-id-1");
        verify(userRepository).findById("user-id-1");
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void doesNotCanonicalizeEmailAliasesWhenLoadingJwtSubjects() {
        when(userRepository.findById("pratyush.dev+tech@gmail.com")).thenReturn(Optional.empty());

        CustomUserDetailsService service = new CustomUserDetailsService(userRepository);

        assertThatThrownBy(() -> service.loadUserByUsername("pratyush.dev+tech@gmail.com"))
                .isInstanceOf(UsernameNotFoundException.class);
        verify(userRepository).findById("pratyush.dev+tech@gmail.com");
        verifyNoMoreInteractions(userRepository);
    }
}
