package com.chrono.chrono.repositories.pms;

import com.chrono.chrono.entities.pms.GuestProfile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GuestProfileRepository extends JpaRepository<GuestProfile, Long> {
    List<GuestProfile> findAllByCompany_IdOrderByLastNameAscFirstNameAsc(Long companyId);
    Optional<GuestProfile> findByIdAndCompany_Id(Long id, Long companyId);

    @Query("""
            select guest from GuestProfile guest
            where guest.company.id = :companyId
              and (:pattern = '%%'
                   or lower(guest.firstName) like :pattern
                   or lower(guest.lastName) like :pattern
                   or lower(coalesce(guest.email, '')) like :pattern
                   or lower(coalesce(guest.phone, '')) like :pattern)
            order by guest.updatedAt desc, guest.id desc
            """)
    List<GuestProfile> searchForOperations(@Param("companyId") Long companyId,
                                           @Param("pattern") String pattern,
                                           Pageable pageable);
}
