
package com.dts.content_builder.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryTipResponse {

    // UUID cua ban ghi trong Content Builder
    private UUID id;

    // ID goc trong memory-tips.json: 1..12
    private Integer sourceId;

    // Phien ban bo du lieu
    private String bankVersion;

    // Thu tu hien thi tren frontend
    private Integer sortOrder;

    // Noi dung meo ghi nho
    private String title;
    private String quickRule;
    private String explanation;

    // DRAFT, PUBLISHED, ARCHIVED, HIDDEN
    private String status;

    // Thong tin xuat ban
    private LocalDateTime publishedAt;
}
