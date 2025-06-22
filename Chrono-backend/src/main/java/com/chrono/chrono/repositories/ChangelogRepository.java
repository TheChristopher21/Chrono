package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.Changelog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface ChangelogRepository extends JpaRepository<Changelog, Long> {

    // Findet den neuesten Eintrag basierend auf dem Erstellungsdatum
    Optional<Changelog> findTopByOrderByCreatedAtDesc();

    // Gibt alle Einträge sortiert nach dem neuesten Datum zurück
    List<Changelog> findAllByOrderByCreatedAtDesc();
}