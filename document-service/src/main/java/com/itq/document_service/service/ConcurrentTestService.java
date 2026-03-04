package com.itq.document_service.service;

import com.itq.document_service.dto.ConcurrentTestResult;
import com.itq.document_service.exception.DocumentNotFoundException;
import com.itq.document_service.exception.InvalidStatusTransitionException;
import com.itq.document_service.exception.RegistryException;
import com.itq.document_service.model.ApprovalRegistry;
import com.itq.document_service.model.Document;
import com.itq.document_service.model.DocumentStatusHistory;
import com.itq.document_service.model.enums.DocumentAction;
import com.itq.document_service.model.enums.DocumentStatus;
import com.itq.document_service.repository.ApprovalRegistryRepository;
import com.itq.document_service.repository.DocumentRepository;
import com.itq.document_service.repository.DocumentStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConcurrentTestService {
    private final DocumentRepository documentRepository;
    private final DocumentStatusHistoryRepository historyRepository;
    private final ApprovalRegistryRepository registryRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * Тестирует конкурентное утверждение документа.
     * <p>
     * Запускает несколько параллельных попыток утвердить один документ.
     * Ожидаемое поведение: только одна попытка переводит документ в APPROVED,
     * остальные завершаются конфликтом.
     *
     * @param documentId идентификатор документа
     * @param threads    количество параллельных потоков
     * @param attempts   количество попыток на поток (всего attempts * threads)
     * @return результаты теста
     */
    public ConcurrentTestResult testConcurrentApprove(Long documentId, int threads, int attempts) {
        log.info("Запуск конкурентного теста: documentId={}, threads={}, attempts={}",
                documentId, threads, attempts);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<String> details = new CopyOnWriteArrayList<>();

        //проверка, что документ существует перед тестом
        Document initialDoc = documentRepository.findById(documentId).orElse(null);
        log.info("Начальный статус документа {}: {}", documentId,
                initialDoc != null ? initialDoc.getStatus() : "НЕ НАЙДЕН");

        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final int threadNum = i;
                tasks.add(() -> {
                    for (int j = 0; j < attempts; j++) {
                        final int attemptNum = j;
                        try {
                            transactionTemplate.execute(status -> {
                                try {
                                    log.debug("Попытка {}-{}: начинаем", threadNum, attemptNum);
                                    approveDocumentAtomicallyInternal(documentId, "concurrent-test",
                                            "Конкурентная попытка #" + threadNum + "-" + attemptNum);
                                    successCount.incrementAndGet();
                                    details.add(String.format("Поток %d, попытка %d: УСПЕХ", threadNum, attemptNum));
                                    log.info("Попытка {}-{}: УСПЕХ", threadNum, attemptNum);
                                } catch (InvalidStatusTransitionException e) {
                                    conflictCount.incrementAndGet();
                                    details.add(String.format("Поток %d, попытка %d: КОНФЛИКТ - %s",
                                            threadNum, attemptNum, e.getMessage()));
                                    log.info("Попытка {}-{}: КОНФЛИКТ - {}", threadNum, attemptNum, e.getMessage());
                                    status.setRollbackOnly();
                                } catch (DocumentNotFoundException e) {
                                    errorCount.incrementAndGet();
                                    details.add(String.format("Поток %d, попытка %d: ДОКУМЕНТ НЕ НАЙДЕН - %s",
                                            threadNum, attemptNum, e.getMessage()));
                                    log.error("Попытка {}-{}: ДОКУМЕНТ НЕ НАЙДЕН", threadNum, attemptNum, e);
                                    status.setRollbackOnly();
                                } catch (Exception e) {
                                    errorCount.incrementAndGet();
                                    details.add(String.format("Поток %d, попытка %d: ОШИБКА - %s",
                                            threadNum, attemptNum, e.getMessage()));
                                    log.error("Попытка {}-{}: ОШИБКА", threadNum, attemptNum, e);
                                    status.setRollbackOnly();
                                }
                                return null;
                            });
                        } catch (Exception e) {
                            log.error("Исключение в transactionTemplate {}-{}", threadNum, attemptNum, e);
                        }
                    }
                    return null;
                });
            }

            List<Future<Void>> futures = executorService.invokeAll(tasks);
            for (Future<Void> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    log.error("Задача завершилась с ошибкой", e.getCause());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Тест прерван", e);
        } finally {
            executorService.shutdown();
        }

        //отдельная транзакция для чтения финального статуса
        Document finalDocument = transactionTemplate.execute(status -> {
            Document doc = documentRepository.findById(documentId).orElse(null);
            log.info("Финальный статус документа {}: {}", documentId,
                    doc != null ? doc.getStatus() : "НЕ НАЙДЕН");
            return doc;
        });

        if (finalDocument == null) {
            throw new DocumentNotFoundException(documentId);
        }

        Long registryCount = transactionTemplate.execute(status ->
                registryRepository.countByDocumentId(documentId)
        );
        log.info("Количество записей в реестре: {}", registryCount);

        log.info("Результаты: успех={}, конфликт={}, ошибка={}",
                successCount.get(), conflictCount.get(), errorCount.get());

        return ConcurrentTestResult.builder()
                .documentId(documentId)
                .totalAttempts(threads * attempts)
                .successCount(successCount.get())
                .conflictCount(conflictCount.get())
                .errorCount(errorCount.get())
                .finalStatus(finalDocument.getStatus())
                .details(details)
                .build();
    }

    private void approveDocumentAtomicallyInternal(Long id, String initiator, String comment) {
        log.debug("Попытка утвердить документ {} в транзакции", id);

        //findById может вернуть null из-за кэша Hibernate
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Документ {} не найден в БД", id);
                    return new DocumentNotFoundException(id);
                });

        log.debug("Документ {} найден, статус: {}", id, document.getStatus());

        if (!document.getStatus().canTransitionTo(DocumentStatus.APPROVED)) {
            log.debug("Документ {} не может быть утвержден из статуса {}", id, document.getStatus());
            throw new InvalidStatusTransitionException(id, document.getStatus(), DocumentAction.APPROVE);
        }

        document.setStatus(DocumentStatus.APPROVED);
        documentRepository.save(document);
        log.debug("Документ {} сохранен со статусом APPROVED", id);

        DocumentStatusHistory history = DocumentStatusHistory.createHistory(document, initiator, DocumentAction.APPROVE, comment);
        historyRepository.save(history);
        log.debug("История для документа {} сохранена", id);

        try {
            ApprovalRegistry registry = ApprovalRegistry.createForDocument(document, initiator);
            registryRepository.save(registry);
            registryRepository.flush();
            log.debug("Запись в реестре для документа {} сохранена", id);
        } catch (Exception e) {
            log.error("Ошибка записи в реестр для документа {}, откат транзакции", id, e);
            throw new RegistryException(id, "Ошибка БД при записи в реестр");
        }

        log.info("Документ {} утвержден", id);
    }
}
