package com.bd.blooddonorfinder.storage;

import com.bd.blooddonorfinder.model.enums.DocumentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Ensures every bucket referenced by {@link DocumentType} exists at startup.
 * Best-effort: a storage outage must NOT prevent the app from booting —
 * the same check happens on-demand from the ops bootstrap (docker init
 * container) and can be re-run by restarting.
 */
@Component
@Slf4j
public class StorageBucketInitializer {

    private final StoragePort storage;
    private final boolean enabled;

    public StorageBucketInitializer(StoragePort storage,
                                    @Value("${app.storage.ensure-buckets:true}") boolean enabled) {
        this.storage = storage;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureBuckets() {
        if (!enabled) {
            return;
        }
        Set<String> buckets = new LinkedHashSet<>();
        for (DocumentType type : DocumentType.values()) {
            buckets.add(type.getBucket());
        }
        for (String bucket : buckets) {
            try {
                storage.ensureBucket(bucket);
            } catch (Exception e) {
                log.warn("Could not ensure bucket '{}' — storage may be down (uploads will fail until it recovers): {}",
                        bucket, e.getMessage());
            }
        }
    }
}
