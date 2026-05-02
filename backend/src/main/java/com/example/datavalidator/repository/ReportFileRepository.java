package com.example.datavalidator.repository;

import com.example.datavalidator.persistence.ReportFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportFileRepository extends JpaRepository<ReportFileEntity, String> {
}
