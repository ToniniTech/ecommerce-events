package com.ecommerce.controller.dto;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TokenValidationResponse {
    private boolean valid;
    private String customerId;
    private String email;
    private String role;
    private String expiresAt;

    public static TokenValidationResponse invalid() {
        return TokenValidationResponse.builder().valid(false).build();
    }
}
