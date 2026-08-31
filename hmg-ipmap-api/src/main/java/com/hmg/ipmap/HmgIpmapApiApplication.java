package com.hmg.ipmap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HmgIpmapApiApplication {

    private HmgIpmapApiApplication() {
        // Utility class - prevent instantiation
    }

    static void main(String[] args) {
        SpringApplication.run(HmgIpmapApiApplication.class, args);
    }
}
