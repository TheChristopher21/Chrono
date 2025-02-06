package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.TimeTracking;
import com.chrono.chrono.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface TimeTrackingRepository extends JpaRepository<TimeTracking, Long> {

    List<TimeTracking> findByUserOrderByStartTimeDesc(User user);

    List<TimeTracking> findByUserOrderByStartTimeAsc(User user);

    Optional<TimeTracking> findFirstByUserAndEndTimeIsNullOrderByStartTimeDesc(User user);

    // Hier definieren wir einen benutzerdefinierten Query, der alle TimeTracking-Einträge inkl. des zugehörigen Users zurückgibt.
    @Query("SELECT t FROM TimeTracking t JOIN FETCH t.user")
    List<TimeTracking> findAllWithUser();
}
