package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.ChannelConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ChannelConnectionRepository extends JpaRepository<ChannelConnection, Long> {
    List<ChannelConnection> findAllByProperty_IdOrderByDisplayNameAsc(Long propertyId);
    Optional<ChannelConnection> findByIdAndProperty_Company_Id(Long id, Long companyId);
    Optional<ChannelConnection> findByProperty_CodeIgnoreCaseAndProviderCodeIgnoreCase(
            String propertyCode, String providerCode);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChannelConnection c where c.webhookKey = :webhookKey")
    Optional<ChannelConnection> findByWebhookKey(@Param("webhookKey") String webhookKey);
    boolean existsByProperty_IdAndProviderCodeIgnoreCase(Long propertyId, String providerCode);
}
