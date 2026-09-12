package com.bd.blooddonerfinder.service;

import com.bd.blooddonerfinder.model.Document;
import com.bd.blooddonerfinder.model.enums.DocumentType;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

public interface DocumentService {
	Document upload(Long userId, DocumentType documentType, MultipartFile file);

	List<Document> findAll(Long userId);

	List<Document> findAll(Long userId, DocumentType documentType);

	Document findById(Long userId, UUID documentId);

	InputStream download(Long userId, UUID documentId);

	void delete(Long userId, UUID documentId);
}
