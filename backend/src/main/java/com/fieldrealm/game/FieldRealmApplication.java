package com.fieldrealm.game;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FieldRealmApplication {
    public static void main(String[] args) {
        SpringApplication.run(FieldRealmApplication.class, args);
    }
}
