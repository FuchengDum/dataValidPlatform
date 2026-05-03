package com.example.datavalidator.service;

import java.util.Optional;

@FunctionalInterface
public interface AiChatClient {
    Optional<String> complete(String systemPrompt, String userPrompt);
}
