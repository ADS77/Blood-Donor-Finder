package com.bd.blooddonorfinder.service.audit;

import com.bd.blooddonorfinder.model.AuditLog;
import com.bd.blooddonorfinder.model.enums.AuditEvent;
import com.bd.blooddonorfinder.repository.audit.AuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditEvent auditEvent, UUID userId, String ipAddress, String userAgent, String metadata){
        try {
            AuditLog auditLog = AuditLog.builder(auditEvent)
                    .userId(userId)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .metadata(metadata)
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e){
            log.error("Failed to write audit log entry for event {}: {}", auditEvent, e.getMessage());
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditEvent auditEvent, UUID userId, String ipAddress, String userAgent) {
        log(auditEvent, userId, ipAddress, userAgent, null);
    }
}
