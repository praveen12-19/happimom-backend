package com.healthcare.happimom.repository;

import com.healthcare.happimom.entity.PartnerDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PartnerDetailRepository extends JpaRepository<PartnerDetail, Long> {
    Optional<PartnerDetail> findByUserId(Long userId);
}
