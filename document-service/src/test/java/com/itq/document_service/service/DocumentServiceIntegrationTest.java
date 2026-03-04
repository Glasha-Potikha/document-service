package com.itq.document_service.service;

import com.itq.document_service.dto.BatchOperationResult;
import com.itq.document_service.dto.DocumentResponse;
import com.itq.document_service.exception.DocumentNotFoundException;
import com.itq.document_service.model.ApprovalRegistry;
import com.itq.document_service.model.Document;
import com.itq.document_service.model.DocumentStatusHistory;
import com.itq.document_service.model.enums.DocumentAction;
import com.itq.document_service.model.enums.DocumentStatus;
import com.itq.document_service.repository.ApprovalRegistryRepository;
import com.itq.document_service.repository.DocumentRepository;
import com.itq.document_service.repository.DocumentStatusHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Интеграционные тесты для DocumentService.
 * <p>
 * Тестирует реальное взаимодействие с БД и транзакционное поведение.
 * Каждый тест изолирован и не влияет на другие тесты.
 */
@SpringBootTest
@Testcontainers
@DisplayName("Интеграционные тесты DocumentService")
@Slf4j
class DocumentServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
    }

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentStatusHistoryRepository historyRepository;

    @Autowired
    private ApprovalRegistryRepository registryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        //Очистка базы перед каждым тестом
        registryRepository.deleteAll();
        historyRepository.deleteAll();
        documentRepository.deleteAll();

        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Nested
    @DisplayName("Тесты создания документа")
    class CreateDocumentTests {

        @Test
        @DisplayName("Создание документа с валидными данными должно сохранять его в БД")
        void createDocument_ValidData_SavesToDatabase() {
            //act
            DocumentResponse document = documentService.createDocument("Test Author", "Test Title");

            //assert
            assertThat(document.getId()).isNotNull();
            assertThat(document.getUniqueNumber()).startsWith("DOC-");
            assertThat(document.getStatus()).isEqualTo(DocumentStatus.DRAFT);
            assertThat(document.getCreatedAt()).isNotNull();
            assertThat(document.getUpdatedAt()).isNotNull();

            //Проверяем, что документ реально сохранен в БД
            Document found = documentRepository.findById(document.getId()).orElse(null);
            assertThat(found).isNotNull();
            assertThat(found.getAuthor()).isEqualTo("Test Author");
            assertThat(found.getTitle()).isEqualTo("Test Title");
        }

        @Test
        @DisplayName("Создание документа с пустым автором должно выбрасывать исключение и не сохранять документ")
        void createDocument_EmptyAuthor_ThrowsException() {
            //act&assert
            assertThatThrownBy(() -> documentService.createDocument("", "Test Title"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Автор не должен быть пустым");

            //Проверяем, что ничего не сохранилось
            assertThat(documentRepository.count()).isZero();
        }
    }

    @Nested
    @DisplayName("Тесты получения документов")
    class GetDocumentsTests {

        private List<Document> savedDocuments;
        private LocalDateTime now;

        @BeforeEach
        void setUp() {
            now = LocalDateTime.now();
            //Создаем тестовые документы
            Document doc1 = Document.builder()
                    .uniqueNumber("DOC-001")
                    .author("Author 1")
                    .title("Title 1")
                    .status(DocumentStatus.DRAFT)
                    .createdAt(now.minusDays(2))
                    .updatedAt(now.minusDays(2))
                    .build();

            Document doc2 = Document.builder()
                    .uniqueNumber("DOC-002")
                    .author("Author 2")
                    .title("Title 2")
                    .status(DocumentStatus.SUBMITTED)
                    .createdAt(now.minusDays(1))
                    .updatedAt(now.minusDays(1))
                    .build();

            Document doc3 = Document.builder()
                    .uniqueNumber("DOC-003")
                    .author("Author 1")
                    .title("Title 3")
                    .status(DocumentStatus.APPROVED)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            savedDocuments = documentRepository.saveAll(List.of(doc1, doc2, doc3));
        }

        @Test
        @DisplayName("Получение документа по ID должно возвращать документ с историей")
        void getDocumentById_ReturnsDocumentWithHistory() {
            //Arrange
            Document doc = savedDocuments.get(0);

            //Добавляем историю к документу
            transactionTemplate.execute(status -> {
                Document document = documentRepository.findById(doc.getId()).orElseThrow();
                DocumentStatusHistory history = DocumentStatusHistory.createHistory(
                        document, "initiator", DocumentAction.SUBMIT, null);
                historyRepository.save(history);
                return null;
            });

            //act
            DocumentResponse found = documentService.getDocumentById(doc.getId());

            //assert
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(doc.getId());
        }

        @Test
        @DisplayName("Получение документа по несуществующему ID должно выбрасывать DocumentNotFoundException")
        void getDocumentById_NotFound_ThrowsException() {
            assertThatThrownBy(() -> documentService.getDocumentById(999L))
                    .isInstanceOf(DocumentNotFoundException.class)
                    .hasMessageContaining("Документ с id 999 не найден");
        }

        @Test
        @DisplayName("Пакетное получение документов по списку ID должно возвращать только существующие")
        void getDocumentsByIds_ReturnsExistingDocuments() {
            //arrange
            List<Long> ids = List.of(savedDocuments.get(0).getId(), savedDocuments.get(1).getId(), 999L);
            PageRequest pageRequest = PageRequest.of(0, 10, Sort.by("createdAt").descending());

            //act
            Page<DocumentResponse> page = documentService.getDocumentsByIds(ids, pageRequest);

            //assert
            assertThat(page.getContent()).hasSize(2);
            assertThat(page.getContent()).extracting(DocumentResponse::getId)
                    .containsExactlyInAnyOrder(savedDocuments.get(0).getId(), savedDocuments.get(1).getId());
        }

        @Test
        @DisplayName("Поиск документов по фильтрам должен работать корректно")
        void searchDocuments_WithFilters_ReturnsFilteredResults() {
            //act - поиск по статусу
            Page<DocumentResponse> byStatus = documentService.searchDocuments(
                    DocumentStatus.DRAFT, null, null, null, PageRequest.of(0, 10));

            //assert
            assertThat(byStatus.getContent()).hasSize(1);
            assertThat(byStatus.getContent().get(0).getStatus()).isEqualTo(DocumentStatus.DRAFT);

            //act -поиск по имени
            Page<DocumentResponse> byAuthor = documentService.searchDocuments(
                    null, "Author 1", null, null, PageRequest.of(0, 10));

            //assert
            assertThat(byAuthor.getContent()).hasSize(2);
            assertThat(byAuthor.getContent()).allMatch(doc -> doc.getAuthor().equals("Author 1"));
        }
    }

    @Nested
    @DisplayName("Тесты отправки на согласование - SUBMIT")
    class SubmitDocumentsTests {

        private Document draftDocument;
        private Document submittedDocument;

        @BeforeEach
        void setUp() {
            draftDocument = documentRepository.save(Document.builder()
                    .uniqueNumber("DOC-DRAFT-001")
                    .author("Author")
                    .title("Draft Title")
                    .status(DocumentStatus.DRAFT)
                    .build());

            submittedDocument = documentRepository.save(Document.builder()
                    .uniqueNumber("DOC-SUBMITTED-001")
                    .author("Author")
                    .title("Submitted Title")
                    .status(DocumentStatus.SUBMITTED)
                    .build());
        }

        @Test
        @DisplayName("Пакетный submit для валидного документа должен перевести его в SUBMITTED и создать историю")
        void submitDocuments_ValidDocument_ChangesStatusAndCreatesHistory() {
            //act
            List<BatchOperationResult> results = documentService.submitDocuments(
                    List.of(draftDocument.getId()), "test-initiator");

            //assert
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getStatus()).isEqualTo(BatchOperationResult.Status.SUCCESS);

            //Проверка изменений в БД в отдельной транзакции
            transactionTemplate.execute(status -> {
                Document updated = documentRepository.findById(draftDocument.getId()).orElseThrow();
                assertThat(updated.getStatus()).isEqualTo(DocumentStatus.SUBMITTED);

                List<DocumentStatusHistory> history = historyRepository.findByDocumentId(draftDocument.getId());
                assertThat(history).hasSize(1);
                assertThat(history.get(0).getAction()).isEqualTo(DocumentAction.SUBMIT);
                assertThat(history.get(0).getInitiator()).isEqualTo("test-initiator");

                return null;
            });
        }

        @Test
        @DisplayName("Пакетный submit для документа в неверном статусе должен вернуть CONFLICT")
        void submitDocuments_InvalidStatus_ReturnsConflict() {
            //act
            List<BatchOperationResult> results = documentService.submitDocuments(
                    List.of(submittedDocument.getId()), "test-initiator");

            //assert
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getStatus()).isEqualTo(BatchOperationResult.Status.CONFLICT);

            //Проверяем, что статус не изменился
            transactionTemplate.execute(status -> {
                Document unchanged = documentRepository.findById(submittedDocument.getId()).orElseThrow();
                assertThat(unchanged.getStatus()).isEqualTo(DocumentStatus.SUBMITTED);
                return null;
            });
        }

        @Test
        @DisplayName("Пакетный submit для несуществующего документа должен вернуть NOT_FOUND")
        void submitDocuments_NotFound_ReturnsNotFound() {
            //act
            List<BatchOperationResult> results = documentService.submitDocuments(
                    List.of(999L), "test-initiator");

            //assert
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getStatus()).isEqualTo(BatchOperationResult.Status.NOT_FOUND);
        }

        @Test
        @DisplayName("Пакетный submit для нескольких документов должен возвращать частичные результаты")
        void submitDocuments_MultipleDocuments_ReturnsPartialResults() {
            //act
            List<BatchOperationResult> results = documentService.submitDocuments(
                    List.of(draftDocument.getId(), submittedDocument.getId(), 999L),
                    "test-initiator");

            //assert
            assertThat(results).hasSize(3);

            //успех
            assertThat(results.get(0).getDocumentId()).isEqualTo(draftDocument.getId());
            assertThat(results.get(0).getStatus()).isEqualTo(BatchOperationResult.Status.SUCCESS);

            //конфликт
            assertThat(results.get(1).getDocumentId()).isEqualTo(submittedDocument.getId());
            assertThat(results.get(1).getStatus()).isEqualTo(BatchOperationResult.Status.CONFLICT);

            //не найден
            assertThat(results.get(2).getDocumentId()).isEqualTo(999L);
            assertThat(results.get(2).getStatus()).isEqualTo(BatchOperationResult.Status.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Тесты утверждения документов -APPROVE")
    class ApproveDocumentsTests {

        private Document submittedDocument;
        private Document draftDocument;

        @BeforeEach
        void setUp() {
            submittedDocument = documentRepository.save(Document.builder()
                    .uniqueNumber("DOC-APPROVE-001")
                    .author("Author")
                    .title("To Approve")
                    .status(DocumentStatus.SUBMITTED)
                    .build());

            draftDocument = documentRepository.save(Document.builder()
                    .uniqueNumber("DOC-APPROVE-002")
                    .author("Author")
                    .title("Not Submitted")
                    .status(DocumentStatus.DRAFT)
                    .build());
        }

        @Test
        @DisplayName("Утверждение документа должно перевести его в APPROVED, создать историю и запись в реестре")
        void approveDocuments_ValidDocument_CreatesAllRecords() {
            //act
            List<BatchOperationResult> results = documentService.approveDocuments(
                    List.of(submittedDocument.getId()), "approver", "Approval comment");

            //assert
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getStatus()).isEqualTo(BatchOperationResult.Status.SUCCESS);

            //Проверка всех изменений в одной транзакции
            transactionTemplate.execute(status -> {
                Document approved = documentRepository.findById(submittedDocument.getId()).orElseThrow();
                assertThat(approved.getStatus()).isEqualTo(DocumentStatus.APPROVED);

                //Проверка истории
                List<DocumentStatusHistory> history = historyRepository.findByDocumentId(submittedDocument.getId());
                assertThat(history).hasSize(1);
                assertThat(history.get(0).getAction()).isEqualTo(DocumentAction.APPROVE);
                assertThat(history.get(0).getInitiator()).isEqualTo("approver");
                assertThat(history.get(0).getComment()).isEqualTo("Approval comment");

                //Проверка реестра
                ApprovalRegistry registry = registryRepository.findByDocumentId(submittedDocument.getId()).orElse(null);
                assertThat(registry).isNotNull();
                assertThat(registry.getApprovedBy()).isEqualTo("approver");
                assertThat(registry.getApprovedAt()).isNotNull();

                return null;
            });
        }

        @Test
        @DisplayName("Повторное утверждение документа должно вернуть CONFLICT и не создавать дубликатов в реестре")
        void approveDocuments_AlreadyApproved_ReturnsConflict() {
            //arrange - сначала утверждаем документ
            documentService.approveDocuments(List.of(submittedDocument.getId()), "approver1", null);

            //act - пробуем утвердить снова
            List<BatchOperationResult> results = documentService.approveDocuments(
                    List.of(submittedDocument.getId()), "approver2", "Second attempt");

            // assert
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getStatus()).isEqualTo(BatchOperationResult.Status.CONFLICT);

            // Проверяем, что в реестре только одна запись
            transactionTemplate.execute(status -> {
                Long registryCount = registryRepository.countByDocumentId(submittedDocument.getId());
                assertThat(registryCount).isEqualTo(1);
                return null;
            });
        }

        @Test
        @DisplayName("Утверждение документа с ошибкой записи в реестр должно откатить всю транзакцию")
        void approveDocuments_RegistryError_RollsBackTransaction() {
            //act - первый раз успешно
            documentService.approveDocuments(List.of(submittedDocument.getId()), "approver", null);

            //act - второй раз с комментарием
            List<BatchOperationResult> results = documentService.approveDocuments(
                    List.of(submittedDocument.getId()), "approver2", "Should not change");

            //assert
            assertThat(results.get(0).getStatus()).isEqualTo(BatchOperationResult.Status.CONFLICT);

            //Проверка, что updatedAt не изменился, т е не было реального обновления
            transactionTemplate.execute(status -> {
                Document doc = documentRepository.findById(submittedDocument.getId()).orElseThrow();
                assertThat(doc.getStatus()).isEqualTo(DocumentStatus.APPROVED);

                //Проверяем, что история содержит только одну запись APPROVE
                List<DocumentStatusHistory> history = historyRepository.findByDocumentId(submittedDocument.getId());
                assertThat(history).hasSize(1);
                assertThat(history.get(0).getInitiator()).isEqualTo("approver");

                return null;
            });
        }

        @Test
        @DisplayName("Утверждение документа с невалидным статусом должно вернуть CONFLICT")
        void approveDocuments_InvalidStatus_ReturnsConflict() {
            //act
            List<BatchOperationResult> results = documentService.approveDocuments(
                    List.of(draftDocument.getId()), "approver", null);

            //assert
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getStatus()).isEqualTo(BatchOperationResult.Status.CONFLICT);

            //Проверяем, что статус не изменился и реестр пуст
            transactionTemplate.execute(status -> {
                Document doc = documentRepository.findById(draftDocument.getId()).orElseThrow();
                assertThat(doc.getStatus()).isEqualTo(DocumentStatus.DRAFT);

                Long registryCount = registryRepository.countByDocumentId(draftDocument.getId());
                assertThat(registryCount).isZero();

                return null;
            });
        }
    }

    @Nested
    @DisplayName("Тест целостности данных")
    class TransactionalTests {

        @Test
        @DisplayName("При ошибке в approveDocumentAtomically внутри транзакции все изменения должны откатиться")
        void approveDocumentAtomically_WhenRegistryFails_RollsBack() {
            //arrange
            Document document = documentRepository.save(Document.builder()
                    .uniqueNumber("DOC-ROLLBACK-001")
                    .author("Author")
                    .title("Rollback Test")
                    .status(DocumentStatus.SUBMITTED)
                    .build());

            //act&assert - попытка выполнить утверждение в транзакции с ошибкой
            assertThatThrownBy(() -> {
                transactionTemplate.execute(status -> {
                    try {
                        //Проверка, что при DataIntegrityViolationException
                        //например, что при duplicate key все откатывается
                        Document doc = documentRepository.findById(document.getId()).orElseThrow();
                        doc.setStatus(DocumentStatus.APPROVED);
                        documentRepository.save(doc);

                        //Симулируем ошибку - сохраняем дубликат в реестре
                        ApprovalRegistry registry = ApprovalRegistry.createForDocument(doc, "approver");
                        registryRepository.save(registry);
                        registryRepository.flush(); //первый раз ок

                        //Пробуем сохранить еще раз - будет DataIntegrityViolationException из-за unique constraint
                        ApprovalRegistry duplicate = ApprovalRegistry.createForDocument(doc, "another-approver");
                        registryRepository.save(duplicate);
                        registryRepository.flush(); //а здесь исключение

                        return null;
                    } catch (Exception e) {
                        status.setRollbackOnly();
                        throw e;
                    }
                });
            }).isInstanceOf(DataIntegrityViolationException.class);

            //Проверка, что документ не изменился
            transactionTemplate.execute(status -> {
                Document unchanged = documentRepository.findById(document.getId()).orElseThrow();
                assertThat(unchanged.getStatus()).isEqualTo(DocumentStatus.SUBMITTED);

                List<ApprovalRegistry> registries = registryRepository.findAll();
                assertThat(registries).isEmpty();

                return null;
            });
        }
    }
}