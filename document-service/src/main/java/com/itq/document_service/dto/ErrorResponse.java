package com.itq.document_service.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Единый формат ошибки согласно ТЗ.
 */
@Value
@Builder
public class ErrorResponse {
    String code;
    String message;
    LocalDateTime timestamp;
}
