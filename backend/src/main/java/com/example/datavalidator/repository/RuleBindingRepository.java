package com.example.datavalidator.repository;

import com.example.datavalidator.persistence.RuleBindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RuleBindingRepository extends JpaRepository<RuleBindingEntity, String> {
    List<RuleBindingEntity> findByDatasetId(String datasetId);

    Optional<RuleBindingEntity> findByDatasetIdAndRuleId(String datasetId, String ruleId);
}
