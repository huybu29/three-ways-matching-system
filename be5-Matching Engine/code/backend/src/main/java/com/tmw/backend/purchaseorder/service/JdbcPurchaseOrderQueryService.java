package com.tmw.backend.purchaseorder.service;

import com.tmw.backend.purchaseorder.api.PoLineCandidateQuery;
import com.tmw.backend.purchaseorder.api.PoLineCandidateView;
import com.tmw.backend.purchaseorder.api.PurchaseOrderQueryApi;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Cài đặt PurchaseOrderQueryApi bằng một câu SQL thuần (đọc từ classpath) để kiểm soát được kế hoạch
 * thực thi và index. Đọc không khóa: số dư chỉ để xếp hạng ứng viên.
 *
 * <p>Đề xuất của BE-5 cho BE-3 (xem PurchaseOrderQueryApi).</p>
 */
@Service
public class JdbcPurchaseOrderQueryService implements PurchaseOrderQueryApi {

    static final String SQL_RESOURCE = "sql/purchaseorder/find-candidate-po-lines.sql";

    /** IN () rỗng là cú pháp sai trong SQL → thay bằng giá trị không bao giờ khớp. */
    private static final String NO_ITEM_CODE = "#NO-ITEM-CODE#";
    private static final UUID NO_UUID = new UUID(0L, 0L);

    private static final RowMapper<PoLineCandidateView> ROW_MAPPER = (rs, rowNum) -> new PoLineCandidateView(
            rs.getObject("purchase_order_id", UUID.class),
            rs.getString("po_number"),
            rs.getObject("supplier_id", UUID.class),
            rs.getObject("order_date", LocalDate.class),
            rs.getString("status"),
            rs.getString("currency"),
            rs.getObject("po_line_id", UUID.class),
            rs.getInt("line_no"),
            rs.getString("item_code"),
            rs.getBigDecimal("unit_price"),
            rs.getBigDecimal("vat_rate"),
            rs.getBigDecimal("ordered_qty"),
            rs.getBigDecimal("allocated_qty"),
            rs.getBigDecimal("reserved_qty"),
            rs.getBigDecimal("received_qty")
    );

    private final NamedParameterJdbcTemplate jdbc;
    private final String sql;

    public JdbcPurchaseOrderQueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.sql = loadSql();
    }

    @Override
    public List<PoLineCandidateView> findCandidateLines(PoLineCandidateQuery query) {
        if (query.hasNoSelector()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("supplierId", query.supplierId())
                .addValue("orderDateTo", query.orderDateTo())
                .addValue("currency", query.currency(), Types.VARCHAR)
                .addValue("itemCodes", orSentinel(query.itemCodes(), NO_ITEM_CODE))
                .addValue("poLineIds", orSentinel(query.poLineIds(), NO_UUID))
                .addValue("poNumber", query.poNumber(), Types.VARCHAR)
                .addValue("purchaseOrderId", query.purchaseOrderId(), Types.OTHER)
                .addValue("limit", query.limit());
        return jdbc.query(sql, params, ROW_MAPPER);
    }

    private static <T> Collection<T> orSentinel(Collection<T> values, T sentinel) {
        return values.isEmpty() ? List.of(sentinel) : values;
    }

    static String loadSql() {
        try (InputStream in = new ClassPathResource(SQL_RESOURCE).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được " + SQL_RESOURCE, e);
        }
    }
}
