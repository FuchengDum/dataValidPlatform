package com.example.datavalidator.service;

import com.example.datavalidator.exception.BadRequestException;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

class SqlReadOnlyGuard {
    private static final List<String> DANGEROUS = Arrays.asList(
            "update", "delete", "insert", "drop", "alter", "truncate", "merge",
            "create", "call", "exec", "execute", "replace", "grant", "revoke");
    private static final Pattern SELECT = Pattern.compile("(?is)^\\s*select\\b.*");
    private static final Pattern COMMENT = Pattern.compile("(?s)(--|/\\*|\\*/)");

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
        String lower = text.toLowerCase();
        for (String keyword : DANGEROUS) {
            if (Pattern.compile("\\b" + keyword + "\\b").matcher(lower).find()) {
                throw new BadRequestException("SQL 输入包含危险关键字: " + keyword);
            }
        }
    }

    static void requireIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z0-9_\\u4e00-\\u9fa5]+")) {
            throw new BadRequestException("表名或字段名不合法: " + identifier);
        }
    }
}
