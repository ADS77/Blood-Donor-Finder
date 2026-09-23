package com.bd.blooddonorfinder.repository.audit;

import com.bd.blooddonorfinder.model.AuditLog;
import com.bd.blooddonorfinder.model.enums.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<AuditLog> findByUserIdAndAuditEventOrderByCreatedAtDesc(UUID userId, AuditEvent auditEvent);

    List<AuditLog> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
            UUID userId, Instant after);
}
