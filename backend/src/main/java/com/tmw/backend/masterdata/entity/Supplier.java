package com.tmw.backend.masterdata.entity;

import com.tmw.backend.common.entity.BaseEntity;
import com.tmw.backend.common.error.BusinessException;
import com.tmw.backend.common.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Locale;
import java.util.regex.Pattern;

@Entity
@Table(name = "supplier", uniqueConstraints = {
        @UniqueConstraint(name = "uk_supplier_code", columnNames = "code"),
        @UniqueConstraint(name = "uk_supplier_tax_code", columnNames = "tax_code")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Supplier extends BaseEntity {

    private static final Pattern TAX_CODE_PATTERN = Pattern.compile("^[0-9]{10}(-[0-9]{3})?$");

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "tax_code", nullable = false, length = 20)
    private String taxCode;

    @Column(length = 500)
    private String address;

    @Version
    @Column(nullable = false)
    private Integer version;

    public Supplier(String code, String name, String taxCode, String address) {
        String normalizedCode = requireText(code, "code").toUpperCase(Locale.ROOT);
        String normalizedName = requireText(name, "name");
        String normalizedTaxCode = requireText(taxCode, "tax_code");

        if (!TAX_CODE_PATTERN.matcher(normalizedTaxCode).matches()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Mã số thuế không đúng định dạng (10 chữ số hoặc 10 chữ số kèm mã chi nhánh -XXX): " + normalizedTaxCode
            );
        }

        this.code = normalizedCode;
        this.name = normalizedName;
        this.taxCode = normalizedTaxCode;
        this.address = (address == null || address.isBlank()) ? null : address.trim();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trường " + fieldName + " không được để trống");
        }
        return value.trim();
    }
}