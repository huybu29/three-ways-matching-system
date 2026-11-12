package com.tmw.backend.common.pagination;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PageMeta {
    private boolean hasNextPage;
    private boolean hasPreviousPage;
    
    // Con trỏ để gọi trang tiếp theo hoặc trang trước đó
    private String nextCursor;
    private String previousCursor;
    
    // Tổng số phần tử (Tùy chọn, vì cursor pagination đôi khi không cần đếm tổng số để tối ưu hiệu năng)
    private Long totalElements; 
}