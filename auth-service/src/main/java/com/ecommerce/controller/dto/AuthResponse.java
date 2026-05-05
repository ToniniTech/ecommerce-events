package com.ecommerce.controller.dto;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private String customerId;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
}
