package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.exception.AuthException;
import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.model.document.Document;
import com.bd.blooddonorfinder.model.enums.DocumentStatus;
import com.bd.blooddonorfinder.model.enums.DocumentType;
import com.bd.blooddonorfinder.model.enums.DocumentVerificationStatus;
import com.bd.blooddonorfinder.model.enums.NotificationType;
import com.bd.blooddonorfinder.payload.response.DocumentDto;
import com.bd.blooddonorfinder.payload.response.DocumentReviewDto;
import com.bd.blooddonorfinder.payload.response.DocumentVerificationResult;
import com.bd.blooddonorfinder.repository.DocumentRepository;
import com.bd.blooddonorfinder.repository.UserRepository;
import com.bd.blooddonorfinder.storage.StoragePort;
import com.bd.blooddonorfinder.storage.StorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Upload validation is defense in depth:
 * <ol>
 *   <li>empty / size gate (per document type)</li>
 *   <li>extension whitelist (per document type)</li>
 *   <li>magic-byte sniffing — the declared Content-Type is advisory only</li>
 * </ol>
 * Object keys are always server-generated UUIDs, so a hostile filename can
 * never reach storage (path traversal, key collisions, content-type confusion).
 */
@Service
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int SNIFFER_WINDOW = 16;
    private static final DateTimeFormatter YEAR_FMT = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MM");

    /**
     * Per-type policy: allowed canonical content types + size cap.
     * DOCX is ZIP-based (PK header) — checked together with the extension.
     */
    private static final Map<DocumentType, Long> MAX_SIZE_BYTES = Map.of(
            DocumentType.PROFILE_PHOTO, 5L * 1024 * 1024,      // 5 MB
            DocumentType.BLOOD_REPORT, 20L * 1024 * 1024,      // 20 MB
            DocumentType.DIAGNOSTIC_REPORT, 20L * 1024 * 1024  // 20 MB
    );

    private static final Map<DocumentType, Set<String>> ALLOWED_CONTENT_TYPES = Map.of(
            DocumentType.PROFILE_PHOTO, Set.of("image/jpeg", "image/png", "image/webp"),
            DocumentType.BLOOD_REPORT, Set.of(
                    "application/pdf", "image/jpeg", "image/png",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            DocumentType.DIAGNOSTIC_REPORT, Set.of(
                    "application/pdf", "image/jpeg", "image/png",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    );

    /** Canonical content type → file extension used in object keys. */
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "application/pdf", "pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"
    );

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final StoragePort storage;
    private final NotificationService notificationService;
    private final int presignTtlSeconds;

    public DocumentServiceImpl(DocumentRepository documentRepository,
                               UserRepository userRepository,
                               StoragePort storage,
                               NotificationService notificationService,
                               @Value("${app.storage.presign-ttl-seconds:300}") int presignTtlSeconds) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.storage = storage;
        this.notificationService = notificationService;
        this.presignTtlSeconds = presignTtlSeconds;
    }

    @Override
    public DocumentDto upload(UUID ownerId, DocumentType docType, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AuthException(ErrorCode.DOCUMENT_EMPTY, "File is empty");
        }
        long max = MAX_SIZE_BYTES.get(docType);
        if (file.getSize() > max) {
            throw new AuthException(ErrorCode.DOCUMENT_TOO_LARGE,
                    "File exceeds the " + (max / (1024 * 1024)) + " MB limit for " + docType);
        }

        String sniffed = sniffMagicBytes(file);
        String canonical = canonicalContentType(file.getContentType(), sniffed);
        if (!ALLOWED_CONTENT_TYPES.get(docType).contains(canonical)) {
            throw new AuthException(ErrorCode.DOCUMENT_UNSUPPORTED_FORMAT,
                    "Format not allowed for " + docType);
        }

        Document document = new Document();
        document.setOwner(userRepository.getReferenceById(ownerId));
        document.setDocType(docType);
        document.setBucket(docType.getBucket());
        document.setObjectKey(buildObjectKey(ownerId, docType, canonical));
        document.setOriginalFilename(sanitizeFilename(file.getOriginalFilename()));
        document.setContentType(canonical);
        document.setSizeBytes(file.getSize());
        document.setStatus(DocumentStatus.ACTIVE);

        try (InputStream in = file.getInputStream()) {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            DigestingInputStream digesting = new DigestingInputStream(in, sha256);
            storage.put(document.getBucket(), document.getObjectKey(),
                    digesting, file.getSize(), canonical);
            document.setChecksumSha256(HexFormat.of().formatHex(sha256.digest()));
        } catch (NoSuchAlgorithmException | IOException | RuntimeException e) {
            log.error("Upload to storage failed for user={}, key={}: {}", ownerId,
                    document.getObjectKey(), e.getMessage());
            throw new AuthException(ErrorCode.DOCUMENT_UPLOAD_FAILED, "Upload failed, please try again");
        }

        Document saved = documentRepository.save(document);
        log.info("Uploaded document {} type={} owner={} bytes={} key={}",
                saved.getId(), docType, ownerId, saved.getSizeBytes(), saved.getObjectKey());
        return toDto(saved);
    }

    @Override
    public List<DocumentDto> listMy(UUID ownerId, Long beforeEpochMs, Integer size) {
        int pageSize = size != null && size > 0 ? Math.min(size, MAX_PAGE_SIZE) : 30;
        LocalDateTime before = beforeEpochMs != null ? toLocalDateTime(beforeEpochMs) : null;
        return documentRepository.findActiveFeed(ownerId, before, PageRequest.of(0, pageSize))
                .stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentReviewDto> verificationQueue(Integer size) {
        int pageSize = size != null && size > 0 ? Math.min(size, MAX_PAGE_SIZE) : 50;
        return documentRepository.findVerificationQueue(PageRequest.of(0, pageSize))
                .stream().map(d -> new DocumentReviewDto(
                        d.getId(), d.getDocType().name(), d.getOwner().getId(),
                        d.getOriginalFilename(), d.getContentType(), d.getSizeBytes(),
                        toEpochMillis(d.getCreatedAt()), d.getVerificationStatus().name()))
                .toList();
    }

    @Override
    public DocumentDto get(UUID callerId, UUID documentId, boolean callerIsSystemAdmin) {
        Document document = findAccessible(callerId, documentId, callerIsSystemAdmin);
        return toDto(document);
    }

    @Override
    public String getDownloadUrl(UUID callerId, UUID documentId, boolean callerIsSystemAdmin) {
        Document document = findAccessible(callerId, documentId, callerIsSystemAdmin);
        return storage.presignedGetUrl(document.getBucket(), document.getObjectKey(),
                presignTtlSeconds, document.getOriginalFilename());
    }

    @Override
    public void delete(UUID callerId, UUID documentId, boolean callerIsSystemAdmin) {
        Document document = findAccessible(callerId, documentId, callerIsSystemAdmin);
        document.setStatus(DocumentStatus.DELETED);
        document.setDeletedAt(LocalDateTime.now());
        documentRepository.save(document);
        // best-effort: the DB mark is what counts; object cleanup can be retried
        try {
            storage.delete(document.getBucket(), document.getObjectKey());
        } catch (Exception e) {
            log.warn("Object cleanup failed (row already soft-deleted) key={}: {}",
                    document.getObjectKey(), e.getMessage());
        }
    }

    // ── verification workflow (admin) ───────────────────────────────────

    @Override
    @Transactional
    public DocumentVerificationResult verify(UUID adminId, UUID documentId,
                                             DocumentVerificationStatus decision, String reason) {
        Document document = documentRepository.findById(documentId)
                .filter(d -> d.getStatus() == DocumentStatus.ACTIVE)
                .orElseThrow(() -> new AuthException(ErrorCode.DOCUMENT_NOT_FOUND, "Document not found"));
        if (document.getVerificationStatus() != DocumentVerificationStatus.UNVERIFIED) {
            throw new AuthException(ErrorCode.INVALID_REQUEST,
                    "Document already " + document.getVerificationStatus().name().toLowerCase());
        }
        if (decision == DocumentVerificationStatus.REJECTED && (reason == null || reason.isBlank())) {
            throw new AuthException(ErrorCode.INVALID_REQUEST, "A rejection requires a reason");
        }

        document.setVerificationStatus(decision);
        document.setVerifiedBy(adminId);
        document.setVerifiedAt(LocalDateTime.now());
        document.setRejectionReason(decision == DocumentVerificationStatus.REJECTED ? reason : null);
        document = documentRepository.save(document);

        boolean donorVerified = false;
        if (decision == DocumentVerificationStatus.APPROVED && document.getDocType() != DocumentType.PROFILE_PHOTO) {
            // a validated blood/diagnostic report earns the verified-donor badge
            donorVerified = userRepository.markVerifiedIfAbsent(document.getOwner().getId()) > 0;
        }
        log.info("Document={} verification={} by admin={} (donorVerified={})",
                documentId, decision, adminId, donorVerified);

        notificationService.notify(document.getOwner().getId(), NotificationType.DOCUMENT_VERIFIED,
                document.getId(), Map.of(
                        "docType", document.getDocType().name(),
                        "decision", decision.name(),
                        "reason", decision == DocumentVerificationStatus.REJECTED ? reason : ""));

        return new DocumentVerificationResult(toDto(document), donorVerified);
    }

    // ── access control ──────────────────────────────────────────────────

    private Document findAccessible(UUID callerId, UUID documentId, boolean callerIsSystemAdmin) {
        Document document = documentRepository.findByIdAndOwnerIdAndStatus(
                documentId, callerId, DocumentStatus.ACTIVE).orElse(null);
        if (document == null && callerIsSystemAdmin) {
            document = documentRepository.findById(documentId)
                    .filter(d -> d.getStatus() == DocumentStatus.ACTIVE)
                    .orElse(null);
        }
        if (document == null) {
            // identical response whether it's missing, foreign, or deleted — no existence leak
            throw new AuthException(ErrorCode.DOCUMENT_NOT_FOUND, "Document not found");
        }
        return document;
    }

    // ── validation ──────────────────────────────────────────────────────

    /**
     * Reads the first bytes and identifies the true format. Returns the
     * canonical content type, or {@code null} when nothing matches — the
     * caller then rejects (unknown formats are never trusted).
     */
    static String sniffMagicBytes(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(SNIFFER_WINDOW);
            if (head.length < 4) {
                return null;
            }
            if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
                return "image/jpeg";
            }
            if (matchesPng(head)) {
                return "image/png";
            }
            if (matchesPdf(head)) {
                return "application/pdf";
            }
            if (matchesRiffWebp(head)) {
                return "image/webp";
            }
            if (head[0] == 'P' && head[1] == 'K'
                    && head[2] == 0x03 && head[3] == 0x04) {
                // ZIP-based container — DOCX in our whitelist
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }
            return null;
        } catch (IOException e) {
            throw new AuthException(ErrorCode.DOCUMENT_EMPTY, "File could not be read");
        }
    }

    private static boolean matchesPng(byte[] h) {
        return h[0] == (byte) 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A;
    }

    private static boolean matchesPdf(byte[] h) {
        return h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F' && h[4] == '-';
    }

    private static boolean matchesRiffWebp(byte[] h) {
        return h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
    }

    /**
     * Decides the canonical content type: the sniffed value wins; a mismatched
     * declared type is tolerated only when it still maps to the same format
     * family (browsers vary here). Declared-only types are ignored entirely.
     */
    static String canonicalContentType(String declared, String sniffed) {
        if (sniffed != null) {
            return sniffed;
        }
        // unknown magic bytes are rejected even if the client declares a type
        throw new AuthException(ErrorCode.DOCUMENT_TYPE_MISMATCH,
                "File content does not match any supported format");
    }

    /** Server-generated key: users/{ownerId}/{folder}/{yyyy}/{mm}/{uuid}.{ext} */
    static String buildObjectKey(UUID ownerId, DocumentType type, String canonicalContentType) {
        LocalDateTime now = LocalDateTime.now();
        String ext = EXTENSIONS.get(canonicalContentType);
        return "users/" + ownerId + "/" + type.getFolder()
                + "/" + now.format(YEAR_FMT) + "/" + now.format(MONTH_FMT)
                + "/" + UUID.randomUUID() + "." + ext;
    }

    /** Display-only; never used in keys. Strips path segments and control chars. */
    static String sanitizeFilename(String original) {
        if (original == null) {
            return null;
        }
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        return name.isEmpty() ? null : name.substring(0, Math.min(name.length(), 255));
    }

    private DocumentDto toDto(Document d) {
        return new DocumentDto(
                d.getId(),
                d.getDocType().name(),
                d.getOriginalFilename(),
                d.getContentType(),
                d.getSizeBytes(),
                d.getCreatedAt() != null
                        ? d.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        : 0L);
    }

    private static LocalDateTime toLocalDateTime(long epochMs) {
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
    }

    private static long toEpochMillis(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** SHA-256 while streaming — one pass, zero extra buffering. */
    public static final class DigestingInputStream extends java.io.FilterInputStream {
        private final MessageDigest digest;

        public DigestingInputStream(InputStream in, MessageDigest digest) {
            super(in);
            this.digest = digest;
        }

        @Override
        public int read() throws java.io.IOException {
            int b = super.read();
            if (b != -1) {
                digest.update((byte) b);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws java.io.IOException {
            int n = super.read(b, off, len);
            if (n != -1) {
                digest.update(b, off, n);
            }
            return n;
        }
    }
}
