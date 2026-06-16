package com.example.lawassistant.repository;

import com.example.lawassistant.domain.entity.SyncState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncStateRepository extends JpaRepository<SyncState, Long> {
}
