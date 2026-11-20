package com.tmw.backend.testinfra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.tmw.backend.common.enums.CurrencyCode;
import com.tmw.backend.invoice.entity.Invoice;
import com.tmw.backend.invoice.entity.InvoiceLine;
import com.tmw.backend.masterdata.entity.Supplier;
import com.tmw.backend.masterdata.entity.TolerancePolicy;
import com.tmw.backend.purchaseorder.entity.PurchaseOrder;
import com.tmw.backend.purchaseorder.entity.PurchaseOrderLine;

public final class TestFixtures {

    private static final AtomicLong SEQ = new AtomicLong(1);

    private TestFixtures() {
    }

    public static long nextSeq() {
        return SEQ.getAndIncrement();
    }

    public static String nextTaxCode() {
        // Luôn sinh đúng 10 chữ số hợp lệ (từ 1000000001 trở đi)
        return String.format("%010d", 1_000_000_000L + nextSeq());
    }

    public static SupplierBuilder supplier() {
        return new SupplierBuilder();
    }

    public static TolerancePolicyBuilder tolerancePolicy() {
        return new TolerancePolicyBuilder();
    }

    public static PurchaseOrderBuilder purchaseOrder() {
        return new PurchaseOrderBuilder();
    }

    public static InvoiceBuilder invoice() {
        return new InvoiceBuilder();
    }

    // ==================== 1. SUPPLIER BUILDER ====================

    public static final class SupplierBuilder {
        private String code;
        private String name;
        private String taxCode;
        private String address = "123 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh";

        private SupplierBuilder() {
            long seq = nextSeq();
            this.code = String.format("SUP-%05d", seq);
            this.name = "Công ty TNHH Nhà Cung Cấp " + seq;
            this.taxCode = nextTaxCode();
        }

        public SupplierBuilder code(String code) {
            this.code = code;
            return this;
        }

        public SupplierBuilder name(String name) {
            this.name = name;
            return this;
        }

        public SupplierBuilder taxCode(String taxCode) {
            this.taxCode = taxCode;
            return this;
        }

        public SupplierBuilder address(String address) {
            this.address = address;
            return this;
        }

        public Supplier build() {
            return new Supplier(code, name, taxCode, address);
        }
    }

    // ==================== 2. TOLERANCE POLICY BUILDER ====================

    public static final class TolerancePolicyBuilder {
        private String name = "Policy Test " + nextSeq();
        private Supplier supplier;
        private String itemCategory;
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private BigDecimal qtyTolerancePct = new BigDecimal("2.0000");
        private BigDecimal priceTolerancePct = new BigDecimal("1.0000");
        private BigDecimal amountToleranceAbs = new BigDecimal("10000.0000");
        private boolean requireGrn = true;
        private boolean isDefault = false;

        public TolerancePolicyBuilder name(String name) {
            this.name = name;
            return this;
        }

        public TolerancePolicyBuilder supplier(Supplier supplier) {
            this.supplier = supplier;
            return this;
        }

        public TolerancePolicyBuilder itemCategory(String itemCategory) {
            this.itemCategory = itemCategory;
            return this;
        }

        public TolerancePolicyBuilder amountRange(String min, String max) {
            this.minAmount = min != null ? new BigDecimal(min) : null;
            this.maxAmount = max != null ? new BigDecimal(max) : null;
            return this;
        }

        public TolerancePolicyBuilder qtyTolerancePct(String pct) {
            this.qtyTolerancePct = new BigDecimal(pct);
            return this;
        }

        public TolerancePolicyBuilder priceTolerancePct(String pct) {
            this.priceTolerancePct = new BigDecimal(pct);
            return this;
        }

        public TolerancePolicyBuilder amountToleranceAbs(String abs) {
            this.amountToleranceAbs = new BigDecimal(abs);
            return this;
        }

        public TolerancePolicyBuilder requireGrn(boolean requireGrn) {
            this.requireGrn = requireGrn;
            return this;
        }

        public TolerancePolicyBuilder asDefault(boolean isDefault) {
            this.isDefault = isDefault;
            return this;
        }

        public TolerancePolicy build() {
            return new TolerancePolicy(
                    name,
                    supplier,
                    itemCategory,
                    minAmount,
                    maxAmount,
                    qtyTolerancePct,
                    priceTolerancePct,
                    amountToleranceAbs,
                    requireGrn,
                    isDefault
            );
        }
    }

    // ==================== 3. PURCHASE ORDER BUILDER ====================

    public static final class PurchaseOrderBuilder {
        private String poNumber = String.format("PO-2026-%05d", nextSeq());
        private UUID supplierId;
        private String currency = "VND";
        private LocalDate orderDate = LocalDate.now();
        private LocalDate expectedDeliveryDate;
        private final List<PoLineSpec> lines = new ArrayList<>();

        public PurchaseOrderBuilder poNumber(String poNumber) {
            this.poNumber = poNumber;
            return this;
        }

        public PurchaseOrderBuilder supplierId(UUID supplierId) {
            this.supplierId = supplierId;
            return this;
        }

        public PurchaseOrderBuilder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public PurchaseOrderBuilder orderDate(LocalDate orderDate) {
            this.orderDate = orderDate;
            return this;
        }

        public PurchaseOrderBuilder expectedDeliveryDate(LocalDate expectedDeliveryDate) {
            this.expectedDeliveryDate = expectedDeliveryDate;
            return this;
        }

        public PurchaseOrderBuilder addLine(String itemCode, String itemName, String qty, String unitPrice, String vatRate) {
            int lineNo = lines.size() + 1;
            lines.add(new PoLineSpec(
                    lineNo,
                    itemCode,
                    itemName,
                    new BigDecimal(qty),
                    new BigDecimal(unitPrice),
                    new BigDecimal(vatRate)
            ));
            return this;
        }

        public PurchaseOrder build() {
            if (supplierId == null) {
                throw new IllegalStateException("supplierId bắt buộc phải được gán trước khi build PurchaseOrder");
            }
            if (lines.isEmpty()) {
                addLine("ITEM-001", "Vật tư tiêu chuẩn", "100.0000", "50000.0000", "0.1000");
            }

            com.tmw.backend.purchaseorder.CurrencyCode poCurrency =
                    com.tmw.backend.purchaseorder.CurrencyCode.valueOf(currency.trim().toUpperCase(Locale.ROOT));
            PurchaseOrder po = new PurchaseOrder(poNumber, supplierId, orderDate, poCurrency);
            for (PoLineSpec spec : lines) {
                PurchaseOrderLine line = new PurchaseOrderLine(
                        spec.lineNo(),
                        spec.itemCode(),
                        spec.itemName(),
                        spec.orderedQty(),
                        spec.unitPrice(),
                        spec.vatRate()
                );
                po.addLine(line);
            }
            return po;
        }

        private record PoLineSpec(
                int lineNo,
                String itemCode,
                String itemName,
                BigDecimal orderedQty,
                BigDecimal unitPrice,
                BigDecimal vatRate
        ) {
        }
    }

    // ==================== 4. INVOICE BUILDER ====================

    public static final class InvoiceBuilder {
        private UUID supplierId;
        private UUID poId;
        private String supplierTaxCode = nextTaxCode();
        private String invoiceSeries = "1C26TAA";
        private String invoiceNumber = String.format("%07d", nextSeq());
        private LocalDate issueDate = LocalDate.now();
        private String currency = "VND";
        private UUID createdBy = UUID.randomUUID();
        private final List<InvoiceLineSpec> lines = new ArrayList<>();

        public InvoiceBuilder supplier(Supplier supplier) {
            this.supplierId = supplier.getId();
            this.supplierTaxCode = supplier.getTaxCode();
            return this;
        }

        public InvoiceBuilder supplierId(UUID supplierId) {
            this.supplierId = supplierId;
            return this;
        }

        public InvoiceBuilder poId(UUID poId) {
            this.poId = poId;
            return this;
        }

        public InvoiceBuilder supplierTaxCode(String supplierTaxCode) {
            this.supplierTaxCode = supplierTaxCode;
            return this;
        }

        public InvoiceBuilder invoiceSeries(String invoiceSeries) {
            this.invoiceSeries = invoiceSeries;
            return this;
        }

        public InvoiceBuilder invoiceNumber(String invoiceNumber) {
            this.invoiceNumber = invoiceNumber;
            return this;
        }

        public InvoiceBuilder issueDate(LocalDate issueDate) {
            this.issueDate = issueDate;
            return this;
        }

        public InvoiceBuilder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public InvoiceBuilder createdBy(UUID createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public InvoiceBuilder addLine(String itemCode, String itemName, String qty, String unitPrice, String vatRate) {
            return addLine(null, itemCode, itemName, qty, unitPrice, vatRate);
        }

        public InvoiceBuilder addLine(UUID poLineId, String itemCode, String itemName, String qty, String unitPrice, String vatRate) {
            int lineNo = lines.size() + 1;
            lines.add(new InvoiceLineSpec(
                    lineNo,
                    poLineId,
                    itemCode,
                    itemName,
                    new BigDecimal(qty),
                    new BigDecimal(unitPrice),
                    new BigDecimal(vatRate)
            ));
            return this;
        }

        public Invoice build() {
            if (supplierId == null) {
                throw new IllegalStateException("supplierId bắt buộc phải được gán trước khi build Invoice");
            }
            if (lines.isEmpty()) {
                addLine("ITEM-001", "Vật tư tiêu chuẩn", "100.0000", "50000.0000", "0.1000");
            }

            CurrencyCode currencyCode = CurrencyCode.valueOf(currency.trim().toUpperCase(Locale.ROOT));
            Invoice invoice = new Invoice(
                    supplierId,
                    poId,
                    invoiceSeries,
                    invoiceNumber,
                    issueDate,
                    currencyCode,
                    createdBy
            );

            for (InvoiceLineSpec spec : lines) {
                InvoiceLine line = new InvoiceLine(
                        spec.lineNo(),
                        spec.poLineId(),
                        spec.itemCode(),
                        spec.itemName(),
                        spec.qty(),
                        spec.unitPrice(),
                        spec.vatRate()
                );
                invoice.addLine(line);
            }
            return invoice;
        }

        private record InvoiceLineSpec(
                int lineNo,
                UUID poLineId,
                String itemCode,
                String itemName,
                BigDecimal qty,
                BigDecimal unitPrice,
                BigDecimal vatRate
        ) {
        }
    }
}