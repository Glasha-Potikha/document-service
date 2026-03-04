package com.itq.document_service.dto;

import com.itq.document_service.model.DocumentStatusHistory;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class DocumentHistoryResponse {
    Long id;
    String initiator;
    String action;
    String comment;
    LocalDateTime createdAt;

    public static DocumentHistoryResponse fromHistory(DocumentStatusHistory history) {
        return DocumentHistoryResponse.builder()
                .id(history.getId())
                .initiator(history.getInitiator())
                .action(history.getAction().name())
                .comment(history.getComment())
                .createdAt(history.getCreatedAt())
                .build();
    }
}
