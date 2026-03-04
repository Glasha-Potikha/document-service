package com.itq.document_service.exception;

public class RegistryException extends DocumentServiceException {
    public RegistryException(Long id, String cause) {
        super("REG-001",
                String.format("Ошибка регистрации документа %d в реестре: %s", id, cause));
    }
}
