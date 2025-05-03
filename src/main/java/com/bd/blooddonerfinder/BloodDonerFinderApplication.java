package com.bd.blooddonerfinder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;


@SpringBootApplication
@EnableAsync
public class BloodDonerFinderApplication {

    public static void main(String[] args) {
        SpringApplication.run(BloodDonerFinderApplication.class, args);
    }

}
