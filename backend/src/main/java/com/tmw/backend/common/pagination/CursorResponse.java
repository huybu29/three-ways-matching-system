package com.tmw.backend.common.pagination;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CursorResponse<T> {
    
    // Danh sách dữ liệu trả về (T đại diện cho mọi loại DTO như InvoiceDTO, MatchingDTO, v.v...)
    private List<T> data;
    
    // Thông tin phân trang
    private PageMeta meta;
}