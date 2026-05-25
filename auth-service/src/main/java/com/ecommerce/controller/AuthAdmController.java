package com.ecommerce.controller;

import com.ecommerce.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth/admin")
@RequiredArgsConstructor
public class AuthAdmController {

    private final AuthService authService;


    @PatchMapping("/lockUser/{customerId}")
    public ResponseEntity<Void> lockUserById(@PathVariable("customerId") String customerId) {
        authService.lockUser(customerId);
        return ResponseEntity.noContent().build();

    }

    @PatchMapping("/unlockUser/{customerId}")
    public ResponseEntity<Void> unlockUserById(@PathVariable("customerId") String customerId) {
        authService.unlockUser(customerId);
        return ResponseEntity.noContent().build();

    }
}
