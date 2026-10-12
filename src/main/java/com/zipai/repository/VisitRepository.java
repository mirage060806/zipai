package com.zipai.repository;

import com.zipai.domain.VisitRequest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitRepository extends JpaRepository<VisitRequest, Long> {
    List<VisitRequest> findByRequesterIdOrderByIdDesc(Long requesterId);
}
