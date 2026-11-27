# Thiết kế: phân bổ ghép một-nhiều và nhiều-một (BE-5, giai đoạn 2)

Dùng cho `POST /invoices/{invoiceId}/match-runs` ở giai đoạn 3. Đầu vào là kết quả `CandidateFinder`: với mỗi dòng hóa đơn, danh sách dòng PO ứng viên đã xếp hạng. CandidateFinder **không** chia số lượng. Việc chia nằm ở bộ phân bổ mô tả dưới đây.

## 1. Ràng buộc (mục 1.2 bản phân công v2)

| Vế | Điều kiện | Nguồn số liệu |
|---|---|---|
| PO | allocated + reserved + qty ≤ ordered | `po_line_capacity` |
| GRN (khi `require_grn`) | allocated + reserved + qty ≤ received | `po_line_capacity.received_qty` (cộng dồn mọi GRN của dòng PO) |
| Dòng hóa đơn | matched_qty + qty ≤ qty | `invoice_line` |

`referenceQty` dùng cho `VarianceCalculator`: đối chiếu 3 chiều lấy **số lượng GRN còn lại** (received − allocated − reserved), 2 chiều lấy **số lượng PO còn lại**. Khi `referenceQty = 0`, `VarianceResult.missingReferenceQty()` bật và engine sinh `MISSING_GRN`.

## 2. Thuật toán: tham lam, xác định, giải thích được

1. **Thứ tự dòng hóa đơn:** dòng ít ứng viên nhất đi trước (khó ghép nhất), rồi đến `line_no`. Nhờ vậy dòng PO hiếm không bị dòng dễ ghép chiếm mất.
2. **Sổ tạm trong bộ nhớ:** `available[po_line] = CandidateFinder.availableQty` (min của vế PO và vế GRN). Mỗi lần lấy thì trừ vào sổ, để nhiều dòng hóa đơn trỏ cùng một dòng PO (nhiều-một) không lấy quá tổng.
3. **Với mỗi dòng hóa đơn**, duyệt ứng viên theo `rank` (điểm cao trước, cùng điểm thì PO cũ trước). Mỗi bước lấy `q = min(còn cần, available[po_line])`. Nếu `q > 0` thì tạo một `MatchLine(invoice_line, po_line, q)` và trừ sổ. Một dòng hóa đơn trải qua nhiều dòng PO là **một-nhiều (tách dòng hóa đơn)**.
4. **Chọn GRN cho 3 chiều:** trong cùng dòng PO, chọn GRN line **cũ nhất** (FIFO theo `received_date`) còn số dư làm `grn_line_id`. Vế GRN vẫn kiểm theo tổng ở `po_line_capacity`. Do có unique `(match, invoice_line, po_line)`, mỗi cặp chỉ có một `MatchLine`. Nếu sau này cần truy vết số lượng theo từng GRN line, giai đoạn 3 phải đổi unique thành `(match, invoice_line, po_line, grn_line)`; đây là điểm cần chốt với Tech Lead.
5. **Phần còn thiếu** sau khi duyệt hết ứng viên sẽ thành `MatchException` (qua `ExceptionFactoryService` của BE-7). Lệnh chạy không bị hủy:
   - không có ứng viên nào: `MISSING_PO`;
   - có ứng viên nhưng vế GRN bằng 0 (`missingGrn = true`): `MISSING_GRN`;
   - vế PO hết khi giữ chỗ thật: `CAPACITY_EXCEEDED`.
6. **Tính chênh lệch và dung sai** cho từng `MatchLine`: `VarianceCalculator`, sau đó `ToleranceEvaluationService` (BE-2) gắn `within_tolerance`. Điểm `Match` là trung bình điểm các dòng. Đề xuất cho giai đoạn 3: lấy trung bình **có trọng số theo `matched_amount`**.
7. **Giữ chỗ thật:** gọi BE-3 theo `po_line_id` tăng dần (tránh deadlock). Cần `tryReserve`/`reserveBatch` trả kết quả từng dòng thay vì ném exception, vì exception trong `@Transactional` đánh dấu rollback-only cả lệnh chạy. Dòng bị từ chối chuyển sang bước 5 với loại `CAPACITY_EXCEEDED`.

## 3. Ví dụ

**Một-nhiều.** Dòng hóa đơn 120 cái ITEM-A. Ứng viên PO-A (đặt 01/03, còn 80) và PO-B (đặt 15/03, còn 100), cùng điểm. Kết quả: lấy 80 từ PO-A (cũ hơn), rồi 40 từ PO-B. Tạo 2 `MatchLine`.

**Nhiều-một.** Dòng 1 cần 30, dòng 2 cần 50, cùng trỏ một dòng PO còn 60. Hai dòng có cùng số ứng viên nên xét theo `line_no`: dòng 1 đi trước, lấy 30 (sổ còn 30). Dòng 2 lấy 30, còn thiếu 20: duyệt ứng viên kế tiếp; nếu không còn ứng viên thì tạo ngoại lệ cho 20 cái còn thiếu.

**Giao nhiều đợt (3 chiều).** Dòng PO đặt 100; GRN1 (05/03) nhận 40, GRN2 (20/03) nhận 30; đã allocated 10. Vế GRN = 70 − 10 = 60. Hóa đơn 50 cái: ghép 50, `grn_line_id` = dòng của GRN1 (cũ nhất).

## 4. Vì sao không tối ưu toàn cục

Bài toán là luồng chi phí nhỏ nhất nếu muốn tối ưu tuyệt đối. Với quy mô một hóa đơn (vài chục dòng, vài trăm ứng viên), cách tham lam ở trên cho kết quả **xác định** (chạy lại ra cùng kết quả), **dễ giải thích** với kế toán ("lấy PO cũ trước, điểm cao trước"), và chạy trong O(dòng × ứng viên).
