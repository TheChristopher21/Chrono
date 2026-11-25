package com.chrono.chrono.repositories.pms;
import com.chrono.chrono.entities.pms.HousekeepingTaskEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
public interface HousekeepingTaskEventRepository extends JpaRepository<HousekeepingTaskEvent, Long> {
    Page<HousekeepingTaskEvent> findAllByTask_IdOrderByIdDesc(Long taskId, Pageable pageable);
}
