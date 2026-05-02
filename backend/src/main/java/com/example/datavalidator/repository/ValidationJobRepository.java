package com.example.datavalidator.repository;

import com.example.datavalidator.persistence.ValidationJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ValidationJobRepository extends JpaRepository<ValidationJobEntity, String> {
}
