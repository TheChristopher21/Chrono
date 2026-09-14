package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.BookingEngineSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingEngineSettingsRepository extends JpaRepository<BookingEngineSettings, Long> {
    Optional<BookingEngineSettings> findByProperty_Id(Long propertyId);
    Optional<BookingEngineSettings> findByPublicSlugIgnoreCaseAndEnabledTrue(String publicSlug);
    boolean existsByPublicSlugIgnoreCaseAndProperty_IdNot(String publicSlug, Long propertyId);
}
