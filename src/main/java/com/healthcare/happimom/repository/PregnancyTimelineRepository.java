package com.healthcare.happimom.repository;

import com.healthcare.happimom.entity.PregnancyTimeline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PregnancyTimelineRepository extends JpaRepository<PregnancyTimeline, Long> {
    Optional<PregnancyTimeline> findByUserId(Long userId);
}
