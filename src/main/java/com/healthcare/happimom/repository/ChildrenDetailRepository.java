package com.healthcare.happimom.repository;

import com.healthcare.happimom.entity.ChildrenDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChildrenDetailRepository extends JpaRepository<ChildrenDetail, Long> {
    List<ChildrenDetail> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
