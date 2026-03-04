package com.itq.document_service.dto;

import com.itq.document_service.model.enums.DocumentStatus;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ConcurrentTestResult {
    Long documentId;
    int totalAttempts;
    int successCount;
    int conflictCount;
    int errorCount;
    DocumentStatus finalStatus;
    List<String> details;
}
