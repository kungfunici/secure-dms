package com.niclas.securecdms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class SecureDmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecureDmsApplication.class, args);
    }
}
