ALTER TABLE rule_definition DROP PRIMARY KEY;

ALTER TABLE rule_definition ADD PRIMARY KEY (dataset_id, rule_id);
