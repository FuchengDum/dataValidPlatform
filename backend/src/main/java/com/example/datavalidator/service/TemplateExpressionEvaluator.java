package com.example.datavalidator.service;

import com.example.datavalidator.domain.DataRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class TemplateExpressionEvaluator {
    private TemplateExpressionEvaluator() {
    }

    static Optional<Result> evaluate(String expression, DataRow row) {
        Result first = null;
        for (String condition : expression.split("\\s+&&\\s+")) {
            Optional<Result> result = evaluateSingle(condition, row);
            if (!result.isPresent()) {
                return Optional.empty();
            }
            if (first == null) {
                first = result.get();
            }
            if (!result.get().isSatisfied()) {
                return result;
            }
        }
        return Optional.ofNullable(first);
    }

    static boolean isValidExpression(String expression, List<String> headers) {
        if (ValueParsers.isBlank(expression)) {
            return false;
        }
        for (String condition : expression.split("\\s+&&\\s+")) {
            Optional<ComparisonExpression> parsed = parseComparison(condition);
            if (!parsed.isPresent()) {
                return false;
            }
            ComparisonExpression comparison = parsed.get();
            if (!headers.contains(comparison.leftField)
                    || !isValidArithmeticExpression(comparison.rightExpression, headers)) {
                return false;
            }
        }
        return true;
    }

    private static Optional<Result> evaluateSingle(String expression, DataRow row) {
        Optional<ComparisonExpression> parsed = parseComparison(expression);
        if (!parsed.isPresent()) {
            return Optional.empty();
        }
        ComparisonExpression comparison = parsed.get();
        Optional<BigDecimal> actual = ValueParsers.decimal(row.value(comparison.leftField));
        Optional<BigDecimal> expected = evaluateArithmetic(comparison.rightExpression, row);
        boolean satisfied = actual.isPresent() && expected.isPresent()
                && compare(actual.get(), expected.get(), comparison.operator);
        return Optional.of(new Result(comparison.leftField, row.value(comparison.leftField),
                comparison.operator, comparison.rightExpression, formatValue(expected), satisfied));
    }

    private static Optional<ComparisonExpression> parseComparison(String expression) {
        for (String operator : new String[]{"==", "!=", ">=", "<=", "=", ">", "<"}) {
            int index = expression.indexOf(operator);
            if (index > 0) {
                String left = expression.substring(0, index).trim();
                String right = expression.substring(index + operator.length()).trim();
                if (!ValueParsers.isBlank(left) && !ValueParsers.isBlank(right)) {
                    return Optional.of(new ComparisonExpression(left, operator, right));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<BigDecimal> evaluateArithmetic(String expression, DataRow row) {
        String[] tokens = expression.trim().split("\\s+");
        if (tokens.length == 0 || tokens.length % 2 == 0) {
            return Optional.empty();
        }
        List<BigDecimal> terms = new ArrayList<>();
        List<String> plusMinus = new ArrayList<>();
        Optional<BigDecimal> current = valueOf(tokens[0], row);
        if (!current.isPresent()) {
            return Optional.empty();
        }
        BigDecimal term = current.get();
        for (int i = 1; i < tokens.length; i += 2) {
            Optional<BigDecimal> nextValue = valueOf(tokens[i + 1], row);
            if (!nextValue.isPresent()) {
                return Optional.empty();
            }
            String operator = tokens[i];
            BigDecimal next = nextValue.get();
            if ("*".equals(operator)) {
                term = term.multiply(next);
            } else if ("/".equals(operator)) {
                if (next.compareTo(BigDecimal.ZERO) == 0) {
                    return Optional.empty();
                }
                term = term.divide(next, 10, RoundingMode.HALF_UP);
            } else if ("+".equals(operator) || "-".equals(operator)) {
                terms.add(term);
                plusMinus.add(operator);
                term = next;
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(applyPlusMinus(terms, plusMinus, term));
    }

    private static boolean isValidArithmeticExpression(String expression, List<String> headers) {
        if (ValueParsers.isBlank(expression)) {
            return false;
        }
        String[] tokens = expression.trim().split("\\s+");
        if (tokens.length == 0 || tokens.length % 2 == 0 || !isFieldOrNumber(tokens[0], headers)) {
            return false;
        }
        for (int i = 1; i < tokens.length; i += 2) {
            if (!isArithmeticOperator(tokens[i]) || !isFieldOrNumber(tokens[i + 1], headers)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isArithmeticOperator(String token) {
        return "+".equals(token) || "-".equals(token) || "*".equals(token) || "/".equals(token);
    }

    private static boolean isFieldOrNumber(String token, List<String> headers) {
        return headers.contains(token) || ValueParsers.decimal(token).isPresent();
    }

    private static BigDecimal applyPlusMinus(List<BigDecimal> terms, List<String> plusMinus, BigDecimal lastTerm) {
        terms.add(lastTerm);
        BigDecimal result = terms.get(0);
        for (int i = 1; i < terms.size(); i++) {
            if ("+".equals(plusMinus.get(i - 1))) {
                result = result.add(terms.get(i));
            } else {
                result = result.subtract(terms.get(i));
            }
        }
        return result;
    }

    private static Optional<BigDecimal> valueOf(String token, DataRow row) {
        Optional<BigDecimal> literal = ValueParsers.decimal(token);
        if (literal.isPresent()) {
            return literal;
        }
        return ValueParsers.decimal(row.value(token));
    }

    private static boolean compare(BigDecimal actual, BigDecimal expected, String operator) {
        int comparison = actual.compareTo(expected);
        switch (operator) {
            case "==":
            case "=":
                return comparison == 0;
            case "!=":
                return comparison != 0;
            case ">=":
                return comparison >= 0;
            case "<=":
                return comparison <= 0;
            case ">":
                return comparison > 0;
            case "<":
                return comparison < 0;
            default:
                return false;
        }
    }

    private static String formatValue(Optional<BigDecimal> value) {
        if (!value.isPresent()) {
            return "无法计算";
        }
        return value.get().stripTrailingZeros().toPlainString();
    }

    static class Result {
        private final String leftField;
        private final String leftValue;
        private final String operator;
        private final String rightExpression;
        private final String rightValue;
        private final boolean satisfied;

        Result(String leftField, String leftValue, String operator, String rightExpression,
               String rightValue, boolean satisfied) {
            this.leftField = leftField;
            this.leftValue = leftValue;
            this.operator = operator;
            this.rightExpression = rightExpression;
            this.rightValue = rightValue;
            this.satisfied = satisfied;
        }

        String getLeftField() {
            return leftField;
        }

        String getActualSummary() {
            return leftField + "=" + leftValue + "；" + rightExpression + "=" + rightValue;
        }

        String getFailedCondition() {
            return leftField + " " + operator + " " + rightExpression;
        }

        boolean isSatisfied() {
            return satisfied;
        }
    }

    private static class ComparisonExpression {
        private final String leftField;
        private final String operator;
        private final String rightExpression;

        ComparisonExpression(String leftField, String operator, String rightExpression) {
            this.leftField = leftField;
            this.operator = operator;
            this.rightExpression = rightExpression;
        }
    }
}
