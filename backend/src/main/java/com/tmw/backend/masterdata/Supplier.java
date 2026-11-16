package com.tmw.backend.masterdata;

import backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "supplier", uniqueConstraints = {
        @UniqueConstraint(name = "uk_supplier_code", columnNames = "code"),
        @UniqueConstraint(name = "uk_supplier_tax_code", columnNames = "tax_code")
})
public class Supplier extends BaseEntity {

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "tax_code", nullable = false, length = 14)
    private String taxCode;

    private String address;

    protected Supplier() {
    }

    public Supplier(String code, String name, String taxCode, String address) {
        this.code = code;
        this.name = name;
        this.taxCode = taxCode;
        this.address = address;
    }

    public String getCode() { return code; }

    public String getName() { return name; }

    public String getTaxCode() { return taxCode; }

    public String getAddress() { return address; }
}
