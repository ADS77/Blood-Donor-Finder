package com.bd.blooddonorfinder.config.storage_config;

import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO (S3-compatible) client. Bean creation is lazy-safe: no connection is
 * opened here, so the app still boots when MinIO is down (callers fail then).
 */
@Configuration
@Slf4j
public class MinioConfig {

    @Bean
    public MinioClient minioClient(
            @Value("${app.storage.minio.endpoint:http://127.0.0.1:9000}") String endpoint,
            @Value("${app.storage.minio.access-key:sondhan-admin}") String accessKey,
            @Value("${app.storage.minio.secret-key:sondhan-secret}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
