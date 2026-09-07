package com.healthcare.happimom.repository;

import com.healthcare.happimom.entity.DoctorClinicSupport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DoctorClinicSupportRepository extends JpaRepository<DoctorClinicSupport, Long> {
    Optional<DoctorClinicSupport> findByUserId(Long userId);
}
