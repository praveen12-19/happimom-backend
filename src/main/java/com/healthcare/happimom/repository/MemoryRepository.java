package com.healthcare.happimom.repository;

import com.healthcare.happimom.entity.Memory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemoryRepository extends JpaRepository<Memory, Long> {

    List<Memory> findByUserId(Long userId);

    List<Memory> findByUserIdOrderByIdDesc(Long userId);

    Optional<Memory> findByIdAndUserId(Long id, Long userId);
}
