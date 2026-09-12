package com.bd.blooddonerfinder.payload.document;

import com.bd.blooddonerfinder.model.Document;
import com.bd.blooddonerfinder.model.enums.DocumentType;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentResponse(
                UUID id,
                DocumentType documentType,
                String originalFileName,
                String contentType,
                Long fileSize,
                @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime createdAt) {
        public static DocumentResponse from(Document document) {
                return new DocumentResponse(
                                document.getId(),
                                document.getDocumentType(),
                                document.getOriginalFileName(),
                                document.getContentType(),
                                document.getFileSize(),
                                document.getCreatedAt());
        }
}