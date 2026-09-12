package com.bd.blooddonerfinder.repository;

import com.bd.blooddonerfinder.model.enums.DocumentType;
import com.bd.blooddonerfinder.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findAllByUserId(Long userId);

    Optional<Document> findByIdAndUserId(UUID id, Long userId);

    List<Document> findAllByUserIdAndDocumentType(
            Long userId,
            DocumentType documentType);
}