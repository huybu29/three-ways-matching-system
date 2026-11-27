# Đo hiệu năng truy vấn ứng viên (BE-5, giai đoạn 2)

## Cách đo

- PostgreSQL 16. Dữ liệu tất định từ `backend/src/test/resources/matching/candidate-finder-seed-1000-po.sql`: 50 nhà cung cấp, mỗi nhà 20 PO, tổng **1.000 PO / 5.000 dòng PO**, 200 mã hàng. Trạng thái và hạn mức trộn lẫn nhau; dòng số 5 của mỗi PO chưa nhận hàng.
- Câu truy vấn: `backend/src/main/resources/sql/purchaseorder/find-candidate-po-lines.sql`, đúng câu mà `JdbcPurchaseOrderQueryService` chạy.
- Tải thử: 300 hóa đơn ngẫu nhiên (seed cố định). Mỗi hóa đơn 3 mã hàng có thật của nhà cung cấp, một nửa số hóa đơn có ghi số PO. Chạy 20 truy vấn làm nóng trước khi đo.
- Để thấy index có tác dụng khi dữ liệu lớn hơn, đo thêm với 20.000 PO / 100.000 dòng.

## Kết quả

| Dữ liệu | Index | p50 | p95 |
|---|---|---|---|
| 1.000 PO / 5.000 dòng | không | 0,48 ms | 0,65 ms |
| 1.000 PO / 5.000 dòng | có | **0,33 ms** | **0,51 ms** |
| 20.000 PO / 100.000 dòng | không | 4,12 ms | 5,68 ms |
| 20.000 PO / 100.000 dòng | có | **0,67 ms** | **0,99 ms** |

Kế hoạch thực thi khi có index (20.000 PO) đi qua index ở cả ba bước:

```
Nested Loop
  -> Nested Loop
       -> Bitmap Heap Scan on purchase_order po
            -> Bitmap Index Scan on idx_purchase_order_supplier_status_date
       -> Index Scan using idx_po_line_po_item on po_line pl
  -> Index Scan using po_line_capacity_pkey on po_line_capacity c
```

Ở mức 1.000 PO, planner vẫn quét tuần tự `po_line` (5.000 dòng) vì rẻ hơn. Khi bảng lớn lên, planner tự chuyển sang dùng `idx_po_line_po_item`.

## Index (`db/migration/R__matching_candidate_finder_indexes.sql`)

- `idx_purchase_order_supplier_status_date (supplier_id, status, order_date) INCLUDE (po_number, currency)`
- `idx_po_line_po_item (purchase_order_id, item_code)`
- `idx_po_line_item_code (item_code)`

File này là migration kiểu `R__` (repeatable): Flyway luôn chạy nó sau mọi migration `V__`, nên chạy được kể cả khi migration tạo bảng của BE-3 có timestamp muộn hơn.

## Chạy lại

```powershell
cd backend
.\mvnw.cmd test "-Dtest=CandidateFinderPerformanceIntegrationTest"   # cần Docker
```

Test in ra p50/p95 của 300 lần `CandidateFinder.find` (gồm cả phần chấm điểm) và kiểm tra:
- p95 dưới 50 ms (ngưỡng nới rộng cho CI);
- kế hoạch thực thi dùng `idx_purchase_order_supplier_status_date`;
- kết quả chỉ gồm dòng PO đang mở, đúng nhà cung cấp, còn hạn mức, sắp theo PO cũ trước.
