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
    // Two variants instead of a nullable cursor parameter: PostgreSQL cannot
    // infer the parameter type inside "(:before IS NULL OR d.createdAt < :before)"
    // when the value is null ("could not determine data type of parameter").
    @Query("""
           SELECT d FROM Document d
           WHERE d.owner.id = :ownerId
             AND d.status = com.bd.blooddonorfinder.model.enums.DocumentStatus.ACTIVE
           ORDER BY d.createdAt DESC
           """)
    List<Document> findActiveFeed(@Param("ownerId") UUID ownerId,
                                  Pageable pageable);

    @Query("""
           SELECT d FROM Document d
           WHERE d.owner.id = :ownerId
             AND d.status = com.bd.blooddonorfinder.model.enums.DocumentStatus.ACTIVE
             AND d.createdAt < :before
           ORDER BY d.createdAt DESC
           """)
    List<Document> findActiveFeedBefore(@Param("ownerId") UUID ownerId,
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
