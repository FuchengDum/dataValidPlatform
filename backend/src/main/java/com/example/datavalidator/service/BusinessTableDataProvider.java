package com.example.datavalidator.service;

import com.example.datavalidator.domain.DataRow;
import com.example.datavalidator.domain.DataTable;
import com.example.datavalidator.domain.DatasetSourceType;
import com.example.datavalidator.exception.BadRequestException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BusinessTableDataProvider {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<TableDescriptor> TABLES = Arrays.asList(
            table("t_order", "订单ID", "订单ID", "用户ID", "订单状态", "订单金额", "实付金额", "优惠金额",
                    "下单时间", "支付时间", "收货地址", "备注", "数据标记"),
            table("t_order_item", "明细ID", "明细ID", "订单ID", "商品ID", "商品名称", "单价", "数量", "小计金额", "数据标记"),
            table("t_product", "商品ID", "商品ID", "商品名称", "商品分类", "成本价", "售价", "库存数量", "上架状态", "数据标记"),
            table("t_payment", "支付ID", "支付ID", "订单ID", "用户ID", "支付方式", "支付金额", "支付状态",
                    "支付时间", "退款金额", "数据标记"),
            table("t_inventory_log", "流水ID", "流水ID", "商品ID", "变动类型", "变动数量", "变动前库存",
                    "变动后库存", "关联订单ID", "操作时间", "数据标记")
    );

    private final JdbcTemplate jdbcTemplate;

    public BusinessTableDataProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, DataTable> loadAllTables() {
        Map<String, DataTable> result = new LinkedHashMap<>();
        for (TableDescriptor descriptor : TABLES) {
            result.put(descriptor.logicalName, loadTable(descriptor.logicalName));
        }
        return result;
    }

    public DataTable loadTable(String logicalName) {
        TableDescriptor descriptor = descriptor(logicalName);
        DataTable table = new DataTable();
        table.setSheetName(descriptor.logicalName);
        table.setLogicalName(descriptor.logicalName);
        table.setSourceType(DatasetSourceType.DATABASE_TABLE);
        table.setHeaders(descriptor.headers);

        String sql = selectSql(descriptor);
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(sql);
        } catch (Exception ex) {
            throw new BadRequestException("业务表读取失败: " + logicalName);
        }
        for (Map<String, Object> item : rows) {
            DataRow row = new DataRow();
            row.setRowIndex(intValue(item.get("__row_index")));
            Map<String, String> values = new LinkedHashMap<>();
            for (String header : descriptor.headers) {
                values.put(header, stringValue(item.get(header)));
            }
            row.setValues(values);
            row.setPrimaryKey(values.get(descriptor.primaryKey));
            table.getRows().add(row);
        }
        return table;
    }

    private String selectSql(TableDescriptor descriptor) {
        String columns = descriptor.headers.stream()
                .map(this::quote)
                .collect(Collectors.joining(", "));
        return "SELECT " + quote("__row_index") + ", " + columns
                + " FROM " + quote(descriptor.logicalName)
                + " ORDER BY " + quote("__row_index");
    }

    private TableDescriptor descriptor(String logicalName) {
        for (TableDescriptor descriptor : TABLES) {
            if (descriptor.logicalName.equals(logicalName)) {
                return descriptor;
            }
        }
        throw new BadRequestException("业务表不存在: " + logicalName);
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private int intValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(stringValue(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).stripTrailingZeros().toPlainString();
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime().format(DATE_TIME);
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).format(DATE_TIME);
        }
        return value.toString();
    }

    private static TableDescriptor table(String logicalName, String primaryKey, String... headers) {
        return new TableDescriptor(logicalName, primaryKey, Arrays.asList(headers));
    }

    private static class TableDescriptor {
        private final String logicalName;
        private final String primaryKey;
        private final List<String> headers;

        TableDescriptor(String logicalName, String primaryKey, List<String> headers) {
            this.logicalName = logicalName;
            this.primaryKey = primaryKey;
            this.headers = headers;
        }
    }
}
