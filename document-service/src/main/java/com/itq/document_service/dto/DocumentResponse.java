package com.itq.document_service.dto;

import com.itq.document_service.model.Document;
import com.itq.document_service.model.enums.DocumentStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Value
@Builder
public class DocumentResponse {
    Long id;
    String uniqueNumber;
    String author;
    String title;
    DocumentStatus status;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    List<DocumentHistoryResponse> history;

    public static DocumentResponse fromDocument(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .uniqueNumber(document.getUniqueNumber())
                .author(document.getAuthor())
                .title(document.getTitle())
                .status(document.getStatus())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }

    public static DocumentResponse fromDocumentWithHistory(Document document) {
        List<DocumentHistoryResponse> historyList = null;
        if (document.getStatusHistory() != null) {
            historyList = document.getStatusHistory().stream()
                    .map(DocumentHistoryResponse::fromHistory)
                    .collect(Collectors.toList());
        }
        return DocumentResponse.builder()
                .id(document.getId())
                .uniqueNumber(document.getUniqueNumber())
                .author(document.getAuthor())
                .title(document.getTitle())
                .status(document.getStatus())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .history(historyList)  // ← устанавливаем сразу при создании
                .build();
    }
}
