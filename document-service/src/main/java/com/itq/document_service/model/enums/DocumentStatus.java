package com.itq.document_service.model.enums;

public enum DocumentStatus {
    DRAFT("Черновик"),
    SUBMITTED("На согласовании"),
    APPROVED("Утвержден");

    private final String description;

    DocumentStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean canTransitionTo(DocumentStatus newStatus) {
        return switch (this) {
            case DRAFT -> newStatus == SUBMITTED;
            case SUBMITTED -> newStatus == APPROVED;
            case APPROVED -> false;
        };
    }
}
