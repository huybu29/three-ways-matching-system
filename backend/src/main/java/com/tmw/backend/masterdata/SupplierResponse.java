package com.tmw.backend.masterdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Supplier trong API contract. */
public record SupplierResponse(
        UUID id,
        String code,
        String name,
        @JsonProperty("tax_code") String taxCode,
        String address,
        @JsonProperty("created_at") OffsetDateTime createdAt) {

    public static SupplierResponse from(Supplier s) {
        return new SupplierResponse(s.getId(), s.getCode(), s.getName(), s.getTaxCode(), s.getAddress(),
                s.getCreatedAt());
    }
}
