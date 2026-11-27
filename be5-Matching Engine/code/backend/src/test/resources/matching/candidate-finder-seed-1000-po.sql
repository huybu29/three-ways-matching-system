-- Dữ liệu tất định: 50 nhà cung cấp x 20 PO = 1.000 PO, mỗi PO 5 dòng = 5.000 dòng PO.
-- Nhà cung cấp s:  UUID 00000000-0000-4000-8000-<s 12 chữ số>
-- PO p (1..1000):  supplier = ((p-1) % 50) + 1, số PO = 'PO-2026-' || lpad(p, 5, '0')
-- Mã hàng:         'ITEM-' || lpad(((p * 7 + l * 13) % 200) + 1, 4, '0')  (200 mã)
-- Trạng thái:      p % 20 = 0 → CLOSED; p % 10 = 0 → FULLY_INVOICED; p % 7 = 0 → PARTIALLY_INVOICED; còn lại OPEN
-- Ngày đặt:        2026-01-01 + (p % 300) ngày
-- Hạn mức:         ordered = 10 + (p*l % 91); allocated/reserved/received theo công thức bên dưới

INSERT INTO purchase_order (id, po_number, supplier_id, order_date, currency, status)
SELECT ('10000000-0000-4000-8000-' || lpad(p::text, 12, '0'))::uuid,
       'PO-2026-' || lpad(p::text, 5, '0'),
       ('00000000-0000-4000-8000-' || lpad((((p - 1) % 50) + 1)::text, 12, '0'))::uuid,
       DATE '2026-01-01' + (p % 300),
       'VND',
       CASE WHEN p % 20 = 0 THEN 'CLOSED'
            WHEN p % 10 = 0 THEN 'FULLY_INVOICED'
            WHEN p % 7  = 0 THEN 'PARTIALLY_INVOICED'
            ELSE 'OPEN' END
FROM generate_series(1, 1000) AS p;

INSERT INTO po_line (id, purchase_order_id, line_no, item_code, ordered_qty, unit_price, vat_rate)
SELECT ('20000000-0000-4000-8000-' || lpad((p * 10 + l)::text, 12, '0'))::uuid,
       ('10000000-0000-4000-8000-' || lpad(p::text, 12, '0'))::uuid,
       l,
       'ITEM-' || lpad((((p * 7 + l * 13) % 200) + 1)::text, 4, '0'),
       10 + (p * l % 91),
       1000 * (1 + (p + l) % 50),
       0.1000
FROM generate_series(1, 1000) AS p, generate_series(1, 5) AS l;

-- allocated: 0..(ordered/2); reserved: 0 hoặc 1 phần nhỏ; received: dòng l=5 chưa nhận hàng, còn lại nhận 60..100%
INSERT INTO po_line_capacity (po_line_id, ordered_qty, allocated_qty, reserved_qty, received_qty, ordered_amount)
SELECT pl.id,
       pl.ordered_qty,
       floor(pl.ordered_qty * ((p * 3 + l) % 6) / 10),
       CASE WHEN (p + l) % 4 = 0 THEN 2 ELSE 0 END,
       CASE WHEN l = 5 THEN 0 ELSE floor(pl.ordered_qty * (6 + (p + l) % 5) / 10) END,
       pl.ordered_qty * pl.unit_price
FROM generate_series(1, 1000) AS p
CROSS JOIN generate_series(1, 5) AS l
JOIN po_line pl ON pl.id = ('20000000-0000-4000-8000-' || lpad((p * 10 + l)::text, 12, '0'))::uuid;

ANALYZE purchase_order;
ANALYZE po_line;
ANALYZE po_line_capacity;
