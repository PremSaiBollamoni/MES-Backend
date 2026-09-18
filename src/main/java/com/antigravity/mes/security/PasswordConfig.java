package com.antigravity.mes.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Provides a BCrypt password encoder without pulling in spring-boot-starter-security
 * (which would auto-lock every endpoint behind a login form). Only the crypto primitive
 * is needed here; the app's own AuthController handles the login flow.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
