package com.example.datavalidator.repository;

import com.example.datavalidator.persistence.DatasetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasetRepository extends JpaRepository<DatasetEntity, String> {
}
