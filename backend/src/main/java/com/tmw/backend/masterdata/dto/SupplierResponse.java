package com.tmw.backend.masterdata.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tmw.backend.masterdata.entity.Supplier;

import java.time.Instant;
import java.util.UUID;

public record SupplierResponse(
        UUID id,
        String code,
        String name,
        @JsonProperty("tax_code") String taxCode,
        String address,
        @JsonProperty("created_at") Instant createdAt
) {
    public static SupplierResponse from(Supplier s) {
        if (s == null) {
            return null;
        }
        return new SupplierResponse(
                s.getId(),
                s.getCode(),
                s.getName(),
                s.getTaxCode(),
                s.getAddress(),
                s.getCreatedAt()
        );
    }
}