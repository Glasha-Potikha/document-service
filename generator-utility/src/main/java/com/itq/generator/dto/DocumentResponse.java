package com.itq.generator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO для ответа от API
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentResponse {
    Long id;
    String uniqueNumber;
    String author;
    String title;
    String status;
    LocalDateTime createdAt;
}
