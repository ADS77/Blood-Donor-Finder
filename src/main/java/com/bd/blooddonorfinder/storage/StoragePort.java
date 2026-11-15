package com.bd.blooddonorfinder.storage;

/**
 * Storage port (hexagonal). The document service depends only on this —
 * MinIO today, any S3-compatible or native storage later, without touching
 * service logic. Also makes every storage interaction unit-testable.
 */
public interface StoragePort {

    /**
     * Uploads a stream at {@code bucket/objectKey}.
     *
     * @param sizeBytes   exact stream length (known before upload)
     * @param contentType verified content type (already validated by the service)
     */
    void put(String bucket, String objectKey, java.io.InputStream content,
             long sizeBytes, String contentType);

    /**
     * Short-lived presigned GET URL so file bytes flow browser→storage directly,
     * never through the API. {@code downloadFilename} sets the browser's
     * save-as name; {@code null} renders inline.
     */
    String presignedGetUrl(String bucket, String objectKey, int ttlSeconds, String downloadFilename);

    /** Removes the object. Missing objects must NOT throw (idempotent delete). */
    void delete(String bucket, String objectKey);

    /** Creates the bucket when absent; safe to call repeatedly. */
    void ensureBucket(String bucket);
}
