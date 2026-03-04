package com.itq.generator.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для создания документа то, что нужно отправить в API
 */
@Data
@NoArgsConstructor
public class CreateDocumentRequest {
    private String author;
    private String title;

    @JsonCreator
    public CreateDocumentRequest(
            @JsonProperty("author") String author,
            @JsonProperty("title") String title) {
        this.author = author;
        this.title = title;
    }
}
