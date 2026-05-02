package com.example.datavalidator.persistence;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

@Entity
@Table(name = "finding_evidence")
public class FindingEvidenceEntity {
    @Id
    private String id;
    @Column(name = "finding_id", nullable = false)
    private String findingId;
    @Column(name = "evidence_type", nullable = false)
    private String evidenceType;
    @Column(name = "table_name")
    private String tableName;
    @Column(name = "record_key")
    private String recordKey;
    @Column(name = "field_name")
    private String fieldName;
    @Lob
    @Column(name = "actual_value")
    private String actualValue;
    @Lob
    @Column(name = "expected_value")
    private String expectedValue;
    @Lob
    @Column(name = "calculation")
    private String calculation;
    @Lob
    @Column(name = "related_values_json")
    private String relatedValuesJson;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFindingId() { return findingId; }
    public void setFindingId(String findingId) { this.findingId = findingId; }
    public String getEvidenceType() { return evidenceType; }
    public void setEvidenceType(String evidenceType) { this.evidenceType = evidenceType; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getRecordKey() { return recordKey; }
    public void setRecordKey(String recordKey) { this.recordKey = recordKey; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getActualValue() { return actualValue; }
    public void setActualValue(String actualValue) { this.actualValue = actualValue; }
    public String getExpectedValue() { return expectedValue; }
    public void setExpectedValue(String expectedValue) { this.expectedValue = expectedValue; }
    public String getCalculation() { return calculation; }
    public void setCalculation(String calculation) { this.calculation = calculation; }
    public String getRelatedValuesJson() { return relatedValuesJson; }
    public void setRelatedValuesJson(String relatedValuesJson) { this.relatedValuesJson = relatedValuesJson; }
}
