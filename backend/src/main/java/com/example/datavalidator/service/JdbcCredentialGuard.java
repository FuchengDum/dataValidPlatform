package com.example.datavalidator.service;

import com.example.datavalidator.exception.BadRequestException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class JdbcCredentialGuard {
    private static final Pattern URL_SECRET_PARAM = Pattern.compile(
            "(?i)([?&;])\\s*(password|pwd|token|secret)\\s*=");

    private JdbcCredentialGuard() {
    }

    static void requireSafe(GenericValidationConfig.JdbcConfig config) {
        String secret = sensitiveUrlParameter(config.getUrl());
        if (secret != null) {
            throw new BadRequestException("JDBC URL 不允许包含敏感参数: " + secret);
        }
        if (!isBlank(config.getPassword())) {
            throw new BadRequestException("JDBC 配置不允许明文 password，请改用 passwordEnv");
        }
        if ("mysql".equalsIgnoreCase(config.getDialect()) && isBlank(config.getPasswordEnv())) {
            throw new BadRequestException("MySQL JDBC 配置必须使用 passwordEnv");
        }
    }

    static String sensitiveUrlParameter(String url) {
        if (isBlank(url)) {
            return null;
        }
        Matcher matcher = URL_SECRET_PARAM.matcher(url);
        if (matcher.find()) {
            return matcher.group(2).toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
