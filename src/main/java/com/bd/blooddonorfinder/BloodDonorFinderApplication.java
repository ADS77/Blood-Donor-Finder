package com.bd.blooddonorfinder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.scheduling.annotation.EnableAsync;


@SpringBootApplication
@PropertySource({"classpath:application.properties"})
@EnableAsync
@EnableConfigurationProperties
@EnableElasticsearchRepositories(basePackages = "com.bd.blooddonorfinder.repository.es")
public class BloodDonorFinderApplication {

    public static void main(String[] args) {
        SpringApplication.run(BloodDonorFinderApplication.class, args);
    }

}
