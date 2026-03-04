package com.itq.document_service.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ConcurrentTestRequest {
    @Min(1)
    Long documentId;

    @Min(1)
    @Max(20)
    int threads;

    @Min(1)
    @Max(100)
    int attempts;

    @JsonCreator
    public ConcurrentTestRequest(@JsonProperty("documentId") Long documentId,
                                 @JsonProperty("threads") int threads,
                                 @JsonProperty("attempts") int attempts) {
        this.documentId = documentId;
        this.threads = threads;
        this.attempts = attempts;
    }
}
