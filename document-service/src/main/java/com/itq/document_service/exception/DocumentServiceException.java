package com.itq.document_service.exception;

public abstract class DocumentServiceException extends RuntimeException {
    private final String code;
    private final String message;

    public DocumentServiceException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }
}
