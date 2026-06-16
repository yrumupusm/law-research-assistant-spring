package com.example.lawassistant.repository;

import com.example.lawassistant.domain.entity.Law;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LawRepository extends JpaRepository<Law, Long> {

    List<Law> findByTitleContainingIgnoreCaseOrderByTitleAsc(String title);
}
