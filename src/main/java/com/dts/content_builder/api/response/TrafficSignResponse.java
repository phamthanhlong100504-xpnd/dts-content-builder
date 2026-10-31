
package com.dts.content_builder.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrafficSignResponse {

    // UUID cua ban ghi trong Content Builder
    private UUID id;

    // ID goc cua bien bao: 1..221
    private Integer sourceId;

    // Phien ban bo du lieu
    private String bankVersion;

    // Thong tin bien bao
    private String code;
    private String name;
    private String category;
    private String categoryName;

    // Noi dung hoc tap
    private String meaning;
    private String quickTip;

    // Thong tin hinh anh
    private String sourceImage;
    private UUID mediaFileId;

    // Cac hang giay phep lai xe ap dung
    private List<String> applicableLicenses;

    // DRAFT, PUBLISHED, ARCHIVED, HIDDEN
    private String status;

    // Thong tin xuat ban
    private LocalDateTime publishedAt;
}
