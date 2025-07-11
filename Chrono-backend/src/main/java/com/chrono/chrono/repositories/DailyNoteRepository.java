package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.DailyNote;
import com.chrono.chrono.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyNoteRepository extends JpaRepository<DailyNote, Long> {
    Optional<DailyNote> findByUserAndNoteDate(User user, LocalDate noteDate);
}
