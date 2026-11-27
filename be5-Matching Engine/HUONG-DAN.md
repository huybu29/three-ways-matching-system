# BE-5 · Giai đoạn 1 (sửa nợ) + Giai đoạn 2 (CandidateFinder)

## Trong gói này có gì

| Thư mục / file | Dùng khi nào |
|---|---|
| `patches/be5-gd1-gd2-gop.patch` | **Cách khuyên dùng.** Một patch gộp cả 2 giai đoạn, áp thẳng lên `develop` |
| `patches/1-...patch`, `patches/2-...patch` | Áp từng giai đoạn riêng (nếu muốn 2 PR tách nhau) |
| `code/` | 34 file đã sửa hoặc thêm, đúng cấu trúc repo (dùng khi upload qua web GitHub) |
| `XOA-3-FILE.txt` | 3 file phải xóa nếu upload qua web (patch thì tự xóa) |

Kết quả đã kiểm tra: 90/90 unit test đạt (compile Java 17); câu SQL tìm ứng viên chạy trên PostgreSQL 16 thật; patch gộp áp sạch lên `develop` hiện tại.

---

## Cách 1: Dùng lệnh git (PowerShell)

### 1. Lấy develop mới nhất và tạo nhánh

```powershell
cd $HOME\Documents\three-ways-matching-system
git status
```

Nếu `git status` báo có file sửa dở trên nhánh `feature/be5-phase1-debt` (do lần trước đã áp patch nhưng chưa commit), bỏ chúng đi để làm lại từ đầu cho gọn:

```powershell
git checkout develop
git branch -D feature/be5-phase1-debt
```

Lệnh `checkout` báo lỗi vì có file đang sửa thì chạy `git stash` trước, rồi chạy lại 2 lệnh trên.

Sau đó:

```powershell
git checkout develop
git pull origin develop
git checkout -b feature/be5-matching-phase1-2
```

### 2. Áp patch gộp

Giả sử bạn giải nén gói vào thư mục `Downloads\be5-gd1-gd2`:

```powershell
git apply --check --3way $HOME\Downloads\be5-gd1-gd2\patches\be5-gd1-gd2-gop.patch
git apply --3way $HOME\Downloads\be5-gd1-gd2\patches\be5-gd1-gd2-gop.patch
git status
```

Dòng `Falling back to direct application...` chỉ là thông báo, không phải lỗi. `git status` phải liệt kê **37 file**: 3 file `deleted`, các file còn lại là `modified` hoặc `new file`.

### 3. Chạy test

```powershell
cd backend
```

**a) Unit test (không cần Docker), mất khoảng 1 phút:**

```powershell
.\mvnw.cmd test "-Dtest=CandidateFinderUnitTest,ConfidenceScorerUnitTest,PoLineCandidateQueryUnitTest,MatchDomainUnitTest,MatchStateMachineMatrixUnitTest,VarianceCalculatorUnitTest,SoDGuardMatrixUnitTest"
```

Kết quả đạt: `Tests run: 90, Failures: 0, Errors: 0` và `BUILD SUCCESS`.

**b) Integration test (cần bật Docker Desktop trước):**

```powershell
.\mvnw.cmd test "-Dtest=MatchPartialUniqueIndexIntegrationTest,CandidateFinderPerformanceIntegrationTest"
```

- `MatchPartialUniqueIndexIntegrationTest`: mỗi hóa đơn chỉ có 1 match đang hoạt động, cùng các check constraint (6 test).
- `CandidateFinderPerformanceIntegrationTest`: nạp 1.000 PO, kiểm tra lọc đúng, dùng index, p95 < 50 ms (5 test). Trong log có dòng `CandidateFinder 1.000 PO: p50=... p95=...`.

Không có Docker thì bỏ qua (b); CI trên GitHub sẽ chạy.

**Không dùng** `.\mvnw.cmd test` không kèm `-Dtest` để đánh giá phần BE-5: các integration test của module khác đang lỗi sẵn vì `pom.xml` chưa có Flyway (việc của Tech Lead).

### 4. Commit và push

```powershell
cd ..
git add -A
git commit -m "feat(matching): BE-5 tra no giai doan 1 va giai doan 2 (CandidateFinder, ConfidenceScorer v1, thiet ke phan bo, do hieu nang)"
git push -u origin feature/be5-matching-phase1-2
```

### 5. Mở Pull Request vào `develop`

```
https://github.com/huybu29/three-ways-matching-system/compare/develop...feature/be5-matching-phase1-2
```

Gắn reviewer: **Tech Lead** và **BE-3** (PR có thêm `PurchaseOrderQueryApi` trong module `purchaseorder`).

---

## Cách 2: Upload qua web GitHub (không dùng lệnh)

1. Trên GitHub, tạo nhánh mới từ `develop`.
2. Ở trang gốc repo (đang chọn nhánh mới), bấm **Add file → Upload files**. Mở thư mục `code` trong gói, chọn cả **`backend`** và **`docs`**, kéo vào khung upload. Danh sách phải có 34 file, đường dẫn bắt đầu bằng `backend/...` hoặc `docs/...`. Bấm **Commit changes**.
3. Xóa tay 3 file trong `XOA-3-FILE.txt`: mở từng file trên GitHub → nút **…** → **Delete file** → commit.
4. Mở Pull Request vào `develop`.

Cách này không chạy được test trên máy; CI sẽ chạy sau khi mở PR.

---

## Ghi chú cho PR

- Giai đoạn 1: migration `V202611270140__matching_create_match_tables.sql` khớp entity, có partial unique index; `Match` thêm `approvedAt`/`postedAt`/`rejectReason`; MANUAL có confidence = null; `allocation_state` nhất quán; `VarianceCalculator` không ném lỗi khi chưa có GRN; xóa code trùng.
- Giai đoạn 2: `CandidateFinder`, `ConfidenceScorer` bản 1, index (`R__matching_candidate_finder_indexes.sql`), tài liệu `docs/be5/`.
- Cần phối hợp: Tech Lead thêm Flyway vào `pom.xml`; BE-4 thêm `po_reference` vào `Invoice`; BE-3 review `PurchaseOrderQueryApi`; BE-6 chốt người giữ `MatchStateMachine`.
