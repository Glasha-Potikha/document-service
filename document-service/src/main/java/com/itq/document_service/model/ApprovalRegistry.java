package com.itq.document_service.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "approval_registry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"document"})
@EqualsAndHashCode(exclude = {"document"})
public class ApprovalRegistry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    public ApprovalRegistry(Document document, String approvedBy, LocalDateTime approvedAt) {
        this.document = document;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
    }

    public static ApprovalRegistry createForDocument(Document document, String approvedBy) {
        return new ApprovalRegistry(document, approvedBy, LocalDateTime.now());
    }
}
