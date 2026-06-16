package com.example.lawassistant.repository;

import com.example.lawassistant.domain.entity.SearchLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {

    List<SearchLog> findTop50ByOrderByCreatedAtDesc();
}
