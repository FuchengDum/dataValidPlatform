package com.example.datavalidator.service;

import com.example.datavalidator.config.AppProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OpenAiCompatibleChatClient implements AiChatClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleChatClient.class);
    private static final AtomicLong CALL_SEQUENCE = new AtomicLong();

    private final AppProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public OpenAiCompatibleChatClient(AppProperties properties,
                                      RestTemplateBuilder restTemplateBuilder,
                                      ObjectMapper objectMapper) {
        this(properties, restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(properties.getAi().getTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(properties.getAi().getTimeoutSeconds()))
                .build(), objectMapper);
    }

    OpenAiCompatibleChatClient(AppProperties properties, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<String> complete(String systemPrompt, String userPrompt) {
        long callId = CALL_SEQUENCE.incrementAndGet();
        AppProperties.Ai ai = properties.getAi();
        if (!ai.isEnabled() || isBlank(ai.getEndpoint()) || isBlank(ai.getModel())) {
            log.info("AI 模型调用跳过: callId={}, enabled={}, endpointConfigured={}, modelConfigured={}",
                    callId, ai.isEnabled(), !isBlank(ai.getEndpoint()), !isBlank(ai.getModel()));
            return Optional.empty();
        }
        String url = chatCompletionsUrl(ai.getEndpoint());
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (!isBlank(ai.getApiKey())) {
                headers.setBearerAuth(ai.getApiKey());
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", ai.getModel());
            body.put("temperature", 0.2);
            body.put("messages", Arrays.asList(
                    message("system", systemPrompt),
                    message("user", userPrompt)
            ));
            log.info("AI 模型请求参数: callId={}, url={}, model={}, requestBody={}",
                    callId, url, ai.getModel(), toJson(body));
            String response = restTemplate.postForObject(url,
                    new HttpEntity<>(body, headers), String.class);
            log.info("AI 模型响应原文: callId={}, response={}", callId, response);
            Optional<String> content = extractContent(response);
            log.info("AI 模型响应内容: callId={}, hasContent={}, content={}",
                    callId, content.isPresent(), content.orElse(""));
            return content;
        } catch (Exception ex) {
            log.warn("AI 模型调用失败: callId={}, url={}, model={}, error={}",
                    callId, url, ai.getModel(), ex.toString(), ex);
            return Optional.empty();
        }
    }

    private Optional<String> extractContent(String response) throws Exception {
        if (isBlank(response)) {
            return Optional.empty();
        }
        Map<String, Object> root = objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {});
        Object choicesValue = root.get("choices");
        if (!(choicesValue instanceof List) || ((List<?>) choicesValue).isEmpty()) {
            return Optional.empty();
        }
        Object firstChoice = ((List<?>) choicesValue).get(0);
        if (!(firstChoice instanceof Map)) {
            return Optional.empty();
        }
        Object messageValue = ((Map<?, ?>) firstChoice).get("message");
        if (!(messageValue instanceof Map)) {
            return Optional.empty();
        }
        Object content = ((Map<?, ?>) messageValue).get("content");
        return content == null ? Optional.empty() : Optional.of(content.toString());
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String chatCompletionsUrl(String endpoint) {
        String trimmed = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        if (trimmed.endsWith("/v1/chat/completions")) {
            return trimmed;
        }
        return trimmed + "/v1/chat/completions";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
