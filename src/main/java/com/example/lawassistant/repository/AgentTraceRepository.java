package com.example.lawassistant.repository;

import com.example.lawassistant.domain.entity.AgentTrace;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentTraceRepository extends JpaRepository<AgentTrace, Long> {

    List<AgentTrace> findTop100ByOrderByCreatedAtDesc();

    List<AgentTrace> findTop100ByRequestIdOrderByCreatedAtAsc(String requestId);

    long countByRequestId(String requestId);
}
