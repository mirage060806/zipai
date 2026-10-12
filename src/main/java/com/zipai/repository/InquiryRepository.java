package com.zipai.repository;

import com.zipai.domain.Inquiry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {
    List<Inquiry> findByUserIdOrderByIdDesc(Long userId);
    Optional<Inquiry> findByIdAndUserId(Long id, Long userId);
    List<Inquiry> findAllByOrderByIdDesc();
}
