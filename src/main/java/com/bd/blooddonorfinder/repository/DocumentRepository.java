package com.bd.blooddonorfinder.repository;

import com.bd.blooddonorfinder.model.document.Document;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /** Owner's document list, newest first (keyset: everything older than the cursor). */
    @Query("""
           SELECT d FROM Document d
           WHERE d.owner.id = :ownerId
             AND d.status = com.bd.blooddonorfinder.model.enums.DocumentStatus.ACTIVE
             AND (:before IS NULL OR d.createdAt < :before)
           ORDER BY d.createdAt DESC
           """)
    List<Document> findActiveFeed(@Param("ownerId") UUID ownerId,
                                  @Param("before") LocalDateTime before,
                                  Pageable pageable);

    Optional<Document> findByIdAndOwnerIdAndStatus(UUID id, UUID ownerId,
                                                   com.bd.blooddonorfinder.model.enums.DocumentStatus status);

    /** Admin review queue: unverified, still-active documents, oldest first. */
    @Query("""
           SELECT d FROM Document d
           WHERE d.status = com.bd.blooddonorfinder.model.enums.DocumentStatus.ACTIVE
             AND d.verificationStatus = com.bd.blooddonorfinder.model.enums.DocumentVerificationStatus.UNVERIFIED
           ORDER BY d.createdAt ASC
           """)
    List<Document> findVerificationQueue(Pageable pageable);
}
