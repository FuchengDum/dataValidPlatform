CREATE TABLE dataset (
    dataset_id VARCHAR(64) PRIMARY KEY,
    source_type VARCHAR(32) NOT NULL,
    source_name VARCHAR(255),
    file_name VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    imported_at TIMESTAMP NOT NULL
);

CREATE TABLE data_table_snapshot (
    id VARCHAR(64) PRIMARY KEY,
    dataset_id VARCHAR(64) NOT NULL,
    sheet_name VARCHAR(255),
    logical_name VARCHAR(128) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    headers_json CLOB NOT NULL,
    row_count INTEGER NOT NULL
);

CREATE TABLE data_row_snapshot (
    id VARCHAR(64) PRIMARY KEY,
    dataset_id VARCHAR(64) NOT NULL,
    table_name VARCHAR(128) NOT NULL,
    row_index INTEGER NOT NULL,
    primary_key VARCHAR(255),
    values_json CLOB NOT NULL
);

CREATE TABLE rule_definition (
    rule_id VARCHAR(32) PRIMARY KEY,
    dataset_id VARCHAR(64) NOT NULL,
    rule_name VARCHAR(255) NOT NULL,
    category VARCHAR(64) NOT NULL,
    applicable_tables VARCHAR(255),
    description CLOB,
    pseudo_logic CLOB,
    severity VARCHAR(32) NOT NULL,
    example CLOB,
    scenario_ids VARCHAR(255),
    executor_type VARCHAR(32),
    template_code VARCHAR(64)
);

CREATE TABLE rule_binding (
    id VARCHAR(64) PRIMARY KEY,
    dataset_id VARCHAR(64) NOT NULL,
    rule_id VARCHAR(32) NOT NULL,
    executor_type VARCHAR(32) NOT NULL,
    builtin_executor_name VARCHAR(128),
    template_code VARCHAR(64),
    template_params_json CLOB
);

CREATE TABLE validation_job (
    job_id VARCHAR(64) PRIMARY KEY,
    dataset_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    enable_ai_analysis BOOLEAN NOT NULL,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    duration_millis BIGINT
);

CREATE TABLE validation_finding (
    finding_id VARCHAR(64) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    rule_id VARCHAR(32) NOT NULL,
    rule_name VARCHAR(255) NOT NULL,
    rule_category VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    table_name VARCHAR(128),
    record_key VARCHAR(255),
    field_name VARCHAR(255),
    actual_value CLOB,
    expected_value CLOB,
    description CLOB,
    reason CLOB,
    impact CLOB,
    suggestion CLOB,
    scenario_ids VARCHAR(255)
);

CREATE TABLE finding_evidence (
    id VARCHAR(64) PRIMARY KEY,
    finding_id VARCHAR(64) NOT NULL,
    evidence_type VARCHAR(64) NOT NULL,
    table_name VARCHAR(128),
    record_key VARCHAR(255),
    field_name VARCHAR(255),
    actual_value CLOB,
    expected_value CLOB,
    calculation CLOB,
    related_values_json CLOB
);

CREATE TABLE report_file (
    report_id VARCHAR(64) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    format VARCHAR(32) NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    generated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_data_table_dataset ON data_table_snapshot(dataset_id);
CREATE INDEX idx_data_row_dataset_table ON data_row_snapshot(dataset_id, table_name);
CREATE INDEX idx_rule_dataset ON rule_definition(dataset_id);
CREATE INDEX idx_finding_job ON validation_finding(job_id);
CREATE INDEX idx_finding_filter ON validation_finding(job_id, severity, table_name, rule_id);
CREATE INDEX idx_evidence_finding ON finding_evidence(finding_id);
