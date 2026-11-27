-- PurchaseOrderQueryApi.findCandidateLines
-- Index hỗ trợ: db/migration/R__matching_candidate_finder_indexes.sql
SELECT po.id               AS purchase_order_id,
       po.po_number        AS po_number,
       po.supplier_id      AS supplier_id,
       po.order_date       AS order_date,
       po.status           AS status,
       po.currency         AS currency,
       pl.id               AS po_line_id,
       pl.line_no          AS line_no,
       pl.item_code        AS item_code,
       pl.unit_price       AS unit_price,
       pl.vat_rate         AS vat_rate,
       c.ordered_qty       AS ordered_qty,
       c.allocated_qty     AS allocated_qty,
       c.reserved_qty      AS reserved_qty,
       c.received_qty      AS received_qty
FROM purchase_order po
JOIN po_line pl          ON pl.purchase_order_id = po.id
JOIN po_line_capacity c  ON c.po_line_id = pl.id
WHERE po.supplier_id = :supplierId
  AND po.status IN ('OPEN', 'PARTIALLY_INVOICED')
  AND po.order_date <= :orderDateTo
  AND (CAST(:currency AS VARCHAR) IS NULL OR po.currency = CAST(:currency AS VARCHAR))
  AND c.ordered_qty - c.allocated_qty - c.reserved_qty > 0
  AND (
        pl.item_code IN (:itemCodes)
     OR pl.id IN (:poLineIds)
     OR po.po_number = CAST(:poNumber AS VARCHAR)
     OR po.id = CAST(:purchaseOrderId AS UUID)
  )
ORDER BY po.order_date ASC, po.po_number ASC, pl.line_no ASC
LIMIT :limit
