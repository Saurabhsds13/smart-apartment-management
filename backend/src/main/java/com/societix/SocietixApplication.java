package com.societix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Societix modular-monolith backend.
 *
 * <p>All feature modules live under {@code com.societix.<module>}, so component scanning
 * from this root package discovers every module's {@code api} and {@code internal} packages.
 */
@SpringBootApplication
public class SocietixApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocietixApplication.class, args);
    }
}
