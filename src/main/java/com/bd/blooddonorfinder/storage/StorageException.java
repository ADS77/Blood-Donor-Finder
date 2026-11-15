package com.bd.blooddonorfinder.storage;

/** Any storage-layer failure, wrapped so services can map it to a clean 5xx. */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
