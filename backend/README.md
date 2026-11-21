# Three-Ways Matching System — Backend Guide

Tài liệu hướng dẫn kiến trúc nền tảng, cơ chế chống trùng lặp giao dịch (**Idempotency**) và khóa lạc quan (**Optimistic Locking với ETag / If-Match**) dành cho các module nghiệp vụ (**BE-3 / BE-4**: `purchaseorder`, `goodsreceipt`, `invoice`, `allocation`, `matching`, `matchexception`, `posting`).

---

## 1. Kiến trúc Common & Quy chuẩn chung

Tất cả các entity, controller và service của BE-3 / BE-4 kế thừa và sử dụng trực tiếp các thành phần dùng chung trong `com.tmw.backend.common`:

| Thành phần | Package / Class | Mục đích sử dụng |
| :--- | :--- | :--- |
| **Audit & ID** | `common.entity.BaseEntity` | Cung cấp khóa chính UUID và thông tin audit cơ bản. |
| **Optimistic Locking** | `common.entity.VersionedEntity` | Bổ sung `@Version Integer version` và hàm `getETag()` trả về chuỗi chuẩn `"\"0\""`, `"\"1\""`... |
| **Idempotency** | `common.filter.IdempotencyFilter` | Chặn các request ghi (`POST`, `PUT`, `PATCH`, `DELETE`) thiếu `Idempotency-Key` và cache response trong 24 giờ. |
| **ETag & If-Match** | `common.web.RequireIfMatch`, `ETagSupport`, `IfMatchInterceptor` | Bắt buộc và xác thực header `If-Match` cho các API cập nhật/chuyển trạng thái chứng từ. |
| **Chuẩn lỗi RFC 7807** | `common.error.BusinessException`, `ErrorCode`, `GlobalExceptionHandler` | Chuẩn hóa toàn bộ lỗi nghiệp vụ và kỹ thuật sang `application/problem+json` kèm `traceId`. |

---

## 2. Hướng dẫn tích hợp Idempotency (`Idempotency-Key`)

`IdempotencyFilter` tự động áp dụng cho **toàn bộ các HTTP method ghi dữ liệu**: `POST`, `PUT`, `PATCH`, `DELETE`.

### 2.1. Quy tắc hoạt động
1. **Thiếu header `Idempotency-Key`**: Hệ thống tự động trả về **`400 Bad Request`** với mã lỗi `MISSING_IDEMPOTENCY_KEY`.
2. **Gửi trùng `Idempotency-Key` trong vòng 24 giờ**:
   - Nếu request trước đã hoàn tất (`COMPLETED`), filter trả lại nguyên vẹn **HTTP Status** và **JSON Response Body** đã lưu mà không thực thi lại logic nghiệp vụ.
   - Nếu request trước đang xử lý (`IN_PROGRESS`), filter trả về **`409 Conflict`** với mã lỗi `IDEMPOTENCY_KEY_REUSED`.
3. **Dọn dẹp tự động**: `IdempotencyCleanupJob` định kỳ xóa các bản ghi đã hết hạn (`expires_at <= now()`).

### 2.2. Ví dụ gọi API từ Client / Postman / Integration Test
``bash
curl -X POST http://localhost:8080/api/v1/purchase-orders \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{"poNumber": "PO-2026-001", "supplierId": "..."}'
``

> **Lưu ý cho BE-3 / BE-4**: Không cần tự kiểm tra `Idempotency-Key` thủ công trong Controller hay Service. Chỉ cần đảm bảo các test tích hợp (`MockMvc` / `RestAssured`) luôn đính kèm header `Idempotency-Key` khi gọi `POST`, `PUT`, `PATCH`, `DELETE`.

---

## 3. Hướng dẫn tích hợp Optimistic Locking (`ETag` & `If-Match`)

Để tránh ghi đè dữ liệu đồng thời (Lost Update) khi nhiều kế toán viên cùng thao tác trên một chứng từ (`PurchaseOrder`, `GoodsReceipt`, `Invoice`, `MatchProposal`, `MatchException`...), BE-3 và BE-4 áp dụng quy trình 3 bước sau:

### Bước 1: Kế thừa `VersionedEntity` ở tầng Entity
``java
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder extends VersionedEntity {
    // Tự động có trường @Version Integer version và phương thức getETag()
}
``

### Bước 2: Trả về header `ETag` ở các API đọc (`GET`) và tạo/cập nhật (`POST`, `PUT`, `PATCH`)
``java
@GetMapping("/{id}")
public ResponseEntity<PurchaseOrderResponse> getById(@PathVariable UUID id) {
    PurchaseOrder po = purchaseOrderService.getById(id);
    return ResponseEntity.ok()
            .eTag(ETagSupport.from(po)) // hoặc po.getETag() -> "\"0\""
            .body(PurchaseOrderResponse.from(po));
}
``

### Bước 3: Gắn `@RequireIfMatch` và gọi `ETagSupport.verifyMatch(...)` ở các API cập nhật/duyệt
``java
@RequireIfMatch
@PatchMapping("/{id}/approve")
public ResponseEntity<PurchaseOrderResponse> approve(
        @PathVariable UUID id,
        @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch
) {
    PurchaseOrder updated = purchaseOrderService.approve(id, ifMatch);
    return ResponseEntity.ok()
            .eTag(ETagSupport.from(updated))
            .body(PurchaseOrderResponse.from(updated));
}
``

Bên trong Service:
``java
@Transactional
public PurchaseOrder approve(UUID id, String ifMatchHeader) {
    PurchaseOrder po = purchaseOrderRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy PO"));

    // So sánh version từ header If-Match với version hiện tại trong DB (ném 412 nếu lệch)
    ETagSupport.verifyMatch(ifMatchHeader, po);

    po.approve();
    return purchaseOrderRepository.saveAndFlush(po);
}
``

### Luồng kiểm tra lỗi tự động của `IfMatchInterceptor` & `ETagSupport`
| Tình huống | HTTP Status | `ErrorCode` | Ghi chú |
| :--- | :---: | :--- | :--- |
| Thiếu header `If-Match` trên endpoint gắn `@RequireIfMatch` | `428` | `PRECONDITION_REQUIRED` | Bị chặn ngay tại `IfMatchInterceptor` trước khi vào Controller. |
| Sai cú pháp `If-Match` (ví dụ `"abc"`) | `412` | `VERSION_CONFLICT` | Bị chặn ngay tại `IfMatchInterceptor`. |
| Phiên bản `If-Match` khác với `entity.getVersion()` trong DB | `412` | `VERSION_CONFLICT` | Ném từ `ETagSupport.verifyMatch` hoặc `ObjectOptimisticLockingFailureException`. |
| Khớp phiên bản (`If-Match: "0"`) | `200` | — | Cập nhật thành công và trả về header `ETag: "1"` mới. |

---

## 4. Bảng mã lỗi chuẩn RFC 7807 (`ErrorCode`)

Khi ném `new BusinessException(ErrorCode.XXX, "Chi tiết lỗi")`, `GlobalExceptionHandler` tự động trả về JSON chuẩn `application/problem+json`:

| `ErrorCode` | HTTP Status | Ý nghĩa nghiệp vụ |
| :--- | :---: | :--- |
| `UNAUTHORIZED` | `401` | Chưa xác thực hoặc JWT token hết hạn / không hợp lệ. |
| `FORBIDDEN` | `403` | Không có quyền thực hiện thao tác. |
| `SOD_VIOLATION` | `403` | Vi phạm nguyên tắc bất kiêm nhiệm (Segregation of Duties). |
| `NOT_FOUND` | `404` | Không tìm thấy chứng từ / tài nguyên yêu cầu. |
| `DUPLICATE_INVOICE` | `409` | Hóa đơn đã tồn tại trong hệ thống (trùng số hóa đơn + MST). |
| `ALLOCATION_CAPACITY_EXCEEDED` | `409` | Số lượng hoặc giá trị phân bổ vượt quá hạn mức còn lại của PO. |
| `INVALID_STATE_TRANSITION` | `409` | Trạng thái chứng từ không hợp lệ cho bước chuyển trạng thái hiện tại. |
| `IDEMPOTENCY_KEY_REUSED` | `409` | `Idempotency-Key` đang được xử lý đồng thời hoặc tái sử dụng sai ngữ cảnh. |
| `VERSION_CONFLICT` | `412` | Phiên bản dữ liệu đã bị người khác cập nhật trước đó (`If-Match` không khớp). |
| `VALIDATION_ERROR` | `422` | Dữ liệu đầu vào sai định dạng (tiền tệ, số lượng, MST, thiếu trường bắt buộc). |
| `PRECONDITION_REQUIRED` | `428` | Thiếu header `If-Match` trên API yêu cầu Optimistic Locking. |

---

## 5. Checklist tích hợp nhanh cho BE-3 & BE-4

- [ ] **Entity**: Mọi chứng từ có cập nhật trạng thái (`PO`, `GRN`, `Invoice`, `MatchProposal`, `MatchException`, `PostingBatch`) đều kế thừa `VersionedEntity`.
- [ ] **Controller (Read/Create)**: Luôn gắn `.eTag(ETagSupport.from(entity))` vào `ResponseEntity` trả về.
- [ ] **Controller (Update/Approve/Reject/Post)**: Gắn annotation `@RequireIfMatch` lên method và nhận `@RequestHeader(HttpHeaders.IF_MATCH) String ifMatch`.
- [ ] **Service**: Gọi `ETagSupport.verifyMatch(ifMatch, entity)` trước khi thay đổi trạng thái và dùng `saveAndFlush(...)` để lấy ngay `version` mới trả về header `ETag`.
- [ ] **Integration Tests**: Luôn truyền header `Idempotency-Key` cho mọi request `POST`/`PUT`/`PATCH`/`DELETE`, và kiểm thử đủ 3 case `428` (thiếu `If-Match`), `412` (lệch version), `200` (đúng version) cho các API cập nhật.
