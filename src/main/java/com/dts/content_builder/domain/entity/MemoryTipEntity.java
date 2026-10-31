
package com.dts.content_builder.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "memory_tips")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemoryTipEntity {

    public enum Status {
        DRAFT,
        PUBLISHED,
        ARCHIVED,
        HIDDEN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ID goc trong memory-tips.json: 1..12
    @Column(name = "source_id", nullable = false)
    private Integer sourceId;

    // Phien ban bo du lieu
    @Column(name = "content_version", nullable = false, length = 64)
    private String bankVersion;

    // Thu tu hien thi theo truong "order" trong JSON
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(nullable = false, length = 255)
    private String title;

    // Quy tac ghi nho ngan gon
    @Column(name = "quick_rule", nullable = false, columnDefinition = "TEXT")
    private String quickRule;

    // Giai thich chi tiet
    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.DRAFT;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        updatedAt = now;

        if (status == null) {
            status = Status.DRAFT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
