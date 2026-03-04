package com.itq.document_service.repository;

import com.itq.document_service.model.DocumentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentStatusHistoryRepository extends JpaRepository<DocumentStatusHistory, Long> {
    List<DocumentStatusHistory> findByDocumentId(Long documentId);
}
