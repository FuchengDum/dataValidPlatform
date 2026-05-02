package com.example.datavalidator.repository;

import com.example.datavalidator.persistence.FindingEvidenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FindingEvidenceRepository extends JpaRepository<FindingEvidenceEntity, String> {
    List<FindingEvidenceEntity> findByFindingId(String findingId);
}
