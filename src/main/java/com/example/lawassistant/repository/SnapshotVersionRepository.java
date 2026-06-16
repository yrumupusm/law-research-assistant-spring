package com.example.lawassistant.repository;

import com.example.lawassistant.domain.entity.SnapshotVersion;
import com.example.lawassistant.domain.enums.SnapshotStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SnapshotVersionRepository extends JpaRepository<SnapshotVersion, Long> {

    Optional<SnapshotVersion> findFirstByStatusOrderByIndexedAtDesc(SnapshotStatus status);
}
