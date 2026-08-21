package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.ChannelMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChannelMappingRepository extends JpaRepository<ChannelMapping, Long> {
    List<ChannelMapping> findAllByConnection_IdOrderByExternalRoomCodeAsc(Long connectionId);
}
