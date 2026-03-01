package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.NfcCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface NfcCommandRepository extends JpaRepository<NfcCommand, Long> {
    Optional<NfcCommand> findFirstByStatusOrderByCreatedAtAsc(String status);
}
