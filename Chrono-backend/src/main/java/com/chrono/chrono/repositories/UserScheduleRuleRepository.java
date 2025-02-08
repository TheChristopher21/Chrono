package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.UserScheduleRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserScheduleRuleRepository extends JpaRepository<UserScheduleRule, Long> {

    /**
     * Liefert alle Regeln für einen bestimmten User
     */
    List<UserScheduleRule> findByUser(User user);
}
