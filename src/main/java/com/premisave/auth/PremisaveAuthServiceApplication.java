package com.premisave.auth;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PremisaveAuthServiceApplication {

    public static void main(String[] args) {
        // Load .env file from project root or classpath
        Dotenv dotenv = Dotenv.configure()
                              .ignoreIfMissing()  // don't fail if .env is missing
                              .load();

        // Set all .env variables as system properties so Spring can read them
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

        // Start Spring Boot
        SpringApplication.run(PremisaveAuthServiceApplication.class, args);
    }
}
