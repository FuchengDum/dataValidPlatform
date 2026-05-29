package com.example.datavalidator.service;

import com.example.datavalidator.exception.BadRequestException;

final class GenericRuleValueNormalizer {
    private GenericRuleValueNormalizer() {
    }

    static String severity(String value) {
        if (isBlank(value)) {
            return "CRITICAL";
        }
        String text = value.trim();
        if ("严重".equals(text)) {
            return "CRITICAL";
        }
        if ("警告".equals(text)) {
            return "WARNING";
        }
        String upper = text.toUpperCase();
        if ("CRITICAL".equals(upper) || "WARNING".equals(upper)) {
            return upper;
        }
        throw new BadRequestException("严重等级不支持: " + value);
    }

    static String category(String value) {
        if (isBlank(value)) {
            return "SINGLE_BUSINESS_RULE";
        }
        String text = value.trim();
        String upper = text.toUpperCase();
        if ("SINGLE_FIELD_CONSTRAINT".equals(upper)
                || "SINGLE_BUSINESS_RULE".equals(upper)
                || "MULTI_TABLE_RELATION".equals(upper)
                || "METRIC_CONSISTENCY".equals(upper)) {
            return upper;
        }
        if (text.contains("字段约束")) {
            return "SINGLE_FIELD_CONSTRAINT";
        }
        if (text.contains("多表")) {
            return "MULTI_TABLE_RELATION";
        }
        if (text.contains("指标")) {
            return "METRIC_CONSISTENCY";
        }
        if (text.contains("单表")) {
            return "SINGLE_BUSINESS_RULE";
        }
        throw new BadRequestException("规则分类不支持: " + value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
