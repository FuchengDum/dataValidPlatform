package com.example.datavalidator.repository;

import com.example.datavalidator.persistence.ValidationFindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ValidationFindingRepository extends JpaRepository<ValidationFindingEntity, String> {
    List<ValidationFindingEntity> findByJobId(String jobId);
}
