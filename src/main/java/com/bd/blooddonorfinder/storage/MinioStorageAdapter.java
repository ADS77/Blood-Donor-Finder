package com.bd.blooddonorfinder.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * MinIO (S3-compatible) adapter for {@link StoragePort}.
 * All failures are normalized to {@link StorageException} so the service
 * layer never deals with client-specific exceptions.
 */
@Component
@Slf4j
public class MinioStorageAdapter implements StoragePort {

    private final MinioClient client;

    public MinioStorageAdapter(MinioClient client) {
        this.client = client;
    }

    @Override
    public void put(String bucket, String objectKey, InputStream content,
                    long sizeBytes, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(content, sizeBytes, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Failed to upload object " + objectKey, e);
        }
    }

    @Override
    public String presignedGetUrl(String bucket, String objectKey, int ttlSeconds,
                                  String downloadFilename) {
        try {
            Map<String, String> responseHeaders = downloadFilename != null
                    ? Map.of("response-content-disposition",
                            "attachment; filename=\"" + sanitizeHeaderValue(downloadFilename) + "\"")
                    : Collections.emptyMap();
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(ttlSeconds, TimeUnit.SECONDS)
                    .extraQueryParams(responseHeaders)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Failed to presign object " + objectKey, e);
        }
    }

    /** Neutralizes characters that could break out of the quoted header value. */
    private static String sanitizeHeaderValue(String value) {
        return value.replaceAll("[\\\"\\\\\\r\\n;]", "_");
    }

    @Override
    public void delete(String bucket, String objectKey) {
        try {
            // RemoveObject is idempotent for MinIO/S3 — deleting a missing object is a no-op
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Failed to delete object " + objectKey, e);
        }
    }

    @Override
    public void ensureBucket(String bucket) {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created storage bucket: {}", bucket);
            }
        } catch (Exception e) {
            throw new StorageException("Failed to ensure bucket " + bucket, e);
        }
    }
}
