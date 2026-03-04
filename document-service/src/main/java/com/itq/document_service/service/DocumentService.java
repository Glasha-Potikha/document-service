package com.itq.document_service.service;

import com.itq.document_service.dto.BatchOperationResult;
import com.itq.document_service.dto.DocumentResponse;
import com.itq.document_service.exception.DocumentNotFoundException;
import com.itq.document_service.exception.InvalidStatusTransitionException;
import com.itq.document_service.exception.RegistryException;
import com.itq.document_service.model.Document;
import com.itq.document_service.model.enums.DocumentStatus;
import com.itq.document_service.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Сервис для управления документами.
 * <p>
 * Содержит бизнес-логику создания, изменения статусов и поиска документов.
 * Все операции с изменением статусов транзакционы и атомарны.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {
    private final DocumentRepository documentRepository;
    private final DocumentAtomicService atomicService;

    @Transactional
    public DocumentResponse createDocument(String author, String title) {
        log.debug("Создание документа: author={}, title={}", author, title);

        //валидация
        if (author == null || author.trim().isEmpty()) {
            throw new IllegalArgumentException("Автор не должен быть пустым");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Название не должно быть пустым");
        }

        String uniqueNumber = generateUniqueNumber();

        Document document = Document.builder()
                .uniqueNumber(uniqueNumber)
                .author(author.trim())
                .title(title.trim())
                .status(DocumentStatus.DRAFT)
                .build();

        Document saved = documentRepository.save(document);
        log.info("Создан документ: id={}, number={}", saved.getId(), saved.getUniqueNumber());
        return DocumentResponse.fromDocument(saved);
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocumentById(Long id) {
        log.debug("Поиск документа по id: {}", id);
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        return DocumentResponse.fromDocumentWithHistory(document);
    }

    @Transactional(readOnly = true)
    //пакетное получение с пагинацией/сортировкой по списку id
    public Page<DocumentResponse> getDocumentsByIds(List<Long> ids, Pageable pageable) {
        log.debug("Пакетное получение документов: {} ids, page={}", ids.size(), pageable);
        return documentRepository.findByIdIn(ids, pageable).map(DocumentResponse::fromDocument);
    }

    /**
     * Поиск документов по фильтрам
     * <p>
     * Фильтрация по дате создания.
     *
     * @param status   -статус дока - опционально
     * @param author   автор - опционально, поиск по частичному совпадению
     * @param dateFrom начальная дата создания - опционально
     * @param dateTo   конечная дата создания - опционально
     * @param pageable параметры пагинации и сортировки
     * @return страница с документами
     */
    @Transactional(readOnly = true)
    public Page<DocumentResponse> searchDocuments(DocumentStatus status,
                                                  String author,
                                                  LocalDateTime dateFrom,
                                                  LocalDateTime dateTo,
                                                  Pageable pageable) {
        log.debug("Поиск документов: status={}, author={}, from={}, to={}",
                status, author, dateFrom, dateTo);
        Specification<Document> spec = Specification.where((root, query, cb) -> cb.isTrue(cb.literal(true)));
        if (status != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("status"), status));
        }
        if (author != null && !author.isEmpty()) {
            spec = spec.and(((root, query, cb) ->
                    cb.like(cb.lower(root.get("author")), "%" + author.toLowerCase() + "%")));
        }
        if (dateFrom != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom));
        }
        if (dateTo != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("createdAt"), dateTo));
        }
        return documentRepository.findAll(spec, pageable).map(DocumentResponse::fromDocument);
    }

    /**
     * Пакетная отправка документов на согласование.
     * <p>
     * Переводит документы из DRAFT в SUBMITTED.
     * Каждый документ обрабатывается атомарно в отдельной транзакции.
     *
     * @param ids       список идентификаторов документов
     * @param initiator инициатор действия
     * @return список результатов для каждого ID
     */
    public List<BatchOperationResult> submitDocuments(List<Long> ids, String initiator) {
        log.info("Пакетная отправка на согласование: {} документов, инициатор={}", ids.size(), initiator);
        return ids.stream()
                .map(id -> processSubmitDocument(id, initiator))
                .collect(Collectors.toList());
    }

    /**
     * Метод обработки одного документа для submit
     */
    private BatchOperationResult processSubmitDocument(Long id, String initiator) {
        try {
            atomicService.submitDocumentAtomically(id, initiator);
            return BatchOperationResult.success(id);
        } catch (DocumentNotFoundException e) {
            log.warn("Документ {} не найден при отправке", id);
            return BatchOperationResult.notFound(id);
        } catch (InvalidStatusTransitionException e) {
            log.warn("Конфликт статусов для документа {}: {}", id, e.getMessage());
            return BatchOperationResult.conflict(id);
        } catch (Exception e) {
            log.warn("Неожиданная ошибка при отправке документа {}", id, e);
            return BatchOperationResult.builder()
                    .documentId(id)
                    .status(BatchOperationResult.Status.CONFLICT)
                    .message("Внутренняя ошибка: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Пакетное утверждение документов.
     * <p>
     * Переводит документы из SUBMITTED в APPROVED.
     * При успешном утверждении создается запись в реестре.
     * Если запись в реестр не удалась - транзакция откатывается.
     *
     * @param ids       список идентификаторов документов
     * @param initiator инициатор действия
     * @param comment   комментарий (может быть null)
     * @return список результатов для каждого ID
     */

    public List<BatchOperationResult> approveDocuments(List<Long> ids,
                                                       String initiator,
                                                       String comment) {
        log.info("Пакетное утверждение: {} документов, инициатор={}", ids.size(), initiator);
        return ids.stream()
                .map(id -> processApproveDocument(id, initiator, comment))
                .collect(Collectors.toList());
    }

    /**
     * Метод обработки одного документа для approve
     */
    private BatchOperationResult processApproveDocument(Long id, String initiator, String comment) {
        try {
            atomicService.approveDocumentAtomically(id, initiator, comment);
            return BatchOperationResult.success(id);
        } catch (DocumentNotFoundException e) {
            log.warn("Документ {} не найден при утверждении", id);
            return BatchOperationResult.notFound(id);
        } catch (InvalidStatusTransitionException e) {
            log.warn("Конфликт статусов для документа {}: {}", id, e.getMessage());
            return BatchOperationResult.conflict(id);
        } catch (DataIntegrityViolationException e) {
            log.warn("Конкурентное утверждение для документа {}", id);
            return BatchOperationResult.conflict(id);
        } catch (RegistryException e) {
            log.error("Ошибка регистрации документа {} в реестре", id, e);
            return BatchOperationResult.builder()
                    .documentId(id)
                    .status(BatchOperationResult.Status.REGISTRY_ERROR)
                    .message(e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Неожиданная ошибка при утверждении документа {}", id, e);
            return BatchOperationResult.builder()
                    .documentId(id)
                    .status(BatchOperationResult.Status.CONFLICT)
                    .message("Внутренняя ошибка: " + e.getMessage())
                    .build();
        }
    }


    //генерация уникального номера в формате "DOC-yyyymmdd-UUID"
    private String generateUniqueNumber() {
        return String.format("DOC-%s-%s",
                LocalDateTime.now().toString().substring(0, 10).replace("-", ""),
                UUID.randomUUID().toString().substring(0, 8)
        );
    }
}


