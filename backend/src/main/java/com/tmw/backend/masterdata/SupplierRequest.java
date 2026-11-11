package com.tmw.backend.masterdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Body của POST /suppliers (SupplierInput trong API contract). */
public record SupplierRequest(
        @NotBlank String code,
        @NotBlank String name,
        @JsonProperty("tax_code")
        @NotBlank
        @Pattern(regexp = "^[0-9]{10}(-[0-9]{3})?$", message = "Mã số thuế phải gồm 10 số, hoặc 10 số + '-' + 3 số")
        String taxCode,
        String address) {
}
