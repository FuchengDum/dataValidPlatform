package com.example.datavalidator.service;

import java.util.ArrayList;
import java.util.List;

public class GenericLintResult {
    private boolean valid = true;
    private List<GenericLintIssue> errors = new ArrayList<>();
    private List<GenericLintIssue> warnings = new ArrayList<>();

    public boolean isValid() {
        return errors.isEmpty();
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public List<GenericLintIssue> getErrors() { return errors; }
    public void setErrors(List<GenericLintIssue> errors) { this.errors = errors; }
    public List<GenericLintIssue> getWarnings() { return warnings; }
    public void setWarnings(List<GenericLintIssue> warnings) { this.warnings = warnings; }

    void error(String code, String message, String path, String suggestion) {
        errors.add(new GenericLintIssue(code, message, path, suggestion));
        valid = false;
    }

    void warning(String code, String message, String path, String suggestion) {
        warnings.add(new GenericLintIssue(code, message, path, suggestion));
    }
}
