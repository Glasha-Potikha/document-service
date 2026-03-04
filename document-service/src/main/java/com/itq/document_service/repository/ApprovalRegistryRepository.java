package com.itq.document_service.repository;

import com.itq.document_service.model.ApprovalRegistry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApprovalRegistryRepository extends JpaRepository<ApprovalRegistry, Long> {
    long countByDocumentId(Long documentId);

    Optional<ApprovalRegistry> findByDocumentId(Long documentId);
}
