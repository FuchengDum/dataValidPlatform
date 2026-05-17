package com.example.datavalidator.service;

public class GenericLintIssue {
    private String code;
    private String message;
    private String path;
    private String suggestion;

    public GenericLintIssue() {
    }

    public GenericLintIssue(String code, String message, String path, String suggestion) {
        this.code = code;
        this.message = message;
        this.path = path;
        this.suggestion = suggestion;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
}
