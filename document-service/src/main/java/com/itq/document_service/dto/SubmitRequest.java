package com.itq.document_service.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class SubmitRequest {
    @NotEmpty(message = "Список ID не может быть пустым")
    @Size(min = 1, max = 1000, message = "Количество ID должно быть от 1 до 1000")
    List<@Min(1) Long> ids;

    @NotBlank(message = "Инициатор не должен быть пустым")
    String initiator;

    @JsonCreator
    public SubmitRequest(
            @JsonProperty("ids") List<Long> ids,
            @JsonProperty("initiator") String initiator) {
        this.initiator = initiator;
        this.ids = ids;
    }
}
