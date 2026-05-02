package com.example.datavalidator.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class IdFactory {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private IdFactory() {
    }

    public static String next(String prefix) {
        return prefix + "-" + LocalDateTime.now().format(FORMATTER) + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
