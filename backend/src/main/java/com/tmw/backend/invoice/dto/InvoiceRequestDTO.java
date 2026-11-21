package com.tmw.backend.invoice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class InvoiceRequestDTO {
    
    private UUID supplierId;
    private String invoiceNumber;
    private LocalDate issueDate;
    private String currency;
    private BigDecimal taxAmount;
    private List<InvoiceLineDTO> lines;

    // Getters and Setters
    public UUID getSupplierId() { return supplierId; }
    public void setSupplierId(UUID supplierId) { this.supplierId = supplierId; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }

    public List<InvoiceLineDTO> getLines() { return lines; }
    public void setLines(List<InvoiceLineDTO> lines) { this.lines = lines; }
    
    public static class InvoiceLineDTO {
        private UUID poLineId;
        private UUID itemId;
        private Integer quantity;
        private BigDecimal unitPrice;

        public UUID getPoLineId() { return poLineId; }
        public void setPoLineId(UUID poLineId) { this.poLineId = poLineId; }

        public UUID getItemId() { return itemId; }
        public void setItemId(UUID itemId) { this.itemId = itemId; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    }
}