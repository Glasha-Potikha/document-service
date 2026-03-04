package com.itq.document_service.exception;

import com.itq.document_service.model.enums.DocumentAction;
import com.itq.document_service.model.enums.DocumentStatus;

public class InvalidStatusTransitionException extends DocumentServiceException {
    public InvalidStatusTransitionException(Long id, DocumentStatus current, DocumentAction action) {
        super("DOC-002",
                String.format("Недопустимый переход для документа %d: %s -> %s",
                        id, current, action.getTargetStatus())
        );
    }
}
