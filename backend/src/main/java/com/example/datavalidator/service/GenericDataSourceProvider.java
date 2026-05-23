package com.example.datavalidator.service;

import com.example.datavalidator.domain.DataRow;
import com.example.datavalidator.domain.DataTable;
import com.example.datavalidator.domain.DatasetSourceType;
import com.example.datavalidator.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final GenericFileTableReader fileTableReader;
    private final GenericJdbcTableReader jdbcTableReader;

    @Autowired
    public GenericDataSourceProvider(GenericRuleAssetLoader assetLoader, JdbcTemplate jdbcTemplate) {
        this(assetLoader, jdbcTemplate, new GenericFileTableReader(),
                new GenericJdbcTableReader(new GenericJdbcDataSourceFactory()));
    }

    GenericDataSourceProvider(GenericRuleAssetLoader assetLoader, JdbcTemplate jdbcTemplate,
                              GenericFileTableReader fileTableReader,
                              GenericJdbcTableReader jdbcTableReader) {
        this.assetLoader = assetLoader;
        this.fileTableReader = fileTableReader;
        this.jdbcTableReader = jdbcTableReader;
    }

    public Map<String, DataTable> load(GenericValidationConfig.SourceConfig source, Path baseDir) {
        String type = normalize(source.getType());
        if ("file".equals(type)) {
            if (isBlank(source.getFile())) {
                return fromFileTables(source.getTables(), baseDir, DatasetSourceType.EXCEL_WORKBOOK);
            }
            Path path = resolve(baseDir, source.getFile());
            return fromFileTables(assetLoader.loadSource(path).getTables(), path.getParent(),
                    DatasetSourceType.EXCEL_WORKBOOK);
        }
        if ("inline".equals(type)) {
            return fromConfiguredTables(source.getTables(), DatasetSourceType.EXCEL_WORKBOOK);
        }
        if ("jdbc".equals(type) || "database_table".equals(type) || "sql_query".equals(type)) {
            return fromConfiguredTables(jdbcTableReader.read(source), DatasetSourceType.DATABASE_TABLE);
        }
        throw new BadRequestException("不支持的数据源类型: " + source.getType());
    }

    private Map<String, DataTable> fromFileTables(List<GenericValidationConfig.TableConfig> configs,
                                                  Path baseDir, DatasetSourceType sourceType) {
        Map<String, DataTable> tables = new LinkedHashMap<>();
        for (GenericValidationConfig.TableConfig config : configs) {
            requireTableConfig(config);
            GenericValidationConfig.TableConfig loaded = config;
            if (!isBlank(config.getFile())) {
                loaded = fileTableReader.read(config, resolve(baseDir, config.getFile()));
            }
            tables.put(loaded.getLogicalName(), dataTable(loaded, sourceType));
        }
        return tables;
    }

    private Map<String, DataTable> fromConfiguredTables(List<GenericValidationConfig.TableConfig> configs,
                                                        DatasetSourceType sourceType) {
        Map<String, DataTable> tables = new LinkedHashMap<>();
        for (GenericValidationConfig.TableConfig config : configs) {
            requireTableConfig(config);
            DataTable table = dataTable(config, sourceType);
            tables.put(config.getLogicalName(), table);
        }
        return tables;
    }

    private DataTable dataTable(GenericValidationConfig.TableConfig config, DatasetSourceType sourceType) {
        DataTable table = new DataTable();
        table.setLogicalName(config.getLogicalName());
        table.setSheetName(config.getLogicalName());
        table.setSourceType(sourceType);
        table.setHeaders(headers(config));
        table.setRows(rows(config, table.getHeaders()));
        return table;
    }

    private List<String> headers(GenericValidationConfig.TableConfig config) {
        if (config.getHeaders() != null && !config.getHeaders().isEmpty()) {
            return config.getHeaders();
        }
        if (config.getRows() == null || config.getRows().isEmpty()) {
            throw new BadRequestException("表缺少 headers 或 rows: " + config.getLogicalName());
        }
        return new ArrayList<>(config.getRows().get(0).keySet());
    }

    private List<DataRow> rows(GenericValidationConfig.TableConfig config, List<String> headers) {
        List<DataRow> rows = new ArrayList<>();
        int index = 1;
        if (config.getRows() == null) {
            return rows;
        }
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
