package com.bd.blooddonorfinder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties
@ConfigurationPropertiesScan
@EnableElasticsearchRepositories(basePackages = "com.bd.blooddonorfinder.repository.es")
@EnableScheduling
public class BloodDonorFinderApplication {

    public static void main(String[] args) {
        SpringApplication.run(BloodDonorFinderApplication.class, args);
    }

}
