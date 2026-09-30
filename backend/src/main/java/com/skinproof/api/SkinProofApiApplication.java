package com.skinproof.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SkinProofApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkinProofApiApplication.class, args);
    }
}