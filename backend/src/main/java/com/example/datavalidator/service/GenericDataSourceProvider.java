package com.example.datavalidator.service;

import com.example.datavalidator.domain.DataRow;
import com.example.datavalidator.domain.DataTable;
import com.example.datavalidator.domain.DatasetSourceType;
import com.example.datavalidator.exception.BadRequestException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GenericDataSourceProvider {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GenericRuleAssetLoader assetLoader;
    private final JdbcTemplate jdbcTemplate;

    public GenericDataSourceProvider(GenericRuleAssetLoader assetLoader, JdbcTemplate jdbcTemplate) {
        this.assetLoader = assetLoader;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, DataTable> load(GenericValidationConfig.SourceConfig source, Path baseDir) {
        String type = normalize(source.getType());
        if ("file".equals(type)) {
            if (isBlank(source.getFile())) {
                return fromConfiguredTables(source.getTables(), DatasetSourceType.EXCEL_WORKBOOK);
            }
            Path path = resolve(baseDir, source.getFile());
            return fromConfiguredTables(assetLoader.loadSource(path).getTables(), DatasetSourceType.EXCEL_WORKBOOK);
        }
        if ("inline".equals(type)) {
            return fromConfiguredTables(source.getTables(), DatasetSourceType.EXCEL_WORKBOOK);
        }
        if ("jdbc".equals(type) || "database_table".equals(type) || "sql_query".equals(type)) {
            return fromJdbc(source.getTables());
        }
        throw new BadRequestException("不支持的数据源类型: " + source.getType());
    }

    private Map<String, DataTable> fromConfiguredTables(List<GenericValidationConfig.TableConfig> configs,
                                                        DatasetSourceType sourceType) {
        Map<String, DataTable> tables = new LinkedHashMap<>();
        for (GenericValidationConfig.TableConfig config : configs) {
            requireTableConfig(config);
            DataTable table = new DataTable();
            table.setLogicalName(config.getLogicalName());
            table.setSheetName(config.getLogicalName());
            table.setSourceType(sourceType);
            table.setHeaders(headers(config));
            table.setRows(rows(config, table.getHeaders()));
            tables.put(table.getLogicalName(), table);
        }
        return tables;
    }

    private Map<String, DataTable> fromJdbc(List<GenericValidationConfig.TableConfig> configs) {
        Map<String, DataTable> tables = new LinkedHashMap<>();
        for (GenericValidationConfig.TableConfig config : configs) {
            requireTableConfig(config);
            String sql = jdbcSql(config);
            SqlReadOnlyGuard.requireSelect(sql);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            GenericValidationConfig.TableConfig loaded = new GenericValidationConfig.TableConfig();
            loaded.setLogicalName(config.getLogicalName());
            loaded.setPrimaryKey(config.getPrimaryKey());
            loaded.setRows(rows);
            loaded.setHeaders(config.getHeaders().isEmpty() && !rows.isEmpty()
                    ? new ArrayList<>(rows.get(0).keySet()) : config.getHeaders());
            tables.put(config.getLogicalName(), fromConfiguredTables(
                    java.util.Collections.singletonList(loaded), DatasetSourceType.DATABASE_TABLE)
                    .get(config.getLogicalName()));
        }
        return tables;
    }

    private String jdbcSql(GenericValidationConfig.TableConfig config) {
        if (!isBlank(config.getSql())) {
            return config.getSql();
        }
        String tableName = isBlank(config.getPhysicalName()) ? config.getLogicalName() : config.getPhysicalName();
        SqlReadOnlyGuard.requireIdentifier(tableName);
        return "SELECT * FROM " + tableName;
    }

    private List<String> headers(GenericValidationConfig.TableConfig config) {
        if (!config.getHeaders().isEmpty()) {
            return config.getHeaders();
        }
        if (config.getRows().isEmpty()) {
            throw new BadRequestException("表缺少 headers 或 rows: " + config.getLogicalName());
        }
        return new ArrayList<>(config.getRows().get(0).keySet());
    }

    private List<DataRow> rows(GenericValidationConfig.TableConfig config, List<String> headers) {
        List<DataRow> rows = new ArrayList<>();
        int index = 1;
        for (Map<String, Object> item : config.getRows()) {
            DataRow row = new DataRow();
            row.setRowIndex(index++);
            Map<String, String> values = new LinkedHashMap<>();
            for (String header : headers) {
                values.put(header, stringValue(item.get(header)));
            }
            row.setValues(values);
            row.setPrimaryKey(values.get(config.getPrimaryKey()));
            rows.add(row);
        }
        return rows;
    }

    private void requireTableConfig(GenericValidationConfig.TableConfig config) {
        if (config == null || isBlank(config.getLogicalName()) || isBlank(config.getPrimaryKey())) {
            throw new BadRequestException("表配置必须包含 logicalName 和 primaryKey");
        }
        SqlReadOnlyGuard.requireIdentifier(config.getLogicalName());
    }

    private Path resolve(Path baseDir, String path) {
        Path candidate = Path.of(path);
        return candidate.isAbsolute() ? candidate : baseDir.resolve(candidate).normalize();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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
}
