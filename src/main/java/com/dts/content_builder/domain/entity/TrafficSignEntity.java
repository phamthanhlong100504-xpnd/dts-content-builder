
package com.dts.content_builder.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "traffic_signs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrafficSignEntity {

    public enum Status {
        DRAFT,
        PUBLISHED,
        ARCHIVED,
        HIDDEN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ID goc trong traffic-signs.json: 1..221
    @Column(name = "source_id", nullable = false)
    private Integer sourceId;

    // Phien ban bo du lieu
    @Column(name = "content_version", nullable = false, length = 64)
    private String bankVersion;

    // Ma bien bao, vi du P.101
    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    // cam, nguy_hiem, hieu_lenh, chi_dan, phu
    @Column(nullable = false, length = 32)
    private String category;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String meaning;

    @Column(name = "quick_tip", nullable = false, columnDefinition = "TEXT")
    private String quickTip;

    // Ten file anh nguon, khong phai MinIO URL
    @Column(name = "image_filename", nullable = false, length = 255)
    private String sourceImage;

    // UUID anh da duoc luu tren Media Service
    @Column(name = "media_file_id")
    private UUID mediaFileId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
        name = "applicable_licenses",
        nullable = false,
        columnDefinition = "jsonb"
    )
    private List<String> applicableLicenses;

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
