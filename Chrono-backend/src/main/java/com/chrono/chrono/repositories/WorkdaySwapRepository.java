package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.WorkdaySwap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface WorkdaySwapRepository extends JpaRepository<WorkdaySwap, Long> {
    @Query("""
            select swap from WorkdaySwap swap
            where swap.user = :user
              and (swap.originalWorkDate between :startDate and :endDate
                   or swap.replacementWorkDate between :startDate and :endDate)
            order by swap.originalWorkDate, swap.id
            """)
    List<WorkdaySwap> findByUserAndDateRange(
            @Param("user") User user,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            select swap from WorkdaySwap swap
            where swap.user = :user
              and (swap.originalWorkDate in :dates or swap.replacementWorkDate in :dates)
            """)
    List<WorkdaySwap> findConflicts(@Param("user") User user, @Param("dates") List<LocalDate> dates);
}
