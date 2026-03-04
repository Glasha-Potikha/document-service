package com.itq.document_service.service;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис для атомарных операций с документами.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentAtomicService {
    private final DocumentRepository documentRepository;
    private final DocumentStatusHistoryRepository historyRepository;
    private final ApprovalRegistryRepository registryRepository;

    @Transactional
    public void submitDocumentAtomically(Long id, String initiator) {
        Document document = documentRepository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
        //проверка безопасности перехода
        if (!document.getStatus().canTransitionTo(DocumentStatus.SUBMITTED)) {
            throw new InvalidStatusTransitionException(id, document.getStatus(), DocumentAction.SUBMIT);
        }
        //смена статуса
        document.setStatus(DocumentStatus.SUBMITTED);
        documentRepository.save(document);

        //пишем историю
        DocumentStatusHistory history = DocumentStatusHistory.createHistory(document, initiator, DocumentAction.SUBMIT, null);
        historyRepository.save(history);
        log.info("Документ {} отправлен на согласование", id);
    }

    @Transactional
    public void approveDocumentAtomically(Long id, String initiator, String comment) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));

        if (!document.getStatus().canTransitionTo(DocumentStatus.APPROVED)) {
            throw new InvalidStatusTransitionException(id, document.getStatus(), DocumentAction.APPROVE);
        }

        document.setStatus(DocumentStatus.APPROVED);
        documentRepository.save(document);

        DocumentStatusHistory history = DocumentStatusHistory.createHistory(document, initiator, DocumentAction.APPROVE, comment);
        historyRepository.save(history);

        try {
            ApprovalRegistry registry = ApprovalRegistry.createForDocument(document, initiator);
            registryRepository.save(registry);
            registryRepository.flush();
        } catch (DataIntegrityViolationException e) {
            //уникальное ограничение - запись существует
            log.warn("Запись в реестре для документа {} уже существует (конкурентное утверждение)", id);
        } catch (Exception e) {
            log.error("Ошибка записи в реестр для документа {}, откат транзакции", id, e);
            throw new RegistryException(id, "Ошибка БД при записи в реестр");
        }

        log.info("Документ {} утвержден", id);
    }
}
