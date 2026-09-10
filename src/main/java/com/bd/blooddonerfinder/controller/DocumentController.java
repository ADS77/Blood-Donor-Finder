package com.bd.blooddonerfinder.controller;

import com.bd.blooddonerfinder.model.Document;
import com.bd.blooddonerfinder.model.enums.DocumentType;
import com.bd.blooddonerfinder.payload.document.DocumentResponse;
import com.bd.blooddonerfinder.service.DocumentService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") DocumentType documentType,
            @RequestParam("userId") Long userId) {
        log.info("Received request to upload document for userId: {}, documentType: {}, fileName: {}", userId,
                documentType, file.getOriginalFilename());
        return ResponseEntity.ok(DocumentResponse.from(
                documentService.upload(userId, documentType, file)));
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> getDocuments(
            @RequestParam("userId") Long userId,
            @RequestParam(value = "documentType", required = false) DocumentType documentType) {
        return ResponseEntity.ok(
                documentService.findAll(userId, documentType)
                        .stream()
                        .map(DocumentResponse::from)
                        .collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocument(
            @PathVariable UUID id,
            @RequestParam("userId") Long userId) {

        Document document = documentService.findById(userId, id);

        return ResponseEntity.ok(DocumentResponse.from(document));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(
            @PathVariable UUID id,
            @RequestParam("userId") Long userId) {
        Document document = documentService.findById(userId, id);
        InputStream inputStream = documentService.download(userId, id);
        MediaType mediaType = MediaType.parseMediaType(document.getContentType());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentLength(document.getFileSize());
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(document.getOriginalFileName())
                .build());
        return ResponseEntity.ok().headers(headers).body(new InputStreamResource(inputStream));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestParam("userId") Long userId) {
        documentService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}