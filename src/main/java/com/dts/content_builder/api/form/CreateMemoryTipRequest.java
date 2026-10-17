
package com.dts.content_builder.api.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMemoryTipRequest {

    // ID goc trong memory-tips.json
    @NotNull(message = "Source ID is required")
    @Min(value = 1, message = "Source ID must be positive")
    private Integer sourceId;

    // Phien ban du lieu
    @NotBlank(message = "Bank version is required")
    @Size(max = 64)
    private String bankVersion;

    // Thu tu hien thi tren frontend
    @NotNull(message = "Sort order is required")
    @Min(value = 1, message = "Sort order must be positive")
    private Integer sortOrder;

    // Tieu de meo ghi nho
    @NotBlank(message = "Title is required")
    @Size(max = 255)
    private String title;

    // Quy tac ghi nho nhanh
    @NotBlank(message = "Quick rule is required")
    private String quickRule;

    // Noi dung giai thich chi tiet
    @NotBlank(message = "Explanation is required")
    private String explanation;
}
