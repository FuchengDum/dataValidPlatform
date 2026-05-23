package com.example.datavalidator.service;

import com.example.datavalidator.exception.BadRequestException;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

class SqlReadOnlyGuard {
    private static final List<String> DANGEROUS = Arrays.asList(
            "update", "delete", "insert", "drop", "alter", "truncate", "merge",
            "create", "call", "exec", "execute", "replace", "grant", "revoke");
    private static final List<String> DANGEROUS_FUNCTIONS = Arrays.asList(
            "sleep", "benchmark", "load_file");
    private static final Pattern SELECT = Pattern.compile("(?is)^\\s*select\\b.*");
    private static final Pattern COMMENT = Pattern.compile("(?s)(--|/\\*|\\*/)");
    private static final Pattern STAR_PROJECTION = Pattern.compile(
            "(?is)^\\s*select\\s+(distinct\\s+)?(\\*|.*(,\\s*\\*|\\.\\*)).*\\bfrom\\b.*");

    private SqlReadOnlyGuard() {
    }

    static void requireSelect(String sql) {
        String text = sql == null ? "" : sql.trim();
        if (!SELECT.matcher(text).matches()) {
            throw new BadRequestException("SQL 输入只允许 SELECT 语句");
        }
        if (COMMENT.matcher(text).find() || text.contains(";")) {
            throw new BadRequestException("SQL 输入不允许注释或多语句");
        }
        if (STAR_PROJECTION.matcher(text).matches()) {
            throw new BadRequestException("SQL 输入不允许 SELECT *，请显式声明字段。");
        }
        String lower = text.toLowerCase();
        if (Pattern.compile("\\bfor\\s+update\\b").matcher(lower).find()) {
            throw new BadRequestException("SQL 输入不允许 FOR UPDATE");
        }
        for (String keyword : DANGEROUS) {
            if (Pattern.compile("\\b" + keyword + "\\b").matcher(lower).find()) {
                throw new BadRequestException("SQL 输入包含危险关键字: " + keyword);
            }
        }
        for (String function : DANGEROUS_FUNCTIONS) {
            if (Pattern.compile("\\b" + function + "\\s*\\(").matcher(lower).find()) {
                throw new BadRequestException("SQL 输入包含危险函数: " + function);
            }
        }
    }

    static void requireIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z0-9_\\u4e00-\\u9fa5]+")) {
            throw new BadRequestException("表名或字段名不合法: " + identifier);
        }
    }

    static void requireQualifiedIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new BadRequestException("表名或字段名不合法: " + identifier);
        }
        String[] parts = identifier.split("\\.");
        if (parts.length > 2) {
            throw new BadRequestException("仅支持一层 schema-qualified 表名: " + identifier);
        }
        for (String part : parts) {
            requireIdentifier(part);
        }
    }

    static String quoteQualifiedIdentifier(String identifier, String dialect) {
        requireQualifiedIdentifier(identifier);
        String quote = "mysql".equalsIgnoreCase(dialect) ? "`" : "\"";
        String[] parts = identifier.split("\\.");
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < parts.length; index++) {
            if (index > 0) {
                builder.append('.');
            }
            builder.append(quote).append(parts[index]).append(quote);
        }
        return builder.toString();
    }

    static String quoteIdentifier(String identifier, String dialect) {
        requireIdentifier(identifier);
        String quote = "mysql".equalsIgnoreCase(dialect) ? "`" : "\"";
        return quote + identifier + quote;
    }
}
