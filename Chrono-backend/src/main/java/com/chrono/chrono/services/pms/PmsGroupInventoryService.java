package com.chrono.chrono.services.pms;

import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.PmsGroupAllotmentRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Unnamed allotments reserve remaining pickup capacity, never duplicate named reservations. */
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class PmsGroupInventoryService {
    private final PmsGroupAllotmentRepository allotments;
    private final EntityManager em;
    public long heldRooms(Long propertyId,Long roomTypeId,LocalDate date,Long excludeGroupId) {
        return heldByNight(propertyId,roomTypeId,date,date.plusDays(1),excludeGroupId).getOrDefault(date,0L);
    }
    /** Two scoped SQL reads for a whole date range, regardless of group size or night count. */
    public Map<LocalDate,Long> heldByNight(Long propertyId,Long roomTypeId,LocalDate start,LocalDate end,Long excludeGroupId) {
        HotelProperty property=em.find(HotelProperty.class,propertyId);
        LocalDate today=LocalDate.now(ZoneId.of(property.getTimezone()));
        List<PmsGroupAllotment> active=em.createQuery("""
                select a from PmsGroupAllotment a join fetch a.groupBooking g
                where g.property.id=:property and a.roomType.id=:roomType and a.released=false
                  and a.startDate<:end and a.endDate>:start and a.releaseDate>:today
                  and g.status not in :excluded and (:excludeGroup is null or g.id<>:excludeGroup)
                """,PmsGroupAllotment.class).setParameter("property",propertyId).setParameter("roomType",roomTypeId)
                .setParameter("start",start).setParameter("end",end).setParameter("today",today)
                .setParameter("excluded",Set.of(GroupBookingStatus.CANCELLED,GroupBookingStatus.COMPLETED))
                .setParameter("excludeGroup",excludeGroupId).getResultList();
        if(active.isEmpty()) return Map.of();
        Map<PickupKey,Long> pickup=pickup(active.stream().map(a->a.getGroupBooking().getId()).distinct().toList(),start,end);
        Map<LocalDate,Long> result=new HashMap<>();
        for(PmsGroupAllotment a:active) for(LocalDate date=a.getStartDate().isBefore(start)?start:a.getStartDate();date.isBefore(a.getEndDate()) && date.isBefore(end);date=date.plusDays(1))
            result.merge(date,Math.max(0,a.getQuantity()-pickup.getOrDefault(new PickupKey(a.getGroupBooking().getId(),roomTypeId,date),0L)),Long::sum);
        return result;
    }
    public long held(PmsGroupAllotment a,LocalDate date) {
        return held(a,pickedUp(a,date));
    }
    public long held(PmsGroupAllotment a,long pickedUp) {
        GroupBooking group=a.getGroupBooking();
        LocalDate today=LocalDate.now(ZoneId.of(group.getProperty().getTimezone()));
        if (a.isReleased() || !today.isBefore(a.getReleaseDate())
                || Set.of(GroupBookingStatus.CANCELLED,GroupBookingStatus.COMPLETED).contains(group.getStatus())) return 0;
        return Math.max(0,a.getQuantity()-pickedUp);
    }
    public long pickedUp(PmsGroupAllotment a,LocalDate date) {
        return pickup(List.of(a.getGroupBooking().getId()),date,date.plusDays(1)).getOrDefault(new PickupKey(a.getGroupBooking().getId(),a.getRoomType().getId(),date),0L);
    }
    public Map<PickupKey,Long> pickup(List<Long> groupIds,LocalDate start,LocalDate end) {
        if(groupIds.isEmpty()) return Map.of();
        List<Object[]> rows=em.createQuery("""
                select r.groupBooking.id,coalesce(sr.roomType.id,r.roomType.id),coalesce(s.startDate,r.arrivalDate),coalesce(s.endDate,r.departureDate)
                  from Reservation r left join r.roomSegments s left join s.room sr
                 where r.groupBooking.id in :groups and r.status not in :excluded and r.arrivalDate<:end and r.departureDate>:start
                """,Object[].class).setParameter("groups",groupIds).setParameter("start",start).setParameter("end",end)
                .setParameter("excluded",Set.of(ReservationStatus.CANCELLED,ReservationStatus.NO_SHOW,ReservationStatus.WAITLISTED,ReservationStatus.OFFERED)).getResultList();
        Map<PickupKey,Long> counts=new HashMap<>();
        for(Object[] row:rows) {
            LocalDate from=(LocalDate)row[2],to=(LocalDate)row[3];
            for(LocalDate date=from.isBefore(start)?start:from;date.isBefore(to) && date.isBefore(end);date=date.plusDays(1))
                counts.merge(new PickupKey((Long)row[0],(Long)row[1],date),1L,Long::sum);
        }
        return counts;
    }
    public record PickupKey(Long groupId,Long roomTypeId,LocalDate date) {}
}
