package com.worthit.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * WorthIt backend entry point.
 *
 * Minimal starter — wires up Spring Boot, security, exception handling, and a hello endpoint.
 * TODO(worthIt): add worthIt-specific configuration, domain modules, and persistence here as features land.
 */
@SpringBootApplication
public class WorthitBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorthitBackendApplication.class, args);
    }

}
