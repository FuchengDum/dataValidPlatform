package com.example.datavalidator.repository;

import com.example.datavalidator.persistence.RuleDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleDefinitionRepository extends JpaRepository<RuleDefinitionEntity, RuleDefinitionEntity.Key> {
    List<RuleDefinitionEntity> findByDatasetIdOrderByRuleId(String datasetId);
}
