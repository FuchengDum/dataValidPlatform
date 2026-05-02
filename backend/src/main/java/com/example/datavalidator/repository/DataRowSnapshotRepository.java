package com.example.datavalidator.repository;

import com.example.datavalidator.persistence.DataRowSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataRowSnapshotRepository extends JpaRepository<DataRowSnapshotEntity, String> {
    List<DataRowSnapshotEntity> findByDatasetIdAndTableNameOrderByRowIndex(String datasetId, String tableName);
    List<DataRowSnapshotEntity> findByDatasetId(String datasetId);
}
