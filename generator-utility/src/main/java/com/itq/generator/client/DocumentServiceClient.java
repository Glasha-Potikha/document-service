package com.itq.generator.client;

import com.itq.generator.dto.CreateDocumentRequest;
import com.itq.generator.dto.DocumentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceClient {
    private final RestTemplate restTemplate;

    @Value("${service.url:http://localhost:8080}")
    private String serviceUrl;

    public DocumentResponse createDocument(String author, String title) {
        String url = serviceUrl + "/api/documents";
        CreateDocumentRequest request = new CreateDocumentRequest(author, title);
        try {
            return restTemplate.postForObject(url, request, DocumentResponse.class);
        } catch (Exception e) {
            log.error("Ошибка при вызове API создания документа: {}", e.getMessage());
            throw e;
        }
    }
}
