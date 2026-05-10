package com.example.datavalidator.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class SqlIdentifierResolver {
    private final Map<String, List<String>> fieldsByTable;

    SqlIdentifierResolver(Map<String, List<String>> fieldsByTable) {
        this.fieldsByTable = fieldsByTable == null ? Collections.emptyMap() : new LinkedHashMap<>(fieldsByTable);
    }

    String table(String logicalName) {
        return quote(logicalName);
    }

    String field(String logicalName) {
        return quote(logicalName);
    }

    boolean isKnownIdentifier(String identifier) {
        if (fieldsByTable.containsKey(identifier)) {
            return true;
        }
        for (List<String> fields : fieldsByTable.values()) {
            if (fields.contains(identifier)) {
                return true;
            }
        }
        return false;
    }

    private String quote(String value) {
        return "\"" + (value == null ? "" : value).replace("\"", "\"\"") + "\"";
    }
}
