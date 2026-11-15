package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.model.enums.DocumentType;
import com.bd.blooddonorfinder.payload.response.DocumentDto;
import com.bd.blooddonorfinder.payload.response.DocumentReviewDto;
import com.bd.blooddonorfinder.payload.response.DocumentVerificationResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Fine-grained document service for user uploads (profile photo, blood and
 * diagnostic reports). Storage bytes go to MinIO via {@code StoragePort};
 * Postgres metadata is the source of truth.
 */
public interface DocumentService {

    /**
     * Validates and stores a file: size gate, magic-byte sniffing (never trusts
     * the client's declared content type), per-type format whitelist.
     */
    DocumentDto upload(UUID ownerId, DocumentType docType, MultipartFile file);

    /** Owner's active documents, newest first; keyset via {@code beforeEpochMs}. */
    List<DocumentDto> listMy(UUID ownerId, Long beforeEpochMs, Integer size);

    /** Metadata for one document; only the owner or a system admin may read it. */
    DocumentDto get(UUID callerId, UUID documentId, boolean callerIsSystemAdmin);

    /** Short-lived presigned download URL; owner or system admin only. */
    String getDownloadUrl(UUID callerId, UUID documentId, boolean callerIsSystemAdmin);

    /** Soft-deletes the row and removes the object; owner or system admin only. */
    void delete(UUID callerId, UUID documentId, boolean callerIsSystemAdmin);

    /** Admin review queue: unverified documents, oldest first. */
    List<DocumentReviewDto> verificationQueue(Integer size);

    /**
     * Admin verification decision. Approving a medical document also marks
     * the owner as a verified donor; both outcomes notify the owner.
     */
    DocumentVerificationResult verify(UUID adminId, UUID documentId,
                                      com.bd.blooddonorfinder.model.enums.DocumentVerificationStatus decision,
                                      String reason);
}
