package com.onmyway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OnMyWayApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnMyWayApplication.class, args);
    }

}
