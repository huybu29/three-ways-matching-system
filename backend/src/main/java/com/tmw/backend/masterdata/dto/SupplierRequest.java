package com.tmw.backend.masterdata.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tmw.backend.common.validation.ValidTaxCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierRequest(
        @NotBlank(message = "Mã nhà cung cấp không được để trống")
        @Size(max = 50, message = "Mã nhà cung cấp tối đa 50 ký tự")
        String code,

        @NotBlank(message = "Tên nhà cung cấp không được để trống")
        @Size(max = 255, message = "Tên nhà cung cấp tối đa 255 ký tự")
        String name,

        @JsonProperty("tax_code")
        @NotBlank(message = "Mã số thuế không được để trống")
        @ValidTaxCode
        String taxCode,

        @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
        String address
) {
}