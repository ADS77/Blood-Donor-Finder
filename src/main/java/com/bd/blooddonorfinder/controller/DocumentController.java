package com.bd.blooddonorfinder.controller;

import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.model.enums.DocumentType;
import com.bd.blooddonorfinder.payload.response.DocumentDto;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.security.UserPrincipal;
import com.bd.blooddonorfinder.service.DocumentService;
import com.bd.blooddonorfinder.utils.Utils;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fine-grained document service: profile photos, blood and diagnostic reports
 * (jpg / png / webp / pdf / docx). Uploads are validated server-side
 * (magic bytes, size, per-type whitelist); downloads use short-lived
 * presigned URLs so file bytes never pass through this API.
 */
@RestController
@RequestMapping("/api/documents")
@Slf4j
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /** Upload: multipart form with {@code file} and {@code docType}. */
    @PreAuthorize("@authz.isAuthenticated(authentication)")
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<RestApiResponse<DocumentDto>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("docType") DocumentType docType,
            Authentication authentication) {
        UUID callerId = requireCaller(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                Utils.buildSuccessRestResponse(HttpStatus.CREATED, "uploaded",
                        documentService.upload(callerId, docType, file)));
    }

    /** Owner's documents, newest first. Keyset pagination via {@code before}. */
    @PreAuthorize("@authz.isAuthenticated(authentication)")
    @GetMapping
    public ResponseEntity<RestApiResponse<List<DocumentDto>>> listMine(
            Authentication authentication,
            @RequestParam(value = "before", required = false)
            @Min(value = 0, message = "before must be a positive epoch-millis") Long before,
            @RequestParam(value = "size", required = false) @Min(1) @Max(100) Integer size) {
        UUID callerId = requireCaller(authentication);
        return ResponseEntity.ok(Utils.buildSuccessRestResponse(HttpStatus.OK,
                documentService.listMy(callerId, before, size)));
    }

    @PreAuthorize("@authz.isAuthenticated(authentication)")
    @GetMapping("/{id}")
    public ResponseEntity<RestApiResponse<DocumentDto>> get(
            @PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(Utils.buildSuccessRestResponse(HttpStatus.OK, documentService.get(
                requireCaller(authentication), id, isSystemAdmin(authentication))));
    }

    /** Short-lived presigned download URL (bytes flow browser → storage directly). */
    @PreAuthorize("@authz.isAuthenticated(authentication)")
    @GetMapping("/{id}/download-url")
    public ResponseEntity<RestApiResponse<Map<String, String>>> downloadUrl(
            @PathVariable UUID id, Authentication authentication) {
        String url = documentService.getDownloadUrl(
                requireCaller(authentication), id, isSystemAdmin(authentication));
        return ResponseEntity.ok(Utils.buildSuccessRestResponse(HttpStatus.OK,
                "URL expires shortly — fetch again when needed", Map.of("url", url)));
    }

    @PreAuthorize("@authz.isAuthenticated(authentication)")
    @DeleteMapping("/{id}")
    public ResponseEntity<RestApiResponse<String>> delete(
            @PathVariable UUID id, Authentication authentication) {
        documentService.delete(requireCaller(authentication), id, isSystemAdmin(authentication));
        return ResponseEntity.ok(Utils.buildSuccessRestResponse(HttpStatus.OK, "deleted", (String) null));
    }

    private static UUID requireCaller(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getId();
        }
        throw new com.bd.blooddonorfinder.exception.AuthException(
                ErrorCode.UNAUTHORIZED, "Authentication required");
    }

    private static boolean isSystemAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SYSTEM_ADMIN".equals(a.getAuthority()));
    }
}
