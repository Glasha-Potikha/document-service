package com.itq.document_service.controller;

import com.itq.document_service.dto.ConcurrentTestRequest;
import com.itq.document_service.dto.ConcurrentTestResult;
import com.itq.document_service.service.ConcurrentTestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Slf4j
public class ConcurrentTestController {
    private final ConcurrentTestService service;

    /**
     * Тестовый endpoint для проверки конкурентного утверждения.
     * <p>
     * Запускает несколько параллельных попыток утвердить один документ.
     *
     * @param request параметры теста
     * @return результаты конкурентных попыток
     */
    @PostMapping("/concurrent-approve")
    public ConcurrentTestResult testConcurrentApprove(@Valid @RequestBody ConcurrentTestRequest request) {
        log.info("POST /api/documents/test/concurrent-approve - конкурентный тест: " +
                        "documentId={}, threads={}, attempts={}",
                request.getDocumentId(), request.getThreads(), request.getAttempts());
        return service.testConcurrentApprove(
                request.getDocumentId(),
                request.getThreads(),
                request.getAttempts()
        );
    }
}
