package com.itq.document_service.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Результат обработки одного документа в пакетном запросе.
 */
@Value
@Builder
public class BatchOperationResult {
    Long documentId;
    Status status;
    String message;

    public enum Status {
        SUCCESS, NOT_FOUND, CONFLICT, REGISTRY_ERROR
    }

    public static BatchOperationResult success(Long documentId) {
        return BatchOperationResult.builder()
                .documentId(documentId)
                .status(Status.SUCCESS)
                .message("Успешно")
                .build();
    }

    public static BatchOperationResult notFound(Long documentId) {
        return BatchOperationResult.builder()
                .documentId(documentId)
                .status(Status.NOT_FOUND)
                .message("Не найдено")
                .build();
    }

    public static BatchOperationResult conflict(Long documentId) {
        return BatchOperationResult.builder()
                .documentId(documentId)
                .status(Status.CONFLICT)
                .message("Конфликт")
                .build();
    }

    public static BatchOperationResult registryError(Long documentId, String comment) {
        return BatchOperationResult.builder()
                .documentId(documentId)
                .status(Status.REGISTRY_ERROR)
                .message("Ошибка регистрации: " + comment)
                .build();
    }
}
