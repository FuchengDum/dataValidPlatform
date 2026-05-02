package com.example.datavalidator.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public final class ValueParsers {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ValueParsers() {
    }

    public static Optional<BigDecimal> decimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(value.trim()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public static Optional<LocalDateTime> dateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(value.trim(), DATE_TIME));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
