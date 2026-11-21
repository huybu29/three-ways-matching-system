package com.tmw.backend.invoice.controller;

import com.tmw.backend.invoice.dto.InvoiceRequestDTO;
import com.tmw.backend.invoice.service.InvoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    @Autowired
    private InvoiceService invoiceService;

    @PostMapping
    public ResponseEntity<String> createInvoice(@RequestBody InvoiceRequestDTO request) {
        // Gọi Service để tự động tính tổng tiền từ các dòng chi tiết
        BigDecimal totalAmount = invoiceService.calculateTotalAmount(request);
        
        // Tạm thời trả về câu thông báo để chúng ta test thử
        return ResponseEntity.ok("Đã nhận được request! Tổng tiền Backend tự tính là: " + totalAmount);
    }
}