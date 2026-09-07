package com.healthcare.happimom.repository;

import com.healthcare.happimom.entity.FileStorage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileStorageRepository extends JpaRepository<FileStorage, Long> {
    List<FileStorage> findByUserId(Long userId);
    List<FileStorage> findByUserIdOrderByIdDesc(Long userId);
    Optional<FileStorage> findByIdAndUserId(Long id, Long userId);
}
