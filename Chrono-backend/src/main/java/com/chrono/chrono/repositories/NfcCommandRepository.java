package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.NfcCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Pageable;

@Repository
public interface NfcCommandRepository extends JpaRepository<NfcCommand, Long> {
    Optional<NfcCommand> findByIdAndCompanyId(Long id, Long companyId);
    Optional<NfcCommand> findByIdAndCompanyIdAndDeviceId(Long id, Long companyId, String deviceId);
    List<NfcCommand> findByCompanyIdAndDeviceIdAndStatusOrderByCreatedAtAscIdAsc(
            Long companyId, String deviceId, String status, Pageable pageable);
}
