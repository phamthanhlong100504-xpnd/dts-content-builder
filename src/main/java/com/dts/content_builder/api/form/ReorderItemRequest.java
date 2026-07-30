package com.dts.content_builder.api.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReorderItemRequest {

    @NotNull(message = "Option ID is required")
    private UUID id;

    @NotNull(message = "Sort order is required")
    @Min(value = 0, message = "Sort order must be >= 0")
    private Integer sortOrder;
}
