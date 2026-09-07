package com.healthcare.happimom.repository;

import com.healthcare.happimom.entity.MotherDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MotherDetailRepository extends JpaRepository<MotherDetail, Long> {
    Optional<MotherDetail> findByUserId(Long userId);
}
