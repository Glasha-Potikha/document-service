package com.itq.document_service.model;

import com.itq.document_service.model.enums.DocumentAction;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"document"})
@EqualsAndHashCode(exclude = {"document"})
public class DocumentStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    private String initiator;

    @Enumerated(EnumType.STRING)
    private DocumentAction action;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public DocumentStatusHistory(Document document, String initiator, DocumentAction action, String comment, LocalDateTime createdAt) {
        this.document = document;
        this.initiator = initiator;
        this.action = action;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public static DocumentStatusHistory createHistory(Document document, String initiator, DocumentAction action, String comment) {
        return new DocumentStatusHistory(document, initiator, action, comment, LocalDateTime.now());
    }

}
