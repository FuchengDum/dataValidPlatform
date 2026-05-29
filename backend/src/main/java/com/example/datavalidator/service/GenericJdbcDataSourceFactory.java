package com.example.datavalidator.service;

import com.example.datavalidator.exception.BadRequestException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

class GenericJdbcDataSourceFactory {
    Connection open(GenericValidationConfig.JdbcConfig config) {
        try {
            JdbcCredentialGuard.requireSafe(config);
            Class.forName(config.getDriverClassName());
            DriverManager.setLoginTimeout(Math.max(config.getConnectionTimeoutMs(), 1000) / 1000);
            Connection connection = DriverManager.getConnection(config.getUrl(), properties(config));
            connection.setReadOnly(true);
            return connection;
        } catch (ClassNotFoundException ex) {
            throw new BadRequestException("JDBC 驱动不存在: " + config.getDriverClassName());
        } catch (SQLException ex) {
            throw new BadRequestException("JDBC 只读连接失败: " + ex.getMessage());
        }
    }

    private Properties properties(GenericValidationConfig.JdbcConfig config) {
        Properties properties = new Properties();
        if (!isBlank(config.getUsername())) {
            properties.setProperty("user", config.getUsername());
        }
        String password = password(config);
        if (!isBlank(password)) {
            properties.setProperty("password", password);
        }
        return properties;
    }

    private String password(GenericValidationConfig.JdbcConfig config) {
        if (isBlank(config.getPasswordEnv())) {
            return "";
        }
        String password = System.getenv(config.getPasswordEnv());
        if (password == null) {
            throw new BadRequestException("JDBC 密码环境变量不存在: " + config.getPasswordEnv());
        }
        return password;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
