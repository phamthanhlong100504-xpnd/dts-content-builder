
package com.dts.content_builder.api.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTrafficSignRequest {

    @NotNull(message = "Source ID is required")
    @Min(value = 1, message = "Source ID must be positive")
    private Integer sourceId;

    @NotBlank(message = "Bank version is required")
    @Size(max = 64)
    private String bankVersion;

    @NotBlank(message = "Sign code is required")
    @Size(max = 32)
    private String code;

    @NotBlank(message = "Sign name is required")
    @Size(max = 255)
    private String name;

    @NotBlank(message = "Category is required")
    @Pattern(
        regexp = "cam|nguy_hiem|hieu_lenh|chi_dan|phu",
        message = "Invalid traffic sign category"
    )
    private String category;

    @NotBlank(message = "Category name is required")
    @Size(max = 100)
    private String categoryName;

    @NotBlank(message = "Meaning is required")
    private String meaning;

    @NotBlank(message = "Quick tip is required")
    private String quickTip;

    @NotBlank(message = "Source image is required")
    @Size(max = 255)
    private String sourceImage;

    // Co the null khi tao DRAFT.
    // Service se bat buoc co Media UUID khi PUBLISHED.
    private UUID mediaFileId;

    @NotEmpty(message = "Applicable licenses are required")
    private List<@NotBlank String> applicableLicenses;
}
