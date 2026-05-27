package com.example.datavalidator.service;

import com.example.datavalidator.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GenericRuleAssetLoader {
    private final ObjectMapper objectMapper;

    public GenericRuleAssetLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GenericValidationConfig loadConfig(Path path) {
        Object raw = read(path);
        return objectMapper.convertValue(raw, GenericValidationConfig.class);
    }

    @SuppressWarnings("unchecked")
    public GenericRulePackage loadRules(Path path) {
        Object raw = read(path);
        if (raw instanceof List) {
            Map<String, Object> wrapped = new LinkedHashMap<>();
            wrapped.put("rules", raw);
            raw = wrapped;
        }
        if (raw instanceof Map && ((Map<String, Object>) raw).containsKey("rules")) {
            return objectMapper.convertValue(raw, GenericRulePackage.class);
        }
        throw new BadRequestException("规则包必须包含 rules 列表: " + path);
    }

    @SuppressWarnings("unchecked")
    public GenericValidationConfig.SourceConfig loadSource(Path path) {
        Object raw = read(path);
        if (raw instanceof Map && ((Map<String, Object>) raw).containsKey("source")) {
            GenericValidationConfig config = objectMapper.convertValue(raw, GenericValidationConfig.class);
            return config.getSource();
        }
        return objectMapper.convertValue(raw, GenericValidationConfig.SourceConfig.class);
    }

    private Object read(Path path) {
        if (path == null || !Files.exists(path)) {
            throw new BadRequestException("配置文件不存在: " + path);
        }
        try (InputStream input = Files.newInputStream(path)) {
            String fileName = path.getFileName().toString().toLowerCase();
            if (fileName.endsWith(".json")) {
                return objectMapper.readValue(input, Object.class);
            }
            return new Yaml().load(input);
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("配置文件解析失败: " + path + "，" + ex.getMessage());
        }
    }
}
