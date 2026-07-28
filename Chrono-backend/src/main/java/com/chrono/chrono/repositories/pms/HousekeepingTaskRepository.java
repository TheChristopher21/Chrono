package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.HousekeepingTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HousekeepingTaskRepository extends JpaRepository<HousekeepingTask, Long> {
    List<HousekeepingTask> findAllByProperty_IdAndServiceDateOrderByPriorityDescRoom_NumberAsc(
            Long propertyId,
            LocalDate serviceDate
    );
    Optional<HousekeepingTask> findByIdAndProperty_Company_Id(Long id, Long companyId);
    Optional<HousekeepingTask> findByRoom_IdAndServiceDate(Long roomId, LocalDate serviceDate);
}
