package com.itq.document_service.repository;

import com.itq.document_service.model.Document;
import com.itq.document_service.model.enums.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    Page<Document> findByIdIn(List<Long> ids, Pageable pageable);

    Page<Document> findAll(Specification<Document> specification, Pageable pageable);

    @Query("SELECT d.id FROM Document d WHERE d.status = :status ORDER BY d.createdAt ASC")
    List<Long> findIdsByStatus(@Param("status") DocumentStatus status, Pageable pageable);

    long countByStatus(DocumentStatus status);
}
