package com.bd.blooddonerfinder.service;

import com.bd.blooddonerfinder.exception.DocumentNotFoundException;
import com.bd.blooddonerfinder.exception.UserNotFoundException;
import com.bd.blooddonerfinder.model.Document;
import com.bd.blooddonerfinder.model.User;
import com.bd.blooddonerfinder.model.enums.DocumentType;
import com.bd.blooddonerfinder.repository.DocumentRepository;
import com.bd.blooddonerfinder.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {
    private static final Map<String, String> SUPPORTED_FILE_TYPES = Map.of(
            "pdf", "application/pdf",
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final Set<String> GENERIC_CONTENT_TYPES = Set.of(
            "application/octet-stream",
            "application/binary");

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final MinioStorageService storageService;

    public DocumentServiceImpl(DocumentRepository documentRepository,
            UserRepository userRepository,
            MinioStorageService storageService) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    @Override
    @Transactional
    public Document upload(Long userId, DocumentType documentType, MultipartFile file) {
        if (userId == null || documentType == null || file == null || file.isEmpty()) {
            throw new IllegalArgumentException("userId, documentType and a non-empty file are required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        String fileName = Paths.get(StringUtils.cleanPath(file.getOriginalFilename() == null
                ? "document"
                : file.getOriginalFilename())).getFileName().toString();
        String contentType = file.getContentType() == null
                ? "application/octet-stream"
                : file.getContentType().toLowerCase(Locale.ROOT);
        validateFileType(fileName, contentType);
        String objectKey = userId + "/" + UUID.randomUUID() + "-" + fileName;

        try (InputStream inputStream = file.getInputStream()) {
            storageService.upload(objectKey, inputStream, file.getSize(), contentType);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read uploaded file", exception);
        }

        Document document = new Document();
        document.setUser(user);
        document.setDocumentType(documentType);
        document.setOriginalFileName(fileName);
        document.setObjectKey(objectKey);
        document.setContentType(contentType);
        document.setFileSize(file.getSize());
        return documentRepository.save(document);
    }

    private void validateFileType(String fileName, String contentType) {
        int extensionSeparator = fileName.lastIndexOf('.');
        String extension = extensionSeparator >= 0
                ? fileName.substring(extensionSeparator + 1).toLowerCase(Locale.ROOT)
                : "";
        String expectedContentType = SUPPORTED_FILE_TYPES.get(extension);

        if (expectedContentType == null) {
            throw new IllegalArgumentException(
                    "Unsupported file type. Supported types are PDF, PNG, JPG, DOC and DOCX");
        }

        if (!GENERIC_CONTENT_TYPES.contains(contentType) && !expectedContentType.equals(contentType)) {
            throw new IllegalArgumentException("File extension does not match the MIME type");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> findAll(Long userId) {
        return findAll(userId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> findAll(Long userId, DocumentType documentType) {
        if (documentType != null) {
            return documentRepository.findAllByUserIdAndDocumentType(
                    userId,
                    documentType);
        }

        return documentRepository.findAllByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Document findById(Long userId, UUID documentId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        return document;
    }

    @Override
    @Transactional(readOnly = true)
    public InputStream download(Long userId, UUID documentId) {
        return storageService.download(findById(userId, documentId).getObjectKey());
    }

    @Override
    @Transactional
    public void delete(Long userId, UUID documentId) {
        Document document = findById(userId, documentId);
        storageService.delete(document.getObjectKey());
        documentRepository.delete(document);
    }
}