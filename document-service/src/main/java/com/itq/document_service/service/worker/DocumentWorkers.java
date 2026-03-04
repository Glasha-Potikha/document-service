package com.itq.document_service.service.worker;

import com.itq.document_service.dto.BatchOperationResult;
import com.itq.document_service.model.enums.DocumentStatus;
import com.itq.document_service.repository.DocumentRepository;
import com.itq.document_service.service.DocumentService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
@RequiredArgsConstructor
@EnableScheduling
public class DocumentWorkers {
    private final DocumentRepository documentRepository;
    private final DocumentService documentService;

    @Value("${worker.batch-size:100}")
    private int batchSize;

    @Value("${worker.submit.enabled:true}")
    private boolean submitEnabled;

    @Value("${worker.approve.enabled:true}")
    private boolean approveEnabled;

    private final AtomicInteger submittedCount = new AtomicInteger(0);
    private final AtomicInteger approvedCount = new AtomicInteger(0);
    private final AtomicInteger totalProcessed = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        log.info("== Фоновые workers инициализированы ==");
        log.info("Batch size: {}", batchSize);
        log.info("Submit worker enabled: {}", submitEnabled);
        log.info("Approve worker enabled: {}", approveEnabled);
    }

    /**
     * SUBMIT-worker - отправляет DRAFT документы на согласование
     * По умолчанию запускается каждые 30 секунд
     */
    @Scheduled(fixedDelayString = "${worker.submit.interval:30000}")
    public void processSubmitBatch() {
        if (!submitEnabled) {
            log.debug("Submit worker выключен");
            return;
        }

        LocalDateTime startTime = LocalDateTime.now();
        log.info("=== SUBMIT-worker: начало обработки ===");
        try {
            //получаем пачку DRAFT документов
            Pageable pageable = PageRequest.of(0, batchSize);
            List<Long> draftIds = documentRepository.findIdsByStatus(DocumentStatus.DRAFT, pageable);
            if (draftIds.isEmpty()) {
                log.info("SUBMIT-worker: нет документов для обработки");
                return;
            }
            log.info("SUBMIT-worker: найдено {} документов DRAFT для отправки", draftIds.size());
            //отправляем через пакетное API
            List<BatchOperationResult> results = documentService.submitDocuments(
                    draftIds,
                    "SUBMIT-worker"
            );
            // считаем успехи
            long successCount = results.stream()
                    .filter(r -> r.getStatus() == BatchOperationResult.Status.SUCCESS)
                    .count();
            submittedCount.addAndGet((int) successCount);
            totalProcessed.addAndGet(draftIds.size());

            Duration duration = Duration.between(startTime, LocalDateTime.now());

            log.info("=== SUBMIT-worker: завершение ===");
            log.info("Обработано документов: {}", draftIds.size());
            log.info("Успешно отправлено: {}", successCount);
            log.info("Конфликтов/ошибок: {}", draftIds.size() - successCount);
            log.info("Время выполнения: {} мс", duration.toMillis());
            log.info("Всего отправлено за сессию: {}", submittedCount.get());
            log.info("Осталось в очереди: {}", getRemainingDraftCount());

        } catch (Exception e) {
            log.error("SUBMIT-worker: критическая ошибка при обработке пачки", e);
        }
    }

    /**
     * APPROVE-worker - отправляет SUBMITTED документы на утверждение
     * Запускается каждые 45 секунд
     */
    @Scheduled(fixedDelayString = "${worker.approve.interval:45000}")
    public void processApproveBatch() {
        if (!approveEnabled) {
            log.debug("Approve worker выключен");
            return;
        }

        LocalDateTime startTime = LocalDateTime.now();
        log.info("=== APPROVE-worker: начало обработки ===");

        try {
            //получаем пачку SUBMITTED документов
            Pageable pageable = PageRequest.of(0, batchSize);
            List<Long> submittedIds = documentRepository.findIdsByStatus(DocumentStatus.SUBMITTED, pageable);

            if (submittedIds.isEmpty()) {
                log.info("APPROVE-worker: нет документов для обработки");
                return;
            }
            log.info("APPROVE-worker: найдено {} документов SUBMITTED для утверждения", submittedIds.size());

            //Утверждаем через пакетное API
            List<BatchOperationResult> results = documentService.approveDocuments(
                    submittedIds,
                    "APPROVE-worker",
                    "Автоматическое утверждение фоновым worker'ом"
            );

            // считаем успехи
            long successCount = results.stream()
                    .filter(r -> r.getStatus() == BatchOperationResult.Status.SUCCESS)
                    .count();

            long conflictCount = results.stream()
                    .filter(r -> r.getStatus() == BatchOperationResult.Status.CONFLICT)
                    .count();

            long registryErrorCount = results.stream()
                    .filter(r -> r.getStatus() == BatchOperationResult.Status.REGISTRY_ERROR)
                    .count();

            approvedCount.addAndGet((int) successCount);
            totalProcessed.addAndGet(submittedIds.size());

            Duration duration = Duration.between(startTime, LocalDateTime.now());

            log.info("=== APPROVE-worker: завершение ===");
            log.info("Обработано документов: {}", submittedIds.size());
            log.info("Успешно утверждено: {}", successCount);
            log.info("Конфликтов (неверный статус): {}", conflictCount);
            log.info("Ошибок реестра: {}", registryErrorCount);
            log.info("Время выполнения: {} мс", duration.toMillis());
            log.info("Всего утверждено за сессию: {}", approvedCount.get());
            log.info("Осталось на утверждение: {}", getRemainingSubmittedCount());

            //логируем детали ошибок
            results.stream()
                    .filter(r -> r.getStatus() != BatchOperationResult.Status.SUCCESS)
                    .forEach(r -> log.warn("APPROVE-worker: документ {} - {}",
                            r.getDocumentId(), r.getMessage()));
        } catch (Exception e) {
            log.error("APPROVE-worker: критическая ошибка при обработке пачки", e);
        }
    }

    /**
     * Отчет о прогрессе каждые 5 минут
     */
    @Scheduled(fixedDelay = 300000)
    public void logProgress() {
        long draftCount = documentRepository.countByStatus(DocumentStatus.DRAFT);
        long submittedCount = documentRepository.countByStatus(DocumentStatus.SUBMITTED);
        long approvedCount = documentRepository.countByStatus(DocumentStatus.APPROVED);

        log.info("=== ПРОГРЕСС ФОНОВОЙ ОБРАБОТКИ ===");
        log.info("Всего обработано пачек: {}", totalProcessed.get());
        log.info("Отправлено на согласование (всего): {}", this.submittedCount.get());
        log.info("Утверждено (всего): {}", this.approvedCount.get());
        log.info("Текущее состояние БД:");
        log.info("  DRAFT: {} документов", draftCount);
        log.info("  SUBMITTED: {} документов", submittedCount);
        log.info("  APPROVED: {} документов", approvedCount);
    }

    private long getRemainingDraftCount() {
        return documentRepository.countByStatus(DocumentStatus.DRAFT);
    }

    private long getRemainingSubmittedCount() {
        return documentRepository.countByStatus(DocumentStatus.SUBMITTED);
    }
}
