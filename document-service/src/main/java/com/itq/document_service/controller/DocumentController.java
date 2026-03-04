package com.itq.document_service.controller;

import com.itq.document_service.dto.*;
import com.itq.document_service.model.enums.DocumentStatus;
import com.itq.document_service.service.DocumentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST контроллер для управления документами.
 * <p>
 * Предоставляет API для создания, получения, изменения статусов и поиска документов.
 * Все пакетные операции возвращают частичные результаты согласно ТЗ.
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {
    private final DocumentService documentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse createDocument(@Valid @RequestBody CreateDocumentRequest request) {
        log.info("POST /api/documents - создание документа: author={}, title={}", request.getAuthor(), request.getTitle());
        return documentService.createDocument(request.getAuthor(), request.getTitle());
    }

    @GetMapping("/{id}")
    public DocumentResponse getDocument(@PathVariable Long id) {
        log.info("GET /api/documents/{} - получение документа", id);
        return documentService.getDocumentById(id);
    }

    /**
     * Пакетное получение документов по списку ID.
     * <p>
     * Поддерживает пагинацию и сортировку через query parameters.
     *
     * @param ids      список ID через запятую (обязательно)
     * @param pageable параметры пагинации (page, size, sort)
     * @return страница с документами
     */
    @GetMapping
    public Page<DocumentResponse> getDocuments(
            @RequestParam @NotEmpty List<Long> ids,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("GET /api/documents - пакетное получение: {} ids, page={}", ids.size(), pageable);
        return documentService.getDocumentsByIds(ids, pageable);
    }

    /**
     * Поиск документов по фильтрам.
     * <p>
     * Фильтры: статус, автор, период дат создания.
     * Все фильтры опциональны.
     *
     * @param status   статус документа (DRAFT, SUBMITTED, APPROVED)
     * @param author   автор (частичное совпадение, регистронезависимо)
     * @param dateFrom начальная дата создания (включительно)
     * @param dateTo   конечная дата создания (включительно)
     * @param pageable параметры пагинации
     * @return страница с документами
     */
    @GetMapping("/search")
    public Page<DocumentResponse> searchDocuments(
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("GET /api/documents/search - поиск: status={}, author={}, from={}, to={}", status, author, dateFrom, dateTo);
        return documentService.searchDocuments(status, author, dateFrom, dateTo, pageable);
    }

    /**
     * Отправляет документы на согласование.
     * <p>
     * Принимает список ID (1-1000) и переводит DRAFT -> SUBMITTED.
     * Возвращает результат для каждого ID.
     *
     * @param request запрос с IDs и инициатором
     * @return список результатов для каждого документа
     */
    @PostMapping("/submit")
    public List<BatchOperationResult> submitDocuments(@Valid @RequestBody SubmitRequest request) {
        log.info("POST /api/documents/submit - отправка на согласование: {} документов", request.getIds().size());
        return documentService.submitDocuments(request.getIds(), request.getInitiator());
    }

    /**
     * Утверждает документы.
     * <p>
     * Принимает список ID (1-1000) и переводит SUBMITTED -> APPROVED.
     * При успехе создает запись в реестре утверждений.
     * Возвращает результат для каждого ID.
     *
     * @param request запрос с IDs, инициатором и комментарием
     * @return список результатов для каждого документа
     */
    @PostMapping("/approve")
    public List<BatchOperationResult> approveDocuments(@Valid @RequestBody ApproveRequest request) {
        log.info("POST /api/documents/approve - утверждение: {} документов", request.getIds().size());
        return documentService.approveDocuments(
                request.getIds(),
                request.getInitiator(),
                request.getComment()
        );
    }

}
