package com.example.datavalidator.service;

import com.example.datavalidator.exception.BadRequestException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class GenericJdbcTableReader {
    private final GenericJdbcDataSourceFactory dataSourceFactory;

    GenericJdbcTableReader(GenericJdbcDataSourceFactory dataSourceFactory) {
        this.dataSourceFactory = dataSourceFactory;
    }

    List<GenericValidationConfig.TableConfig> read(GenericValidationConfig.SourceConfig source) {
        List<GenericValidationConfig.TableConfig> loaded = new ArrayList<>();
        try (Connection connection = dataSourceFactory.open(source.getJdbc())) {
            for (GenericValidationConfig.TableConfig table : source.getTables()) {
                loaded.add(readTable(connection, source.getJdbc(), table));
            }
            return loaded;
        } catch (SQLException ex) {
            throw new BadRequestException("JDBC 只读查询失败: " + ex.getMessage());
        }
    }

    private GenericValidationConfig.TableConfig readTable(Connection connection,
                                                          GenericValidationConfig.JdbcConfig jdbc,
                                                          GenericValidationConfig.TableConfig table)
            throws SQLException {
        String sql = isBlank(table.getSql()) ? tableSql(table, jdbc.getDialect()) : table.getSql();
        SqlReadOnlyGuard.requireSelect(sql);
        List<Map<String, Object>> rows = query(connection, jdbc, table, sql);
        GenericValidationConfig.TableConfig loaded = new GenericValidationConfig.TableConfig();
        loaded.setLogicalName(table.getLogicalName());
        loaded.setPrimaryKey(table.getPrimaryKey());
        loaded.setHeaders(table.getHeaders());
        loaded.setRows(rows);
        return loaded;
    }

    private List<Map<String, Object>> query(Connection connection, GenericValidationConfig.JdbcConfig jdbc,
                                           GenericValidationConfig.TableConfig table, String sql)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(Math.max(jdbc.getQueryTimeoutSeconds(), 1));
            statement.setFetchSize(Math.max(jdbc.getFetchSize(), 1));
            statement.setMaxRows(Math.max(jdbc.getMaxRows(), 1) + 1);
            try (ResultSet resultSet = statement.executeQuery(sql)) {
                Map<String, Integer> columns = columns(resultSet.getMetaData());
                requireHeaders(table, columns);
                return rows(resultSet, table, columns, jdbc.getMaxRows());
            }
        }
    }

    private Map<String, Integer> columns(ResultSetMetaData metaData) throws SQLException {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int index = 1; index <= metaData.getColumnCount(); index++) {
            columns.put(metaData.getColumnLabel(index).toLowerCase(Locale.ROOT), index);
        }
        return columns;
    }

    private void requireHeaders(GenericValidationConfig.TableConfig table, Map<String, Integer> columns) {
        if (table.getHeaders() == null || table.getHeaders().isEmpty()) {
            throw new BadRequestException("JDBC 表必须配置 headers: " + table.getLogicalName());
        }
        for (String header : table.getHeaders()) {
            if (!columns.containsKey(header.toLowerCase(Locale.ROOT))) {
                throw new BadRequestException("JDBC 查询结果缺少字段: " + header);
            }
        }
    }

    private List<Map<String, Object>> rows(ResultSet resultSet, GenericValidationConfig.TableConfig table,
                                           Map<String, Integer> columns, int maxRows) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        while (resultSet.next()) {
            if (rows.size() >= maxRows) {
                throw new BadRequestException("JDBC 查询结果超过 maxRows: " + maxRows);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            for (String header : table.getHeaders()) {
                Integer columnIndex = columns.get(header.toLowerCase(Locale.ROOT));
                row.put(header, resultSet.getObject(columnIndex));
            }
            rows.add(row);
        }
        return rows;
    }

    private String tableSql(GenericValidationConfig.TableConfig table, String dialect) {
        StringBuilder sql = new StringBuilder("SELECT ");
        for (int index = 0; index < table.getHeaders().size(); index++) {
            String logical = table.getHeaders().get(index);
            String physical = physicalField(table, logical);
            if (index > 0) {
                sql.append(", ");
            }
            sql.append(SqlReadOnlyGuard.quoteIdentifier(physical, dialect))
                    .append(" AS ")
                    .append(SqlReadOnlyGuard.quoteIdentifier(logical, dialect));
        }
        sql.append(" FROM ")
                .append(SqlReadOnlyGuard.quoteQualifiedIdentifier(table.getPhysicalName(), dialect));
        return sql.toString();
    }

    private String physicalField(GenericValidationConfig.TableConfig table, String logical) {
        if (table.getFieldMappings() != null && table.getFieldMappings().containsKey(logical)) {
            return table.getFieldMappings().get(logical);
        }
        return logical;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
