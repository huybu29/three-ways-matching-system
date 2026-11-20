
INSERT INTO supplier (id, code, name, tax_code, address, version, created_at, updated_at)
VALUES
    ('11111111-1111-4111-8111-000000000001', 'SUP-FPTIS',  'Công ty TNHH Hệ thống Thông tin FPT',              '0104128565',     'Số 10 Phạm Văn Bạch, Cầu Giấy, Hà Nội',                 0, TIMESTAMPTZ '2026-11-01 08:00:00+07', TIMESTAMPTZ '2026-11-01 08:00:00+07'),
    ('11111111-1111-4111-8111-000000000002', 'SUP-VIETTEL','Tổng Công ty Giải pháp Doanh nghiệp Viettel',      '0100109106',     'Số 1 Trần Hữu Dực, Nam Từ Liêm, Hà Nội',                0, TIMESTAMPTZ '2026-11-01 08:01:00+07', TIMESTAMPTZ '2026-11-01 08:01:00+07'),
    ('11111111-1111-4111-8111-000000000003', 'SUP-CMC',    'Công ty Cổ phần Hạ tầng Viễn thông CMC',           '0102900049',     'Số 11 Duy Tân, Cầu Giấy, Hà Nội',                       0, TIMESTAMPTZ '2026-11-01 08:02:00+07', TIMESTAMPTZ '2026-11-01 08:02:00+07'),
    ('11111111-1111-4111-8111-000000000004', 'SUP-DGW',    'Công ty Cổ phần Thế Giới Số (Digiworld)',          '0302861742',     'Số 195-197 Nguyễn Thái Bình, Quận 1, TP. Hồ Chí Minh',   0, TIMESTAMPTZ '2026-11-01 08:03:00+07', TIMESTAMPTZ '2026-11-01 08:03:00+07'),
    ('11111111-1111-4111-8111-000000000005', 'SUP-PSD',    'Công ty Cổ phần Dịch vụ Phân phối Tổng hợp Dầu khí','0305482862-001', 'Phòng 207, Tòa nhà PetroVietnam, Quận 1, TP. HCM',      0, TIMESTAMPTZ '2026-11-01 08:04:00+07', TIMESTAMPTZ '2026-11-01 08:04:00+07'),
    ('11111111-1111-4111-8111-000000000006', 'SUP-TLG',    'Công ty Cổ phần Tập đoàn Thiên Long',              '0301464830',     'Lô 6-8-10-12 Đường số 3, KCN Tân Tạo, Bình Tân, TP. HCM',0, TIMESTAMPTZ '2026-11-01 08:05:00+07', TIMESTAMPTZ '2026-11-01 08:05:00+07'),
    ('11111111-1111-4111-8111-000000000007', 'SUP-HPG',    'Công ty Cổ phần Thép Hòa Phát Dung Quất',          '4300806436',     'Khu kinh tế Dung Quất, Bình Sơn, Quảng Ngãi',           0, TIMESTAMPTZ '2026-11-01 08:06:00+07', TIMESTAMPTZ '2026-11-01 08:06:00+07'),
    ('11111111-1111-4111-8111-000000000008', 'SUP-PNJ',    'Công ty Cổ phần Chế tác và Kinh doanh Vàng bạc',   '0300521758-002', '170E Phan Đăng Lưu, Phú Nhuận, TP. Hồ Chí Minh',        0, TIMESTAMPTZ '2026-11-01 08:07:00+07', TIMESTAMPTZ '2026-11-01 08:07:00+07'),
    ('11111111-1111-4111-8111-000000000009', 'SUP-GMD',    'Công ty Cổ phần Gemadept Logistics',               '0301116791',     'Số 6 Lê Thánh Tôn, Quận 1, TP. Hồ Chí Minh',            0, TIMESTAMPTZ '2026-11-01 08:08:00+07', TIMESTAMPTZ '2026-11-01 08:08:00+07'),
    ('11111111-1111-4111-8111-000000000010', 'SUP-VNM',    'Công ty Cổ phần Sữa Việt Nam (Vinamilk)',          '0300588569',     'Số 10 Tân Trào, Tân Phú, Quận 7, TP. Hồ Chí Minh',      0, TIMESTAMPTZ '2026-11-01 08:09:00+07', TIMESTAMPTZ '2026-11-01 08:09:00+07')
ON CONFLICT (id) DO NOTHING;

-- 2. SEED 3 LOẠI CHÍNH SÁCH DUNG SAI (Mặc định, Theo nhà cung cấp, Theo nhóm hàng)
INSERT INTO tolerance_policy (
    id,
    name,
    supplier_id,
    item_category,
    min_amount,
    max_amount,
    qty_tolerance_pct,
    price_tolerance_pct,
    amount_tolerance_abs,
    require_grn,
    is_default,
    is_active,
    version,
    created_at,
    updated_at
)
VALUES
    -- Loại 1: Chính sách mặc định toàn hệ thống (duy nhất 1 bản ghi is_default = TRUE)
    (
        '22222222-2222-4222-8222-000000000001',
        'Chính sách dung sai mặc định toàn hệ thống',
        NULL,
        NULL,
        NULL,
        NULL,
        2.0000,
        1.0000,
        50000.0000,
        TRUE,
        TRUE,
        TRUE,
        0,
        TIMESTAMPTZ '2026-11-01 08:10:00+07',
        TIMESTAMPTZ '2026-11-01 08:10:00+07'
    ),
    -- Loại 2a: Chính sách riêng theo nhà cung cấp (Đối tác chiến lược FPT IS - ngưỡng chặt 0.5%)
    (
        '22222222-2222-4222-8222-000000000002',
        'Chính sách riêng - Hệ thống Thông tin FPT',
        '11111111-1111-4111-8111-000000000001',
        NULL,
        0.0000,
        500000000.0000,
        0.0000,
        0.5000,
        20000.0000,
        TRUE,
        FALSE,
        TRUE,
        0,
        TIMESTAMPTZ '2026-11-01 08:11:00+07',
        TIMESTAMPTZ '2026-11-01 08:11:00+07'
    ),
    -- Loại 2b: Chính sách riêng theo nhà cung cấp dịch vụ vận tải (Gemadept - không bắt buộc GRN)
    (
        '22222222-2222-4222-8222-000000000003',
        'Chính sách dịch vụ logistics - Gemadept',
        '11111111-1111-4111-8111-000000000009',
        NULL,
        NULL,
        NULL,
        0.0000,
        1.5000,
        100000.0000,
        FALSE,
        FALSE,
        TRUE,
        0,
        TIMESTAMPTZ '2026-11-01 08:12:00+07',
        TIMESTAMPTZ '2026-11-01 08:12:00+07'
    ),
    -- Loại 3a: Chính sách theo nhóm hàng thiết bị CNTT (IT_HARDWARE - không lệch số lượng)
    (
        '22222222-2222-4222-8222-000000000004',
        'Chính sách nhóm hàng Thiết bị CNTT',
        NULL,
        'IT_HARDWARE',
        1000000.0000,
        1000000000.0000,
        0.0000,
        1.0000,
        100000.0000,
        TRUE,
        FALSE,
        TRUE,
        0,
        TIMESTAMPTZ '2026-11-01 08:13:00+07',
        TIMESTAMPTZ '2026-11-01 08:13:00+07'
    ),
    -- Loại 3b: Chính sách theo nhóm hàng văn phòng phẩm (OFFICE_SUPPLIES - cho phép lệch 5% số lượng)
    (
        '22222222-2222-4222-8222-000000000005',
        'Chính sách nhóm hàng Văn phòng phẩm',
        NULL,
        'OFFICE_SUPPLIES',
        0.0000,
        50000000.0000,
        5.0000,
        2.0000,
        15000.0000,
        TRUE,
        FALSE,
        TRUE,
        0,
        TIMESTAMPTZ '2026-11-01 08:14:00+07',
        TIMESTAMPTZ '2026-11-01 08:14:00+07'
    )
ON CONFLICT (id) DO NOTHING;