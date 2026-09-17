package com.orbitekk.shagriha.lease;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/properties/{propertyId}/lease-document")
public class LeaseDocumentController {
    private final LeaseDocumentService documents;
    public LeaseDocumentController(LeaseDocumentService documents) { this.documents = documents; }

    @GetMapping LeaseDocumentService.DocumentInfo info(@AuthenticationPrincipal Jwt jwt, @PathVariable long propertyId) {
        return documents.info(propertyId, subject(jwt));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    LeaseDocumentService.DocumentInfo upload(@AuthenticationPrincipal Jwt jwt, @PathVariable long propertyId,
                                              @RequestPart("file") MultipartFile file) {
        return documents.upload(propertyId, subject(jwt), file);
    }

    @GetMapping("/download") ResponseEntity<ByteArrayResource> download(@AuthenticationPrincipal Jwt jwt,
                                                                         @PathVariable long propertyId) {
        return response(documents.downloadForProperty(propertyId, subject(jwt)));
    }

    static ResponseEntity<ByteArrayResource> response(LeaseDocumentService.StoredDocument document) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(document.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(new ByteArrayResource(document.content()));
    }

    private static UUID subject(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
