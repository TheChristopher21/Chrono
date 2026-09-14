package com.chrono.chrono.repositories.pms;
import com.chrono.chrono.entities.User;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.util.*;
public interface PmsUserDirectoryRepository extends Repository<User,Long> {
 @Query(value="select u.id from User u where u.company.id=:company and u.deleted=false and (lower(u.username) like :query escape '!' or lower(coalesce(u.firstName,'')) like :query escape '!' or lower(coalesce(u.lastName,'')) like :query escape '!' or lower(concat(concat(coalesce(u.firstName,''),' '),coalesce(u.lastName,''))) like :query escape '!') order by lower(u.username),u.id",
 countQuery="select count(u) from User u where u.company.id=:company and u.deleted=false and (lower(u.username) like :query escape '!' or lower(coalesce(u.firstName,'')) like :query escape '!' or lower(coalesce(u.lastName,'')) like :query escape '!' or lower(concat(concat(coalesce(u.firstName,''),' '),coalesce(u.lastName,''))) like :query escape '!')")
 Page<Long> searchIds(@Param("company") Long companyId,@Param("query") String query,Pageable page);
 @Query("select distinct u from User u left join fetch u.roles left join fetch u.company c left join fetch c.enabledFeatures where u.company.id=:company and u.id in :ids and u.deleted=false")
 List<User> loadPermissionContext(@Param("company") Long companyId,@Param("ids") Collection<Long> ids);
}
