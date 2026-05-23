package com.example.datavalidator.service;

import com.example.datavalidator.exception.BadRequestException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class GenericFileTableReader {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    GenericValidationConfig.TableConfig read(GenericValidationConfig.TableConfig config, Path path) {
        String format = format(config, path);
        try {
            if ("csv".equals(format)) {
                return loaded(config, readCsv(config, path));
            }
            if ("jsonl".equals(format)) {
                return loaded(config, readJsonLines(config, path));
            }
            if ("json".equals(format)) {
                return loaded(config, readJson(config, path));
            }
            if ("xlsx".equals(format)) {
                return loaded(config, readXlsx(config, path));
            }
        } catch (IOException ex) {
            throw new BadRequestException("读取表 " + config.getLogicalName() + " 文件失败: "
                    + path.getFileName() + " - " + ex.getMessage());
        }
        throw new BadRequestException("表 " + config.getLogicalName() + " 不支持文件格式: " + format);
    }

    private GenericValidationConfig.TableConfig loaded(GenericValidationConfig.TableConfig config,
                                                       List<Map<String, Object>> rows) {
        GenericValidationConfig.TableConfig loaded = new GenericValidationConfig.TableConfig();
        loaded.setLogicalName(config.getLogicalName());
        loaded.setPrimaryKey(config.getPrimaryKey());
        loaded.setHeaders(headers(config, rows));
        loaded.setRows(rows);
        return loaded;
    }

    private List<Map<String, Object>> readCsv(GenericValidationConfig.TableConfig config, Path path)
            throws IOException {
        List<String> lines = Files.readAllLines(path, charset(config));
        if (lines.isEmpty()) {
            return new ArrayList<>();
        }
        int headerIndex = Math.max(config.getHeaderRow(), 1) - 1;
        if (headerIndex >= lines.size()) {
            throw new BadRequestException("表 " + config.getLogicalName() + " headerRow 超出文件行数");
        }
        List<String> headers = configuredHeaders(config);
        if (headers.isEmpty()) {
            headers = parseCsvLine(lines.get(headerIndex), delimiter(config));
        }
        int start = Math.max(config.getDataStartRow(), headerIndex + 2) - 1;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int lineIndex = start; lineIndex < lines.size(); lineIndex++) {
            if (lines.get(lineIndex).trim().isEmpty()) {
                continue;
            }
            List<String> cells = parseCsvLine(lines.get(lineIndex), delimiter(config));
            rows.add(row(headers, cells, config));
        }
        return rows;
    }

    private List<Map<String, Object>> readJsonLines(GenericValidationConfig.TableConfig config, Path path)
            throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, charset(config))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                rows.add(objectRow(OBJECT_MAPPER.readValue(line, new TypeReference<Map<String, Object>>() {}),
                        config, lineNumber));
            }
        }
        return normalizeRows(config, rows);
    }

    private List<Map<String, Object>> readJson(GenericValidationConfig.TableConfig config, Path path)
            throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(Files.newBufferedReader(path, charset(config)));
        JsonNode rowsNode = root.isArray() ? root : root.get("rows");
        if (rowsNode == null || !rowsNode.isArray()) {
            throw new BadRequestException("表 " + config.getLogicalName() + " JSON 文件必须是对象数组或包含 rows 数组");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        int index = 0;
        for (JsonNode node : rowsNode) {
            index++;
            rows.add(objectRow(OBJECT_MAPPER.convertValue(node, new TypeReference<Map<String, Object>>() {}),
                    config, index));
        }
        return normalizeRows(config, rows);
    }

    private List<Map<String, Object>> readXlsx(GenericValidationConfig.TableConfig config, Path path)
            throws IOException {
        try (InputStream input = Files.newInputStream(path);
             XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            Sheet sheet = sheet(config, workbook);
            DataFormatter formatter = new DataFormatter();
            int headerIndex = Math.max(config.getHeaderRow(), 1) - 1;
            Row headerRow = sheet.getRow(headerIndex);
            List<String> headers = configuredHeaders(config);
            if (headers.isEmpty()) {
                headers = xlsxHeaders(headerRow, formatter);
            }
            int start = Math.max(config.getDataStartRow(), headerIndex + 2) - 1;
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int rowIndex = start; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row rawRow = sheet.getRow(rowIndex);
                if (rawRow == null || isBlankRow(rawRow, headers.size(), formatter)) {
                    continue;
                }
                rows.add(xlsxRow(rawRow, headers, formatter, config));
            }
            return rows;
        }
    }

    private Sheet sheet(GenericValidationConfig.TableConfig config, XSSFWorkbook workbook) {
        if (!isBlank(config.getSheet())) {
            Sheet sheet = workbook.getSheet(config.getSheet());
            if (sheet == null) {
                throw new BadRequestException("表 " + config.getLogicalName() + " 找不到 sheet: " + config.getSheet());
            }
            return sheet;
        }
        return workbook.getSheetAt(0);
    }

    private List<String> xlsxHeaders(Row headerRow, DataFormatter formatter) {
        List<String> headers = new ArrayList<>();
        if (headerRow == null) {
            return headers;
        }
        for (int index = 0; index < headerRow.getLastCellNum(); index++) {
            String value = formatter.formatCellValue(headerRow.getCell(index)).trim();
            if (!isBlank(value)) {
                headers.add(value);
            }
        }
        return headers;
    }

    private Map<String, Object> xlsxRow(Row rawRow, List<String> headers, DataFormatter formatter,
                                        GenericValidationConfig.TableConfig config) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            row.put(headers.get(index), normalizeCell(formatter.formatCellValue(rawRow.getCell(index)), config));
        }
        return row;
    }

    private boolean isBlankRow(Row row, int size, DataFormatter formatter) {
        for (int index = 0; index < size; index++) {
            if (!isBlank(formatter.formatCellValue(row.getCell(index)))) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> row(List<String> headers, List<String> cells,
                                    GenericValidationConfig.TableConfig config) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            String value = index < cells.size() ? cells.get(index) : "";
            row.put(headers.get(index), normalizeCell(value, config));
        }
        return row;
    }

    private Map<String, Object> objectRow(Map<String, Object> source,
                                          GenericValidationConfig.TableConfig config, int rowNumber) {
        if (source == null) {
            throw new BadRequestException("表 " + config.getLogicalName() + " 第 " + rowNumber + " 行不是 JSON 对象");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            row.put(entry.getKey(), normalizeCell(entry.getValue(), config));
        }
        return row;
    }

    private List<Map<String, Object>> normalizeRows(GenericValidationConfig.TableConfig config,
                                                    List<Map<String, Object>> rows) {
        List<String> headers = configuredHeaders(config);
        if (headers.isEmpty() || rows.isEmpty()) {
            return rows;
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> rawRow : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String header : headers) {
                row.put(header, normalizeCell(rawRow.get(header), config));
            }
            normalized.add(row);
        }
        return normalized;
    }

    private List<String> headers(GenericValidationConfig.TableConfig config, List<Map<String, Object>> rows) {
        List<String> configured = configuredHeaders(config);
        if (!configured.isEmpty()) {
            return configured;
        }
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(rows.get(0).keySet());
    }

    private List<String> configuredHeaders(GenericValidationConfig.TableConfig config) {
        return config.getHeaders() == null ? new ArrayList<>() : config.getHeaders();
    }

    private List<String> parseCsvLine(String line, char delimiter) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char value = line.charAt(index);
            if (value == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (value == delimiter && !quoted) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(value);
            }
        }
        values.add(current.toString().trim());
        return values;
    }

    private Object normalizeCell(Object value, GenericValidationConfig.TableConfig config) {
        String text = value == null ? "" : value.toString().trim();
        for (String nullValue : config.getNullValues()) {
            if (text.equals(nullValue)) {
                return "";
            }
        }
        return text;
    }

    private Charset charset(GenericValidationConfig.TableConfig config) {
        if (isBlank(config.getEncoding())) {
            return StandardCharsets.UTF_8;
        }
        return Charset.forName(config.getEncoding());
    }

    private char delimiter(GenericValidationConfig.TableConfig config) {
        return isBlank(config.getDelimiter()) ? ',' : config.getDelimiter().charAt(0);
    }

    private String format(GenericValidationConfig.TableConfig config, Path path) {
        String format = config.getFormat();
        if (isBlank(format)) {
            String fileName = path.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            format = dot < 0 ? "" : fileName.substring(dot + 1);
        }
        return format.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
}
