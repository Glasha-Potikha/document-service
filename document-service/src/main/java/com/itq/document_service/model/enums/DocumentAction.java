package com.itq.document_service.model.enums;

public enum DocumentAction {
    SUBMIT("Передача на согласование"),
    APPROVE("Утверждение");

    private final String description;

    DocumentAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    //маппинг действия к статусу
    public DocumentStatus getTargetStatus() {
        return switch (this) {
            case SUBMIT -> DocumentStatus.SUBMITTED;
            case APPROVE -> DocumentStatus.APPROVED;
        };
    }
}
