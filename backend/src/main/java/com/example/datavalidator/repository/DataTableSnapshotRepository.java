package com.example.datavalidator.repository;

import com.example.datavalidator.persistence.DataTableSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataTableSnapshotRepository extends JpaRepository<DataTableSnapshotEntity, String> {
    List<DataTableSnapshotEntity> findByDatasetId(String datasetId);
}
