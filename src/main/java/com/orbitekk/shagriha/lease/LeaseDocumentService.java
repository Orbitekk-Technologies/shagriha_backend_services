package com.orbitekk.shagriha.lease;

import com.orbitekk.shagriha.common.ApiException;
import com.orbitekk.shagriha.property.PropertyReader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class LeaseDocumentService {
    private static final long MAX_FILE_SIZE = 15L * 1024 * 1024;
    private final JdbcClient jdbc;
    private final PropertyReader properties;

    public LeaseDocumentService(JdbcClient jdbc, PropertyReader properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public DocumentInfo info(long propertyId, UUID userId) {
        assertCanAccess(propertyId, userId);
        return findInfo(propertyId).orElse(new DocumentInfo(false, null, null));
    }

    public DocumentInfo upload(long propertyId, UUID managerId, MultipartFile file) {
        if (!properties.isManagedBy(propertyId, managerId)) throw ApiException.notFound("Property not found");
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("lease.pdf");
        boolean pdf = "application/pdf".equalsIgnoreCase(file.getContentType()) || name.toLowerCase().endsWith(".pdf");
        if (file.isEmpty() || !pdf) throw new IllegalArgumentException("A PDF lease document is required");
        if (file.getSize() > MAX_FILE_SIZE) throw new IllegalArgumentException("The PDF must be smaller than 15 MB");
        try {
            jdbc.sql("""
                INSERT INTO property_lease_documents(property_id,file_name,content_type,content)
                VALUES(:propertyId,:fileName,'application/pdf',:content)
                ON CONFLICT(property_id) DO UPDATE SET file_name=EXCLUDED.file_name,
                    content_type=EXCLUDED.content_type,content=EXCLUDED.content,uploaded_at=now()
                """).param("propertyId", propertyId).param("fileName", name)
                    .param("content", file.getBytes()).update();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read the lease document", exception);
        }
        return findInfo(propertyId).orElseThrow();
    }

    public StoredDocument downloadForProperty(long propertyId, UUID userId) {
        assertCanAccess(propertyId, userId);
        return document(propertyId);
    }

    public StoredDocument downloadForLease(long leaseId, UUID userId) {
        Long propertyId = jdbc.sql("SELECT property_id FROM leases WHERE id=:id AND tenant_user_id=:userId")
                .param("id", leaseId).param("userId", userId).query(Long.class).optional()
                .orElseThrow(() -> ApiException.notFound("Lease not found"));
        return document(propertyId);
    }

    public boolean exists(long propertyId) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM property_lease_documents WHERE property_id=:id)")
                .param("id", propertyId).query(Boolean.class).single();
    }

    private Optional<DocumentInfo> findInfo(long propertyId) {
        return jdbc.sql("SELECT file_name,uploaded_at FROM property_lease_documents WHERE property_id=:id")
                .param("id", propertyId).query((rs, n) -> new DocumentInfo(true, rs.getString("file_name"),
                        rs.getTimestamp("uploaded_at").toInstant())).optional();
    }

    private StoredDocument document(long propertyId) {
        return jdbc.sql("SELECT file_name,content_type,content FROM property_lease_documents WHERE property_id=:id")
                .param("id", propertyId).query((rs, n) -> new StoredDocument(rs.getString("file_name"),
                        rs.getString("content_type"), rs.getBytes("content"))).optional()
                .orElseThrow(() -> ApiException.notFound("Lease document not found"));
    }

    private void assertCanAccess(long propertyId, UUID userId) {
        boolean allowed = jdbc.sql("""
            SELECT EXISTS(SELECT 1 FROM properties p WHERE p.id=:propertyId AND
              (p.manager_user_id=:userId OR EXISTS(SELECT 1 FROM leases le
               WHERE le.property_id=p.id AND le.tenant_user_id=:userId)))
            """).param("propertyId", propertyId).param("userId", userId).query(Boolean.class).single();
        if (!allowed) throw ApiException.notFound("Property not found");
    }

    public record DocumentInfo(boolean available, String fileName, Instant uploadedAt) {}
    public record StoredDocument(String fileName, String contentType, byte[] content) {}
}
