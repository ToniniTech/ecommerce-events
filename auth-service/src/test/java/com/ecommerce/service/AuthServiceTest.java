package com.ecommerce.service;

import com.ecommerce.controller.dto.AuthResponse;
import com.ecommerce.controller.dto.LoginRequest;
import com.ecommerce.controller.dto.RegisterRequest;
import com.ecommerce.domain.RefreshToken;
import com.ecommerce.domain.RefreshTokenRepository;
import com.ecommerce.domain.User;
import com.ecommerce.domain.UserRepository;
import com.ecommerce.exception.EmailAlreadyExistsException;
import com.ecommerce.exception.InvalidCredentialsException;
import com.ecommerce.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService unit tests-")
public class AuthServiceTest {

    @InjectMocks private AuthService authService;

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtService jwtService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserSecurityService userSecurityService;

    // ── Test data ──────────────────────────────────────────────────────────────
    private RegisterRequest validRequest;
    private LoginRequest loginRequest;
    private User user;
    @BeforeEach
    void setUp(){

        validRequest = RegisterRequest.builder()
                        .firstName("Anthony")
                        .lastName("Viveros")
                        .email("anthonymvf09@gmail.com")
                        .password("12345678")
                        .build();

        loginRequest = LoginRequest.builder()
                .email("anthonymvf09@gmail.com")
                .password("12345678")
                .build();


        user = User.builder()
                .id(1L)
                .customerId("cust-test123")
                .email("anthonymvf09@gmail.com")
                .password("encoded-password")
                .firstName("Anthony")
                .lastName("Viveros")
                .build();
    }




    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    void shouldRegisterUserSuccessfully(){
        //Arrange
        when(jwtService.generateAccessToken(any())).thenReturn("fake-access-token");
        when(refreshTokenRepository.save(any())).thenReturn(RefreshToken.builder()
                .token("fake-refresh-token")
                .build());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.existsByEmail(any())).thenReturn(false);

        //Act
        AuthResponse response = authService.register(validRequest);

        //Assert
        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("anthonymvf09@gmail.com");

        //Verify
        verify(userRepository, times(1)).save(any());
        verify(jwtService, times(1)).generateAccessToken(any());
        verify(refreshTokenRepository, times(1)).save(any());
    }

    @Test
    void shouldNotRegisterDuplicatedUser(){
        //Arrange
        when(userRepository.existsByEmail(any())).thenReturn(true);

        //Act & Assert
        assertThatThrownBy(()-> authService.register(validRequest))
                .isInstanceOf(EmailAlreadyExistsException.class)
                        .hasMessage("Email already registered: " + validRequest.getEmail());


        //Verify
        verify(userRepository).existsByEmail(validRequest.getEmail());
    }

    @Test
    void shouldLoginUserSuccesfully(){
        // Arrange
        when(userRepository.findByEmail(loginRequest.getEmail()))
                .thenReturn(Optional.of(user));

        when(authenticationManager.authenticate(any()))
                .thenReturn(mock(Authentication.class));

        when(jwtService.generateAccessToken(user))
                .thenReturn("fake-access-token");

        when(refreshTokenRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        //Act
        AuthResponse response = authService.login(loginRequest);

        //Assert
        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo(user.getEmail());
        assertThat(response.getAccessToken()).isEqualTo("fake-access-token");

        //Verify
        verify(userRepository).findByEmail(loginRequest.getEmail());
        verify(authenticationManager).authenticate(any());
        verify(refreshTokenRepository).revokeAllByUserId(user.getId());
        verify(jwtService).generateAccessToken(user);
        verify(refreshTokenRepository).save(any());

    }

    @Test
    void shouldRegisterFailedLogins(){
        //Arrange
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        //Act & Assert
        assertThatThrownBy(()-> authService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Invalid email or password");

        //Verify
        verify(userRepository).findByEmail(loginRequest.getEmail());
        verify(authenticationManager).authenticate(any());
        verify(userSecurityService).registerFailedLogins(user.getEmail());

    }
}
