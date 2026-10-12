package com.zipai.repository;

import com.zipai.domain.PropertyListing;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyRepository extends JpaRepository<PropertyListing, Long> {
    List<PropertyListing> findByOwnerIdOrderByIdDesc(Long ownerId);
    List<PropertyListing> findByStatusOrderByIdDesc(String status);
    List<PropertyListing> findAllByOrderByIdDesc();
}
