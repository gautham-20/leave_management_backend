package com.leavems;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Test beans. BCrypt's default cost of 10 is deliberately slow, which would
 * dominate the runtime of a suite that signs in repeatedly.
 */
@TestConfiguration
public class TestBeans {

    /**
     * Marked as the primary {@link PasswordEncoder} rather than named
     * {@code passwordEncoder}: a matching bean name would collide with the one
     * in SecurityConfig and fail the context startup outright.
     */
    @Bean
    @Primary
    public PasswordEncoder fastTestPasswordEncoder() {
        return new BCryptPasswordEncoder(4);
    }
}