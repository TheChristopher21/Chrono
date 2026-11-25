package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.MobileAppFeedback;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MobileAppFeedbackRepository extends JpaRepository<MobileAppFeedback, Long> {
    @EntityGraph(attributePaths = {"company", "user"})
    List<MobileAppFeedback> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
