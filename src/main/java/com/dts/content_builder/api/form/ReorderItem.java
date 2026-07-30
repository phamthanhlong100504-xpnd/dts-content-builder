package com.dts.content_builder.api.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ReorderItem {
    @NotNull
    private UUID id;

    @NotNull
    @Min(0)
    private Integer sortOrder;
}
