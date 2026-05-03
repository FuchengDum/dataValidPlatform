package com.example.datavalidator.service;

import com.example.datavalidator.domain.DataRow;
import com.example.datavalidator.exception.BadRequestException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class RowExpressionEvaluator {
    private static final List<String> COMPARISON_OPERATORS = Arrays.asList("==", "!=", ">", ">=", "<", "<=");
    private static final List<String> ARITHMETIC_OPERATORS = Arrays.asList("+", "-", "*", "/");

    private RowExpressionEvaluator() {
    }

    static Optional<Result> evaluate(Object rawConditions, DataRow row) {
        for (Map<String, Object> condition : conditions(rawConditions)) {
            Result result = evaluateCondition(condition, row);
            if (!result.isSatisfied()) {
                return Optional.of(result);
            }
        }
        return Optional.empty();
    }

    static void validate(Object rawConditions, List<String> headers) {
        List<Map<String, Object>> conditions = conditions(rawConditions);
        if (conditions.isEmpty()) {
            throw new BadRequestException("模板参数 conditions 不能为空");
        }
        for (Map<String, Object> condition : conditions) {
            String operator = stringValue(condition.get("operator"));
            if (!COMPARISON_OPERATORS.contains(operator)) {
                throw new BadRequestException("不支持的行表达式操作符: " + operator);
            }
            validateNode(condition.get("left"), headers);
            validateNode(condition.get("right"), headers);
        }
    }

    private static Result evaluateCondition(Map<String, Object> condition, DataRow row) {
        ExpressionValue left = evaluateNode(condition.get("left"), row);
        ExpressionValue right = evaluateNode(condition.get("right"), row);
        String operator = stringValue(condition.get("operator"));
        boolean satisfied = compare(left, operator, right);
        return new Result(satisfied, left.firstField(), left.text + " " + operator + " " + right.text,
                left.summary() + "；" + right.summary(), left.text, operator, right.text);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> conditions(Object rawConditions) {
        if (!(rawConditions instanceof List)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : (List<?>) rawConditions) {
            if (item instanceof Map) {
                Map<String, Object> condition = new LinkedHashMap<>();
                ((Map<?, ?>) item).forEach((key, value) -> condition.put(String.valueOf(key), value));
                result.add(condition);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void validateNode(Object rawNode, List<String> headers) {
        if (rawNode instanceof Number || rawNode instanceof String) {
            return;
        }
        if (!(rawNode instanceof Map)) {
            throw new BadRequestException("行表达式节点格式不支持");
        }
        Map<String, Object> node = (Map<String, Object>) rawNode;
        if (node.containsKey("if")) {
            validatePredicate(node.get("if"), headers);
            validateNode(node.get("then"), headers);
            validateNode(node.get("else"), headers);
            return;
        }
        String field = stringValue(node.get("field"));
        if (!ValueParsers.isBlank(field)) {
            if (!headers.contains(field)) {
                throw new BadRequestException("字段不存在: " + field);
            }
            return;
        }
        if (node.containsKey("literal") || node.containsKey("value")) {
            return;
        }
        String operator = stringValue(node.get("op"));
        if (!ARITHMETIC_OPERATORS.contains(operator)) {
            throw new BadRequestException("不支持的行表达式算术操作符: " + operator);
        }
        validateNode(node.get("left"), headers);
        validateNode(node.get("right"), headers);
    }

    @SuppressWarnings("unchecked")
    private static void validatePredicate(Object rawPredicate, List<String> headers) {
        if (!(rawPredicate instanceof Map)) {
            throw new BadRequestException("行表达式 if 条件格式不支持");
        }
        Map<String, Object> predicate = (Map<String, Object>) rawPredicate;
        String operator = stringValue(predicate.get("operator"));
        if (!COMPARISON_OPERATORS.contains(operator)) {
            throw new BadRequestException("不支持的行表达式 if 操作符: " + operator);
        }
        validateNode(predicate.get("left"), headers);
        validateNode(predicate.get("right"), headers);
    }

    @SuppressWarnings("unchecked")
    private static ExpressionValue evaluateNode(Object rawNode, DataRow row) {
        if (rawNode instanceof Number || rawNode instanceof String) {
            String value = stringValue(rawNode);
            return new ExpressionValue(value, value, ValueParsers.decimal(value), "");
        }
        if (!(rawNode instanceof Map)) {
            return ExpressionValue.unavailable("未知表达式");
        }
        Map<String, Object> node = (Map<String, Object>) rawNode;
        if (node.containsKey("if")) {
            return evaluateConditionalNode(node, row);
        }
        String field = stringValue(node.get("field"));
        if (!ValueParsers.isBlank(field)) {
            String value = row.value(field);
            return new ExpressionValue(field, value, ValueParsers.decimal(value), field);
        }
        if (node.containsKey("literal") || node.containsKey("value")) {
            Object literal = node.containsKey("literal") ? node.get("literal") : node.get("value");
            String value = stringValue(literal);
            return new ExpressionValue(value, value, ValueParsers.decimal(value), "");
        }
        ExpressionValue left = evaluateNode(node.get("left"), row);
        ExpressionValue right = evaluateNode(node.get("right"), row);
        String operator = stringValue(node.get("op"));
        String text = left.text + " " + operator + " " + right.text;
        Optional<BigDecimal> calculated = calculate(left, operator, right);
        return new ExpressionValue(text, calculated.map(RowExpressionEvaluator::formatDecimal).orElse("无法计算"),
                calculated, left.firstField());
    }

    private static ExpressionValue evaluateConditionalNode(Map<String, Object> node, DataRow row) {
        Result predicate = evaluateCondition(asMap(node.get("if")), row);
        ExpressionValue selected = evaluateNode(predicate.isSatisfied() ? node.get("then") : node.get("else"), row);
        String text = "if " + predicate.getFailedCondition() + " then "
                + expressionText(node.get("then")) + " else " + expressionText(node.get("else"));
        return new ExpressionValue(text, selected.rawValue, selected.decimal, selected.firstField());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<>();
            ((Map<?, ?>) value).forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private static String expressionText(Object rawNode) {
        if (rawNode instanceof Number || rawNode instanceof String) {
            return stringValue(rawNode);
        }
        if (!(rawNode instanceof Map)) {
            return "未知表达式";
        }
        Map<String, Object> node = (Map<String, Object>) rawNode;
        String field = stringValue(node.get("field"));
        if (!ValueParsers.isBlank(field)) {
            return field;
        }
        if (node.containsKey("literal") || node.containsKey("value")) {
            Object literal = node.containsKey("literal") ? node.get("literal") : node.get("value");
            return stringValue(literal);
        }
        if (node.containsKey("if")) {
            return "if " + expressionText(asMap(node.get("if")).get("left")) + " "
                    + stringValue(asMap(node.get("if")).get("operator")) + " "
                    + expressionText(asMap(node.get("if")).get("right")) + " then "
                    + expressionText(node.get("then")) + " else " + expressionText(node.get("else"));
        }
        return expressionText(node.get("left")) + " " + stringValue(node.get("op")) + " "
                + expressionText(node.get("right"));
    }

    private static Optional<BigDecimal> calculate(ExpressionValue left, String operator, ExpressionValue right) {
        if (!left.decimal.isPresent() || !right.decimal.isPresent()) {
            return Optional.empty();
        }
        BigDecimal leftValue = left.decimal.get();
        BigDecimal rightValue = right.decimal.get();
        switch (operator) {
            case "+": return Optional.of(leftValue.add(rightValue));
            case "-": return Optional.of(leftValue.subtract(rightValue));
            case "*": return Optional.of(leftValue.multiply(rightValue));
            case "/":
                if (rightValue.compareTo(BigDecimal.ZERO) == 0) {
                    return Optional.empty();
                }
                return Optional.of(leftValue.divide(rightValue, 10, RoundingMode.HALF_UP).stripTrailingZeros());
            default: return Optional.empty();
        }
    }

    private static boolean compare(ExpressionValue left, String operator, ExpressionValue right) {
        if (left.decimal.isPresent() && right.decimal.isPresent()) {
            int compared = left.decimal.get().compareTo(right.decimal.get());
            switch (operator) {
                case "==": return compared == 0;
                case "!=": return compared != 0;
                case ">": return compared > 0;
                case ">=": return compared >= 0;
                case "<": return compared < 0;
                case "<=": return compared <= 0;
                default: return false;
            }
        }
        switch (operator) {
            case "==": return left.rawValue.equals(right.rawValue);
            case "!=": return !left.rawValue.equals(right.rawValue);
            default: return false;
        }
    }

    private static String formatDecimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static class ExpressionValue {
        private final String text;
        private final String rawValue;
        private final Optional<BigDecimal> decimal;
        private final String firstField;

        ExpressionValue(String text, String rawValue, Optional<BigDecimal> decimal, String firstField) {
            this.text = text;
            this.rawValue = rawValue;
            this.decimal = decimal;
            this.firstField = firstField;
        }

        static ExpressionValue unavailable(String text) {
            return new ExpressionValue(text, "无法计算", Optional.empty(), "");
        }

        String summary() {
            return text + "=" + rawValue;
        }

        String firstField() {
            return firstField;
        }
    }

    static class Result {
        private final boolean satisfied;
        private final String fieldName;
        private final String failedCondition;
        private final String actualSummary;
        private final String leftText;
        private final String operator;
        private final String rightText;

        Result(boolean satisfied, String fieldName, String failedCondition, String actualSummary,
               String leftText, String operator, String rightText) {
            this.satisfied = satisfied;
            this.fieldName = fieldName;
            this.failedCondition = failedCondition;
            this.actualSummary = actualSummary;
            this.leftText = leftText;
            this.operator = operator;
            this.rightText = rightText;
        }

        boolean isSatisfied() { return satisfied; }
        String getFieldName() { return fieldName; }
        String getFailedCondition() { return failedCondition; }
        String getActualSummary() { return actualSummary; }
        String getLeftText() { return leftText; }
        String getOperator() { return operator; }
        String getRightText() { return rightText; }
    }
}
