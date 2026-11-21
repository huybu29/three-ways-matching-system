package com.tmw.backend.invoice.service;

import com.tmw.backend.invoice.dto.InvoiceRequestDTO;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class InvoiceService {

    // Hàm tự động tính tổng tiền hóa đơn (Giá = Số lượng * Đơn giá + Thuế)
    public BigDecimal calculateTotalAmount(InvoiceRequestDTO request) {
        BigDecimal total = BigDecimal.ZERO;
        
        // Tính tổng tiền của từng dòng sản phẩm
        if (request.getLines() != null) {
            for (InvoiceRequestDTO.InvoiceLineDTO line : request.getLines()) {
                BigDecimal quantity = new BigDecimal(line.getQuantity());
                BigDecimal lineTotal = line.getUnitPrice().multiply(quantity);
                total = total.add(lineTotal);
            }
        }
        
        // Cộng thêm tiền thuế
        if (request.getTaxAmount() != null) {
            total = total.add(request.getTaxAmount());
        }
        
        return total;
    }
}