package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.PmsPropertyGrant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PmsPropertyGrantRepository extends JpaRepository<PmsPropertyGrant, Long> {
    @EntityGraph(attributePaths = {"property", "property.company"})
    List<PmsPropertyGrant> findByUser_IdAndProperty_Company_Id(Long userId, Long companyId);
    @EntityGraph(attributePaths = {"property", "property.company"})
    List<PmsPropertyGrant> findByUser_IdInAndProperty_Company_Id(java.util.Collection<Long> userIds,Long companyId);
    void deleteByUser_Id(Long userId);
}
