package com.itq.document_service.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CreateDocumentRequest {
    @NotBlank(message = "Автор не может быть пустым")
    @Size(max = 255, message = "Автор не может быть длиннее 255 символов")
    String author;

    @NotBlank(message = "Название не может быть пустым")
    @Size(max = 500, message = "Название не может быть длиннее 500 символов")
    String title;

    @JsonCreator
    public CreateDocumentRequest(
            @JsonProperty("author") String author,
            @JsonProperty("title") String title) {
        this.author = author;
        this.title = title;
    }
}
