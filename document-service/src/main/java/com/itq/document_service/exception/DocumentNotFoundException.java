package com.itq.document_service.exception;

public class DocumentNotFoundException extends DocumentServiceException {
    public DocumentNotFoundException(Long id) {
        super("DOC-001", "Документ с id " + id + " не найден");
    }
}
