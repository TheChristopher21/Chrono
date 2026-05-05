package com.chrono.chrono.repositories;

import com.chrono.chrono.entities.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles LEFT JOIN FETCH u.company c LEFT JOIN FETCH c.enabledFeatures WHERE u.username = :username")
    Optional<User> findByUsernameWithPermissionContext(@Param("username") String username);
    boolean existsByUsername(String username);
    List<User> findByCompany_Id(Long companyId);
    List<User> findByCompany_IdAndDeletedFalse(Long companyId);
    List<User> findByDeletedFalse();
    @Query("""
            SELECT DISTINCT u FROM User u
            WHERE u.deleted = false
              AND NOT EXISTS (
                  SELECT r FROM u.roles r
                  WHERE r.roleName = 'ROLE_SUPERADMIN'
              )
            """)
    List<User> findOperationalUsersDeletedFalse();
    @Query("""
            SELECT DISTINCT u FROM User u
            WHERE u.company.id = :companyId
              AND u.deleted = false
              AND NOT EXISTS (
                  SELECT r FROM u.roles r
                  WHERE r.roleName = 'ROLE_SUPERADMIN'
              )
            """)
    List<User> findOperationalUsersByCompanyIdAndDeletedFalse(@Param("companyId") Long companyId);
    @Query("""
            SELECT DISTINCT u FROM User u
            WHERE u.deleted = false
              AND u.includeInTimeTracking = true
              AND NOT EXISTS (
                  SELECT r FROM u.roles r
                  WHERE r.roleName = 'ROLE_SUPERADMIN'
              )
            """)
    List<User> findTimeOverviewUsersDeletedFalse();
    @Query("""
            SELECT DISTINCT u FROM User u
            WHERE u.company.id = :companyId
              AND u.deleted = false
              AND u.includeInTimeTracking = true
              AND NOT EXISTS (
                  SELECT r FROM u.roles r
                  WHERE r.roleName = 'ROLE_SUPERADMIN'
              )
            """)
    List<User> findTimeOverviewUsersByCompanyIdAndDeletedFalse(@Param("companyId") Long companyId);
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles LEFT JOIN FETCH u.company c LEFT JOIN FETCH c.enabledFeatures")
    List<User> findAllWithPermissionContext();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(Long id);
}
