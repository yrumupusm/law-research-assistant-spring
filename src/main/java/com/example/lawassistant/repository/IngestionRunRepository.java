package com.example.lawassistant.repository;

import com.example.lawassistant.domain.entity.IngestionRun;
import com.example.lawassistant.domain.enums.IngestionStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionRunRepository extends JpaRepository<IngestionRun, Long> {

    List<IngestionRun> findTop20ByOrderByStartedAtDesc();

    List<IngestionRun> findTop5ByStatusOrderByStartedAtDesc(IngestionStatus status);
}
