package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.*;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional
@Import({PmsGroupInventoryService.class,PmsGroupRoutingService.class,PmsGroupMemberTransaction.class})
public class PmsGroupService {
    private final GroupBookingRepository groups;
    private final HotelPropertyRepository properties;
    private final RoomTypeRepository types;
    private final RoomRepository rooms;
    private final RoomBlockRepository blocks;
    private final ReservationRepository reservations;
    private final PmsGroupAllotmentRepository allotments;
    private final FolioRepository folios;
    private final FolioItemRepository items;
    private final PmsOperationsService operations;
    private final PmsGroupInventoryService inventory;
    private final PmsGroupRoutingService routing;
    private final PmsGroupMemberTransaction memberTransactions;
    private final PmsAuditWriter audit;

    @Transactional(readOnly=true)
    public PmsGroupOperationsDto.View view(Company company,Long propertyId,Long groupId) {
        return toView(requireGroup(company,propertyId,groupId,false));
    }

    public PmsGroupOperationsDto.View addAllotment(Company company,Long propertyId,Long groupId,PmsGroupOperationsDto.Allotment request) {
        GroupBooking group=requireGroup(company,propertyId,groupId,true);
        requireActive(group);
        validateDates(group,request.startDate(),request.endDate());
        if(request.quantity()<1 || request.quantity()>10000 || request.releaseDate()==null
                || request.releaseDate().isAfter(request.startDate())
                || !request.releaseDate().isAfter(LocalDate.now(ZoneId.of(group.getProperty().getTimezone())))) {
            throw bad("Die Freigabefrist muss künftig und spätestens am ersten Kontingenttag liegen; Menge 1 bis 10000.");
        }
        RoomType type=types.findById(request.roomTypeId()).filter(t->t.getProperty().getId().equals(propertyId) && t.isActive())
                .orElseThrow(()->missing("Zimmertyp nicht gefunden."));
        boolean overlaps=allotments.findAllByGroupBooking_IdOrderByStartDateAscIdAsc(groupId).stream()
                .anyMatch(a->!a.isReleased() && a.getRoomType().getId().equals(type.getId())
                        && a.getStartDate().isBefore(request.endDate()) && a.getEndDate().isAfter(request.startDate()));
        if(overlaps) throw conflict("Für den Zimmertyp besteht bereits ein überlappendes Kontingent. Zuerst freigeben.");
        PmsGroupAllotment a=new PmsGroupAllotment();
        a.setGroupBooking(group); a.setRoomType(type); a.setStartDate(request.startDate()); a.setEndDate(request.endDate());
        a.setQuantity(request.quantity()); a.setReleaseDate(request.releaseDate());
        long capacity=rooms.countByProperty_IdAndRoomType_IdAndActiveTrueAndOperationalStatus(propertyId,type.getId(),RoomOperationalStatus.IN_SERVICE);
        var pickup=inventory.pickup(List.of(groupId),a.getStartDate(),a.getEndDate());
        var held=inventory.heldByNight(propertyId,type.getId(),a.getStartDate(),a.getEndDate(),null);
        for(LocalDate date=a.getStartDate();date.isBefore(a.getEndDate());date=date.plusDays(1)) {
            long blocked=blocks.countInventoryBlockingRooms(propertyId,type.getId(),date,date.plusDays(1),RoomBlockStatus.ACTIVE,
                    Set.of(RoomBlockType.OUT_OF_ORDER,RoomBlockType.OWNER_USE));
            long sold=reservations.countOverlappingByRoomType(propertyId,type.getId(),date,date.plusDays(1),
                    Set.of(ReservationStatus.CANCELLED,ReservationStatus.NO_SHOW,ReservationStatus.OFFERED,ReservationStatus.WAITLISTED,ReservationStatus.CHECKED_OUT),null);
            long additional=Math.max(0,a.getQuantity()-pickup.getOrDefault(new PmsGroupInventoryService.PickupKey(groupId,type.getId(),date),0L));
            if(sold+blocked+held.getOrDefault(date,0L)+additional>capacity)
                throw conflict("Das Kontingent übersteigt die freie Kapazität am "+date+".");
        }
        allotments.save(a);
        audit.append(group.getProperty(),"group.allotment_created","group",groupId.toString(),"{\"allotmentId\":"+a.getId()+",\"quantity\":"+a.getQuantity()+"}");
        return toView(group);
    }

    public PmsGroupOperationsDto.View release(Company company,Long propertyId,Long groupId,Long allotmentId) {
        GroupBooking group=requireGroup(company,propertyId,groupId,true);
        PmsGroupAllotment a=allotments.findById(allotmentId).filter(v->v.getGroupBooking().getId().equals(groupId))
                .orElseThrow(()->missing("Kontingent nicht gefunden."));
        a.setReleased(true);
        audit.append(group.getProperty(),"group.allotment_released","group",groupId.toString(),"{\"allotmentId\":"+allotmentId+"}");
        return toView(group);
    }

    public PmsGroupOperationsDto.View appendMembers(Company company,Long propertyId,Long groupId,PmsGroupOperationsDto.RoomingList request,String username) {
        GroupBooking group=requireGroup(company,propertyId,groupId,true);
        requireActive(group);
        for(var member:request.rooms()) addMember(company,group,member,username);
        audit.append(group.getProperty(),"group.rooming_list_added","group",groupId.toString(),"{\"memberCount\":"+request.rooms().size()+"}");
        return toView(group);
    }

    public Reservation addMember(Company company,GroupBooking group,CreateGroupBookingRequest.RoomingEntry room,String username) {
        LocalDate arrival=room.arrivalDate()==null?group.getArrivalDate():room.arrivalDate();
        LocalDate departure=room.departureDate()==null?group.getDepartureDate():room.departureDate();
        validateDates(group,arrival,departure);
        UpsertReservationRequest request=new UpsertReservationRequest(group.getProperty().getId(),room.guestId(),room.roomTypeId(),room.roomId(),room.ratePlanId(),
                arrival,departure,room.adults(),room.children(),group.getStatus()==GroupBookingStatus.OPTION?ReservationStatus.TENTATIVE:ReservationStatus.CONFIRMED,
                room.source()==null?ReservationSource.DIRECT:room.source(),room.notes(),null,null,room.childAges());
        return operations.createReservationRecord(company,request,username,group);
    }

    public PmsGroupOperationsDto.View configureRouting(Company company,Long propertyId,Long groupId,PmsGroupOperationsDto.Routing request) {
        GroupBooking group=requireGroup(company,propertyId,groupId,true);
        requireActive(group);
        if(request.types()==null || request.types().stream().anyMatch(Objects::isNull)) throw bad("Bitte gültige Leistungsarten angeben.");
        List<Reservation> members=reservations.findAllByGroupBooking_IdOrderByGuest_LastNameAsc(groupId);
        if(members.isEmpty() && !request.types().isEmpty()) throw conflict("Vor dem Gruppenhauptkonto muss mindestens ein Gruppenmitglied angelegt sein.");
        Folio master=folios.findByGroupBooking_IdAndGroupMasterTrue(groupId).orElse(null);
        if(master==null && !request.types().isEmpty()) {
            master=new Folio();master.setReservation(members.get(0));master.setGroupBooking(group);master.setGroupMaster(true);
            master.setLabel("Gruppe "+group.getGroupCode()+" · Hauptkonto");master.setOrganization(group.getOrganization());
            master.setCurrencyCode(group.getProperty().getCurrencyCode());folios.save(master);
        }
        group.setRoutedTypes(request.types().stream().map(Enum::name).sorted().collect(Collectors.joining(",")));
        members.forEach(routing::route);
        audit.append(group.getProperty(),"group.routing_updated","group",groupId.toString(),"{\"types\":\""+group.getRoutedTypes()+"\"}");
        return toView(group);
    }

    /** Deliberately no outer transaction: a rejected member must not roll back successful members. */
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public List<PmsGroupOperationsDto.MemberResult> bulk(Company company,Long propertyId,Long groupId,PmsGroupOperationsDto.BulkOperation request,String username) {
        // Membership and property rights are checked again inside each member transaction.
        List<PmsGroupOperationsDto.MemberResult> results=new ArrayList<>();
        for(Long id:new LinkedHashSet<>(request.reservationIds())) {
            try {
                memberTransactions.execute(company,propertyId,groupId,id,request.action(),username,request.businessDate());
                results.add(new PmsGroupOperationsDto.MemberResult(id,true,"Abgeschlossen"));
            } catch(ResponseStatusException ex) {
                results.add(new PmsGroupOperationsDto.MemberResult(id,false,ex.getReason()));
            }
        }
        return results;
    }

    private GroupBooking requireGroup(Company company,Long propertyId,Long id,boolean lock) {
        if(lock) properties.findByIdAndCompany_IdForUpdate(propertyId,company.getId()).orElseThrow(()->missing("Hotel nicht gefunden."));
        return groups.findById(id).filter(g->g.getProperty().getId().equals(propertyId) && g.getProperty().getCompany().getId().equals(company.getId()))
                .orElseThrow(()->missing("Gruppe nicht gefunden."));
    }
    private void requireActive(GroupBooking group) {
        if(Set.of(GroupBookingStatus.CANCELLED,GroupBookingStatus.COMPLETED).contains(group.getStatus())) throw conflict("Die Gruppe ist abgeschlossen oder storniert.");
    }
    private void validateDates(GroupBooking group,LocalDate from,LocalDate to) {
        if(from==null || to==null || !from.isBefore(to) || from.isBefore(group.getArrivalDate()) || to.isAfter(group.getDepartureDate()))
            throw bad("Die Aufenthaltsdaten müssen innerhalb des Gruppenzeitraums liegen.");
    }
    private PmsGroupOperationsDto.View toView(GroupBooking group) {
        var pickup=inventory.pickup(List.of(group.getId()),group.getArrivalDate(),group.getDepartureDate());
        return new PmsGroupOperationsDto.View(group.getId(),folios.findByGroupBooking_IdAndGroupMasterTrue(group.getId()).map(Folio::getId).orElse(null),
                PmsGroupRoutingService.types(group),allotments.findAllByGroupBooking_IdOrderByStartDateAscIdAsc(group.getId()).stream().map(a->{
                    List<PmsGroupOperationsDto.Night> nights=new ArrayList<>();
                    for(LocalDate date=a.getStartDate();date.isBefore(a.getEndDate());date=date.plusDays(1)) {
                        long picked=pickup.getOrDefault(new PmsGroupInventoryService.PickupKey(group.getId(),a.getRoomType().getId(),date),0L);
                        nights.add(new PmsGroupOperationsDto.Night(date,picked,inventory.held(a,picked)));
                    }
                    return new PmsGroupOperationsDto.AllotmentView(a.getId(),a.getRoomType().getId(),a.getRoomType().getName(),a.getStartDate(),a.getEndDate(),
                            a.getQuantity(),a.getReleaseDate(),a.isReleased(),nights);
                }).toList());
    }
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private ResponseStatusException missing(String message){return new ResponseStatusException(HttpStatus.NOT_FOUND,message);}
}
