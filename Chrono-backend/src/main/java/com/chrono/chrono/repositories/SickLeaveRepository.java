package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.SickLeave;
import com.chrono.chrono.entities.User;
import jakarta.transaction.Transactional; // Für @Modifying und @Transactional
import org.springframework.data.jpa.repository.EntityGraph; // NEU importieren
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SickLeaveRepository extends JpaRepository<SickLeave, Long> {

    @EntityGraph(attributePaths = {"user"}) // NEU: User-Entität mitladen
    List<SickLeave> findByUser(User user);

    @EntityGraph(attributePaths = {"user"}) // NEU: User-Entität mitladen
    List<SickLeave> findByUser_Company_Id(Long companyId);

    @Override
    @EntityGraph(attributePaths = {"user"}) // NEU: User-Entität auch bei findAll mitladen
    List<SickLeave> findAll();

    @Modifying
    @Transactional
    @Query("DELETE FROM SickLeave sl WHERE sl.user = :user")
    void deleteByUser(@Param("user") User user);
}