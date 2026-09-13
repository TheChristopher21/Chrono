package com.chrono.chrono.services.pms;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import java.util.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
@Service
public class PmsBeds24Service {
    private final PmsBeds24SettingsRepository settings;private final PmsBeds24PublicationRepository jobs;private final HotelPropertyRepository properties;
    private final RatePlanRepository rates;private final RateOverrideRepository overrides;private final RoomRepository rooms;private final ReservationRepository reservations;
    private final RoomBlockRepository blocks;private final PmsGroupInventoryService groups;private final ExternalBookingReferenceRepository references;
    private final PmsBeds24Client client;private final ObjectMapper mapper;private final TransactionTemplate tx;private final PmsAuditWriter audit;private final boolean workerEnabled;
    public PmsBeds24Service(PmsBeds24SettingsRepository settings,PmsBeds24PublicationRepository jobs,HotelPropertyRepository properties,RatePlanRepository rates,
            RateOverrideRepository overrides,RoomRepository rooms,ReservationRepository reservations,RoomBlockRepository blocks,PmsGroupInventoryService groups,
            ExternalBookingReferenceRepository references,PmsBeds24Client client,ObjectMapper mapper,PlatformTransactionManager transactions,PmsAuditWriter audit,
            @Value("${app.pms.beds24.worker.enabled:false}") boolean workerEnabled){this.settings=settings;this.jobs=jobs;this.properties=properties;this.rates=rates;this.overrides=overrides;this.rooms=rooms;this.reservations=reservations;this.blocks=blocks;this.groups=groups;this.references=references;this.client=client;this.mapper=mapper;this.tx=new TransactionTemplate(transactions);this.audit=audit;this.workerEnabled=workerEnabled;}
    public record Mapping(Long ratePlanId,Long externalRoomId,int priceSlot){}
    public record Save(Long externalPropertyId,String secretReference,boolean enabled,List<Mapping> mappings,Long version){}
    public record RateChoice(Long id,String name,String roomType,int includedAdults){}
    public record View(Long externalPropertyId,String secretReference,boolean enabled,List<Mapping> mappings,long version,LocalDateTime verifiedAt,boolean workerEnabled,List<String> capabilities,List<RateChoice> rates,List<Job> jobs){}
    public record Job(Long id,String requestId,LocalDate from,LocalDate to,String status,int attempts,String error,LocalDateTime createdAt,LocalDateTime completedAt){}
    public record Publish(String requestId,LocalDate from,LocalDate to){}
    public record Reconciliation(Long bookingId,Long roomId,String status,String arrival,String departure,Long reservationId,String result){}
    public record Link(Long reservationId){}
    private static class StalePublication extends RuntimeException { StalePublication(String message){super(message);} }
    public View get(Long companyId,Long propertyId){return tx.execute(s -> {require(companyId,propertyId);var row=settings.findById(propertyId).orElse(new PmsBeds24Settings());return new View(row.getExternalPropertyId(),row.getSecretReference(),row.isEnabled(),mappings(row),row.getVersion(),row.getVerifiedAt(),workerEnabled,List.of("CALENDAR_PRICES","CALENDAR_AVAILABILITY","MIN_MAX_STAY","ARRIVAL_DEPARTURE_CLOSURES","BOOKING_RECONCILIATION"),rates.findAllByProperty_IdOrderByRoomType_SortOrderAscNameAsc(propertyId).stream().filter(r -> r.isActive()&&r.getOrganization()==null).map(r -> new RateChoice(r.getId(),r.getCode()+" · "+r.getName(),r.getRoomType().getName(),r.getIncludedAdults())).toList(),jobs.findTop30ByPropertyIdOrderByIdDesc(propertyId).stream().map(this::view).toList());});}
    public View save(Long companyId,Long propertyId,Save input,String actor){
        HotelProperty property=tx.execute(s -> {var p=require(companyId,propertyId);validateMappings(p,input);return p;});
        if(input.enabled())try{var externalRooms=client.verifyProperty(input.secretReference(),input.externalPropertyId(),property.getCurrencyCode());if(!externalRooms.containsAll(input.mappings().stream().map(Mapping::externalRoomId).toList()))throw error(HttpStatus.CONFLICT,"Mindestens eine Beds24-Zimmer-ID gehört nicht zu diesem Anbieterhotel.");}catch(ResponseStatusException e){throw e;}catch(Exception e){throw error(HttpStatus.BAD_GATEWAY,"Beds24-Hotel, Währung oder Zugriff konnten nicht geprüft werden.");}
        tx.executeWithoutResult(s -> {var p=require(companyId,propertyId);validateMappings(p,input);var row=settings.findById(propertyId).orElse(new PmsBeds24Settings());if(input.version()==null||input.version()!=row.getVersion())throw error(HttpStatus.CONFLICT,"Beds24-Einstellungen wurden geändert. Neu laden.");if(jobs.existsByPropertyIdAndStatusIn(propertyId,List.of("PENDING","PROCESSING","FAILED")))throw error(HttpStatus.CONFLICT,"Zuerst den gespeicherten Beds24-Auftrag abschließen oder verwerfen.");row.setPropertyId(propertyId);row.setExternalPropertyId(input.externalPropertyId());row.setSecretReference(input.secretReference());row.setMappingsJson(json(input.mappings()));row.setEnabled(input.enabled());row.setVerifiedAt(input.enabled()?LocalDateTime.now():null);settings.saveAndFlush(row);audit.append(p,"beds24.settings_saved","property",propertyId.toString(),"{}");});return get(companyId,propertyId);
    }
    private void validateMappings(HotelProperty property,Save input){
        if(input.externalPropertyId()==null||input.externalPropertyId()<=0||input.secretReference()==null||!input.secretReference().matches("env:[A-Z][A-Z0-9_]{2,100}")||input.mappings()==null||input.mappings().isEmpty()||input.mappings().size()>500)throw error(HttpStatus.BAD_REQUEST,"Beds24-Hotel-ID, Server-Tokenreferenz und mindestens eine gültige Zuordnung angeben.");
        Map<Long,Long> localToExternal=new HashMap<>(),externalToLocal=new HashMap<>();Set<String> slots=new HashSet<>();Set<Long> rateIds=new HashSet<>();
        for(Mapping mapping:input.mappings()){
            if(mapping.ratePlanId()==null||mapping.externalRoomId()==null||mapping.externalRoomId()<=0||mapping.priceSlot()<1||mapping.priceSlot()>16||!rateIds.add(mapping.ratePlanId())||!slots.add(mapping.externalRoomId()+":"+mapping.priceSlot()))throw error(HttpStatus.BAD_REQUEST,"Doppelte oder ungültige Raten-/Preisslotzuordnung.");
            RatePlan rate=rates.findByIdAndProperty_Company_Id(mapping.ratePlanId(),property.getCompany().getId()).filter(r -> r.getProperty().getId().equals(property.getId())).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Ratenplan gehört nicht zu diesem Hotel."));
            if(rate.getOrganization()!=null)throw error(HttpStatus.CONFLICT,"Firmenraten dürfen nicht als öffentliche Beds24-Rate veröffentlicht werden.");
            Long roomType=rate.getRoomType().getId();Long previous=localToExternal.putIfAbsent(roomType,mapping.externalRoomId()),inverse=externalToLocal.putIfAbsent(mapping.externalRoomId(),roomType);
            if(previous!=null&&!previous.equals(mapping.externalRoomId())||inverse!=null&&!inverse.equals(roomType))throw error(HttpStatus.CONFLICT,"Eine Zimmerkategorie muss genau einer Beds24-Zimmer-ID entsprechen, damit Bestand nicht mehrfach verkauft wird.");
        }
    }
    public Job publish(Long companyId,Long propertyId,Publish input,String actor){return tx.execute(s -> {
        var property=require(companyId,propertyId);dates(property,input.from(),input.to());if(input.requestId()==null||!input.requestId().matches("[A-Za-z0-9._:-]{8,80}"))throw error(HttpStatus.BAD_REQUEST,"Stabile Vorgangs-ID erforderlich.");
        var previous=jobs.findByPropertyIdAndRequestKey(propertyId,input.requestId()).orElse(null);if(previous!=null){if(!previous.getFromDate().equals(input.from())||!previous.getToDate().equals(input.to()))throw error(HttpStatus.CONFLICT,"Vorgangs-ID gehört zu einem anderen Zeitraum.");return view(previous);}
        var config=settings.findById(propertyId).filter(c -> c.isEnabled()&&c.getVerifiedAt()!=null).orElseThrow(() -> error(HttpStatus.CONFLICT,"Beds24 zuerst für dieses Hotel aktivieren und prüfen."));
        if(jobs.existsByPropertyIdAndStatusIn(propertyId,List.of("PENDING","PROCESSING","FAILED")))throw error(HttpStatus.CONFLICT,"Ein vorheriger Beds24-Auftrag ist noch offen. Zuerst prüfen oder verwerfen.");
        var job=new PmsBeds24Publication();job.setPropertyId(propertyId);job.setExternalPropertyId(config.getExternalPropertyId());job.setSecretReference(config.getSecretReference());job.setCurrencyCode(property.getCurrencyCode());job.setRequestKey(input.requestId());job.setFromDate(input.from());job.setToDate(input.to());job.setPayload(calendar(property,mappings(config),input.from(),input.to()).toString());jobs.saveAndFlush(job);audit.append(property,"beds24.publication_queued","beds24_publication",job.getId().toString(),"{}");return view(job);
    });}
    ArrayNode calendar(HotelProperty property,List<Mapping> mappings,LocalDate from,LocalDate to){
        var allRooms=rooms.findAllByProperty_IdOrderByFloorAscNumberAsc(property.getId());var stays=reservations.findAllByProperty_IdAndArrivalDateLessThanAndDepartureDateGreaterThanOrderByArrivalDateAsc(property.getId(),to,from);
        var allBlocks=blocks.findAllByProperty_IdAndStartDateLessThanAndEndDateGreaterThanOrderByStartDateAsc(property.getId(),to,from);
        var allOverrides=overrides.findAllByRatePlan_Property_IdAndStayDateBetweenOrderByStayDateAsc(property.getId(),from,to.minusDays(1));
        Map<Long,Map<LocalDate,ObjectNode>> external=new LinkedHashMap<>();Map<Long,Map<LocalDate,Long>> held=new HashMap<>();Map<Long,Map<LocalDate,Long>> available=new HashMap<>();
        Map<Long,Map<LocalDate,RateOverride>> overridesByRate=new HashMap<>();for(var override:allOverrides)overridesByRate.computeIfAbsent(override.getRatePlan().getId(),id -> new HashMap<>()).put(override.getStayDate(),override);
        LocalDate today=LocalDate.now(ZoneId.of(property.getTimezone()));
        for(var mapping:mappings){
            RatePlan rate=rates.findByIdAndProperty_Company_Id(mapping.ratePlanId(),property.getCompany().getId()).orElseThrow();Long roomType=rate.getRoomType().getId();
            if(!rate.getProperty().getId().equals(property.getId()) || rate.getOrganization()!=null || !property.getCurrencyCode().equals(rate.getCurrencyCode()))throw error(HttpStatus.CONFLICT,"Ratenzuordnung oder Ratenwährung ist nicht mehr gültig.");
            if(rate.getMinAdvanceDays()!=null||rate.getMaxAdvanceDays()!=null)throw error(HttpStatus.CONFLICT,"Vorausbuchungsfristen benötigen die passende Beds24-Preisregel; Kalenderexport dieser Rate ist gesperrt.");
            if(!rate.isTaxIncluded() && (rate.getVatRate()==null || rate.isBreakfastIncluded()&&rate.getBreakfastVatRate()==null))throw error(HttpStatus.CONFLICT,"Explizite Steuersätze fehlen für den Brutto-Channelpreis.");
            held.computeIfAbsent(roomType,id -> groups.heldByNight(property.getId(),id,from,to,null));
            var cells=external.computeIfAbsent(mapping.externalRoomId(),id -> new LinkedHashMap<>());
            for(LocalDate date=from;date.isBefore(to);date=date.plusDays(1)){
                LocalDate day=date;var override=overridesByRate.getOrDefault(rate.getId(),Map.of()).get(day);
                boolean closed=!rate.isActive()||override!=null&&override.isClosed()||rate.getValidFrom()!=null&&day.isBefore(rate.getValidFrom())||rate.getValidTo()!=null&&day.isAfter(rate.getValidTo())||rate.getBookingFrom()!=null&&today.isBefore(rate.getBookingFrom())||rate.getBookingTo()!=null&&today.isAfter(rate.getBookingTo());
                int min=override==null?rate.getMinStay():override.getMinStay();Integer max=rate.getMaxStay();
                if(min<1||min>365||max!=null&&(max<1||max>364))throw error(HttpStatus.CONFLICT,"Aufenthaltsgrenzen liegen außerhalb des Beds24-Kalenderformats.");
                String closure=override!=null&&override.isClosedArrival()?override.isClosedDeparture()?"noCheckInOrCheckOut":"noCheckIn":override!=null&&override.isClosedDeparture()?"noCheckOut":"none";
                if(closed)closure="blackout";
                ObjectNode cell=cells.get(day);if(cell==null){cell=mapper.createObjectNode();cell.put("from",day.toString());cell.put("to",day.toString());cell.put("numAvail",available.computeIfAbsent(roomType,id -> new HashMap<>()).computeIfAbsent(day,d -> inventory(roomType,d,allRooms,stays,allBlocks,held.get(roomType).getOrDefault(d,0L))));cell.put("minStay",min);if(max==null)cell.putNull("maxStay");else cell.put("maxStay",max);cell.put("override",closure);cells.put(day,cell);}
                else if(cell.path("minStay").asInt()!=min || !Objects.equals(cell.get("maxStay").isNull()?null:cell.get("maxStay").asInt(),max)||!cell.path("override").asText().equals(closure))throw error(HttpStatus.CONFLICT,"Beds24-Kalendergrenzen gelten pro Zimmer: die zugeordneten Raten widersprechen sich. Zuordnungen/Preisregeln abstimmen.");
                cell.put("price"+mapping.priceSlot(),PmsRatePricing.night(rate,override==null?rate.getNightlyRate():override.getPrice(),rate.getIncludedAdults(),0).total());
            }
        }
        ArrayNode result=mapper.createArrayNode();external.forEach((roomId,dates) -> {var node=result.addObject();node.put("roomId",roomId);var days=node.putArray("calendar");dates.values().forEach(days::add);});return result;
    }
    static long inventory(Long roomType,LocalDate day,List<Room> rooms,List<Reservation> stays,List<RoomBlock> blocks,long held){
        Set<Long> sellable=new HashSet<>();for(var room:rooms)if(room.isActive()&&room.getOperationalStatus()==RoomOperationalStatus.IN_SERVICE&&room.getRoomType().getId().equals(roomType))sellable.add(room.getId());
        long sold=stays.stream().filter(r -> !Set.of(ReservationStatus.OFFERED,ReservationStatus.WAITLISTED,ReservationStatus.CANCELLED,ReservationStatus.NO_SHOW,ReservationStatus.CHECKED_OUT).contains(r.getStatus()))
                .filter(r -> !day.isBefore(r.getArrivalDate())&&day.isBefore(r.getDepartureDate()))
                .filter(r -> r.getRoomSegments().isEmpty()?r.getRoomType().getId().equals(roomType):r.getRoomSegments().stream().anyMatch(segment -> segment.getRoom().getRoomType().getId().equals(roomType)&&!day.isBefore(segment.getStartDate())&&day.isBefore(segment.getEndDate()))).count();
        long blocked=blocks.stream().filter(b -> b.getStatus()==RoomBlockStatus.ACTIVE&&Set.of(RoomBlockType.OUT_OF_ORDER,RoomBlockType.OWNER_USE).contains(b.getType())&&sellable.contains(b.getRoom().getId())&&!day.isBefore(b.getStartDate())&&day.isBefore(b.getEndDate())).map(b -> b.getRoom().getId()).distinct().count();return Math.max(0,sellable.size()-sold-blocked-held);
    }
    public List<Reconciliation> reconcile(Long companyId,Long propertyId,LocalDate from,LocalDate to){
        var config=tx.execute(s -> {var p=require(companyId,propertyId);dates(p,from,to);return settings.findById(propertyId).orElseThrow(() -> error(HttpStatus.CONFLICT,"Beds24-Einstellungen fehlen."));});
        final List<PmsBeds24Client.Booking> external;try{external=client.bookings(config.getSecretReference(),config.getExternalPropertyId(),from,to);}catch(Exception e){throw error(HttpStatus.BAD_GATEWAY,"Beds24-Buchungen konnten nicht vollständig abgeglichen werden.");}
        return tx.execute(s -> {require(companyId,propertyId);List<Reconciliation> result=new ArrayList<>();for(var booking:external){var reference=references.findByProperty_IdAndChannelCodeIgnoreCaseAndExternalId(propertyId,"BEDS24",Long.toString(booking.id())).orElse(null);var reservation=reference==null?null:reference.getReservation();String comparison=reservation==null?"NOT_LINKED":matchesMapped(booking,reservation,mappings(config))?"MATCHED":"DIFFERENCE";result.add(new Reconciliation(booking.id(),booking.roomId(),booking.status(),booking.arrival(),booking.departure(),reservation==null?null:reservation.getId(),comparison));}return result;});
    }
    public Reconciliation link(Long companyId,Long propertyId,Long bookingId,Link input,String actor){
        var target=tx.execute(s -> {require(companyId,propertyId);if(input.reservationId()==null)throw error(HttpStatus.BAD_REQUEST,"Chrono-Reservierungs-ID erforderlich.");return reservations.findByIdAndProperty_Company_Id(input.reservationId(),companyId).filter(r -> r.getProperty().getId().equals(propertyId)).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Reservierung gehört nicht zu diesem Hotel."));});
        var config=tx.execute(s -> settings.findById(propertyId).filter(c -> c.isEnabled()&&c.getVerifiedAt()!=null).orElseThrow(() -> error(HttpStatus.CONFLICT,"Beds24-Verbindung zuerst prüfen.")));
        final PmsBeds24Client.Booking booking;
        try{booking=client.bookings(config.getSecretReference(),config.getExternalPropertyId(),target.getArrivalDate(),target.getDepartureDate()).stream().filter(b -> b.id()==bookingId).findFirst().orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Beds24-Buchung wurde im Reservierungszeitraum nicht gefunden."));}
        catch(ResponseStatusException e){throw e;}catch(Exception e){throw error(HttpStatus.BAD_GATEWAY,"Beds24-Buchung konnte nicht geprüft werden.");}
        return tx.execute(s -> {var property=require(companyId,propertyId);var current=settings.findById(propertyId).orElseThrow();
            if(!current.isEnabled()||current.getVersion()!=config.getVersion())throw error(HttpStatus.CONFLICT,"Anbieterzuordnung wurde während der Prüfung geändert.");
            var reservation=reservations.findByIdAndProperty_Company_Id(input.reservationId(),companyId).filter(r -> r.getProperty().getId().equals(propertyId)).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Reservierung nicht gefunden."));
            if(!matchesMapped(booking,reservation,mappings(current)))throw error(HttpStatus.CONFLICT,"Zeitraum, Belegung, Zimmer-/Ratenzuordnung, Status oder Preis stimmen nicht überein. Reservierung zuerst fachlich korrigieren.");
            var existing=references.findByProperty_IdAndChannelCodeIgnoreCaseAndExternalId(propertyId,"BEDS24",bookingId.toString()).orElse(null);
            if(existing!=null&&!existing.getReservation().getId().equals(reservation.getId()))throw error(HttpStatus.CONFLICT,"Beds24-Buchung ist bereits einer anderen Reservierung zugeordnet.");
            var local=references.findByReservation_Id(reservation.getId()).orElse(null);
            if(local!=null&&(existing==null||!local.getId().equals(existing.getId())))throw error(HttpStatus.CONFLICT,"Reservierung besitzt bereits eine andere Anbieterreferenz.");
            if(existing==null){var reference=new ExternalBookingReference();reference.setProperty(property);reference.setReservation(reservation);reference.setChannelCode("BEDS24");reference.setExternalId(bookingId.toString());references.saveAndFlush(reference);audit.append(property,"beds24.booking_linked","reservation",reservation.getId().toString(),"{\"bookingId\":"+bookingId+"}");}
            return new Reconciliation(booking.id(),booking.roomId(),booking.status(),booking.arrival(),booking.departure(),reservation.getId(),"MATCHED");
        });
    }
    static boolean matches(PmsBeds24Client.Booking b,Reservation r){boolean status="cancelled".equals(b.status())?r.getStatus()==ReservationStatus.CANCELLED:Set.of(ReservationStatus.CONFIRMED,ReservationStatus.CHECKED_IN,ReservationStatus.CHECKED_OUT).contains(r.getStatus());return Objects.equals(b.arrival(),r.getArrivalDate().toString())&&Objects.equals(b.departure(),r.getDepartureDate().toString())&&status&&b.adults()==r.getAdults()&&b.children()==r.getChildren()&&b.price()!=null&&b.price().compareTo(r.getTotalAmount())==0;}
    private boolean matchesMapped(PmsBeds24Client.Booking b,Reservation r,List<Mapping> mappings){return (b.currency()==null||b.currency().isBlank()||b.currency().equalsIgnoreCase(r.getCurrencyCode()))&&matches(b,r)&&mappings.stream().anyMatch(m -> m.externalRoomId()==b.roomId()&&m.ratePlanId().equals(r.getRatePlan().getId()));}
    public Job retry(Long companyId,Long propertyId,Long id,boolean discard,String actor){return tx.execute(s -> {var property=require(companyId,propertyId);var row=jobs.findLocked(id).filter(j -> j.getPropertyId().equals(propertyId)).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Beds24-Auftrag nicht gefunden."));if("PROCESSING".equals(row.getStatus()))throw error(HttpStatus.CONFLICT,"Laufenden Anbieterauftrag erst abschließen lassen.");if(!List.of("FAILED","PENDING").contains(row.getStatus()))throw error(HttpStatus.CONFLICT,"Dieser Anbieterauftrag ist bereits abgeschlossen.");if(jobs.existsByPropertyIdAndIdGreaterThan(propertyId,id))throw error(HttpStatus.CONFLICT,"Ein neuerer Auftrag existiert; alte Bestände dürfen nicht zurückgespielt werden.");row.setStatus(discard?"CANCELLED":"PENDING");row.setNextAttemptAt(LocalDateTime.now());row.setAttempts(0);jobs.save(row);audit.append(property,discard?"beds24.publication_cancelled":"beds24.publication_retried","beds24_publication",id.toString(),"{}");return view(row);});}
    public void processDue(){for(var row:jobs.findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByIdAsc(List.of("PENDING","PROCESSING"),LocalDateTime.now()))process(row.getId());}
    void process(Long id){
        PmsBeds24Publication row=tx.execute(s -> {var job=jobs.findLocked(id).orElseThrow();if(!List.of("PENDING","PROCESSING").contains(job.getStatus())||job.getNextAttemptAt().isAfter(LocalDateTime.now()))return null;job.setStatus("PROCESSING");job.setAttempts(job.getAttempts()+1);job.setNextAttemptAt(LocalDateTime.now().plusMinutes(10));return jobs.save(job);});if(row==null)return;
        try{JsonNode payload=mapper.readTree(row.getPayload());var authorized=client.verifyProperty(row.getSecretReference(),row.getExternalPropertyId(),row.getCurrencyCode());for(var room:payload)if(!authorized.contains(room.path("roomId").asLong()))throw new StalePublication("Zimmer-ID gehört nicht zum Anbieterhotel. Auftrag verwerfen und Zuordnung prüfen.");
            var external=client.bookings(row.getSecretReference(),row.getExternalPropertyId(),row.getFromDate(),row.getToDate());
            validateCurrentSnapshot(row,payload,external);
            client.publishCalendar(row.getSecretReference(),payload);tx.executeWithoutResult(s -> {var job=jobs.findLocked(id).orElseThrow();job.setStatus("COMPLETE");job.setCompletedAt(LocalDateTime.now());job.setLastError(null);jobs.save(job);});}
        catch(Exception e){tx.executeWithoutResult(s -> {var job=jobs.findLocked(id).orElseThrow();job.setStatus(e instanceof StalePublication||job.getAttempts()>=8?"FAILED":"PENDING");job.setLastError(e instanceof PmsBeds24Client.ProviderFailure||e instanceof StalePublication?e.getMessage():"Beds24-Übertragung nicht bestätigt. Gespeicherten Auftrag prüfen.");job.setNextAttemptAt(LocalDateTime.now().plusSeconds(e instanceof PmsBeds24Client.ProviderFailure f?f.retryAfterSeconds():Math.min(3600,30L<<Math.min(7,job.getAttempts()))));jobs.save(job);});}
    }
    private void validateCurrentSnapshot(PmsBeds24Publication row,JsonNode payload,List<PmsBeds24Client.Booking> external){tx.executeWithoutResult(s -> {
        var property=properties.findById(row.getPropertyId()).orElseThrow();require(property.getCompany().getId(),property.getId());
        var config=settings.findById(row.getPropertyId()).filter(c -> c.isEnabled()&&c.getVerifiedAt()!=null).orElseThrow(() -> new StalePublication("Beds24-Konfiguration ist nicht mehr freigegeben."));
        if(!Objects.equals(config.getExternalPropertyId(),row.getExternalPropertyId())||!Objects.equals(config.getSecretReference(),row.getSecretReference())||!Objects.equals(property.getCurrencyCode(),row.getCurrencyCode()))throw new StalePublication("Anbieterhotel, Tokenreferenz oder Währung wurde geändert. Auftrag verwerfen und neu erstellen.");
        var mapped=mappings(config);
        // Compare the persisted wire representation on both sides: Jackson's
        // in-memory DecimalNode/LongNode types differ from parsed JSON numbers.
        if(row.getFromDate().isBefore(LocalDate.now(ZoneId.of(property.getTimezone())))||!payload.equals(parseCalendar(calendar(property,mapped,row.getFromDate(),row.getToDate()).toString())))throw new StalePublication("Preise oder Verfügbarkeit haben sich seit Auftragserstellung geändert. Auftrag verwerfen und mit aktuellem Stand neu erstellen.");
        Set<Long> roomIds=new HashSet<>();mapped.forEach(m -> roomIds.add(m.externalRoomId()));
        for(var booking:external){
            if(!roomIds.contains(booking.roomId())||"cancelled".equals(booking.status()))continue;
            LocalDate arrival=LocalDate.parse(booking.arrival()),departure=LocalDate.parse(booking.departure());
            if(!arrival.isBefore(row.getToDate())||!departure.isAfter(row.getFromDate()))continue;
            var reference=references.findByProperty_IdAndChannelCodeIgnoreCaseAndExternalId(row.getPropertyId(),"BEDS24",Long.toString(booking.id())).orElse(null);
            if(reference==null||reference.getReservation()==null||!matchesMapped(booking,reference.getReservation(),mapped))throw new StalePublication("Beds24-Buchung "+booking.id()+" ist noch nicht vollständig im PMS abgeglichen. Zuerst Buchungsabgleich bearbeiten.");
        }
    });}
    private HotelProperty require(Long companyId,Long propertyId){return properties.findByIdAndCompany_IdForUpdate(propertyId,companyId).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Hotel nicht gefunden."));}
    private void dates(HotelProperty p,LocalDate from,LocalDate to){if(from==null||to==null||!to.isAfter(from)||ChronoUnit.DAYS.between(from,to)>31||from.isBefore(LocalDate.now(ZoneId.of(p.getTimezone()))))throw error(HttpStatus.BAD_REQUEST,"Zeitraum ab heute mit höchstens 31 Tagen wählen; Ende ist exklusiv.");}
    private List<Mapping> mappings(PmsBeds24Settings row){try{return row.getMappingsJson()==null?List.of():mapper.readValue(row.getMappingsJson(),mapper.getTypeFactory().constructCollectionType(List.class,Mapping.class));}catch(Exception e){throw new IllegalStateException("Gespeicherte Beds24-Zuordnung ist ungültig.");}}
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private JsonNode parseCalendar(String value){try{return mapper.readTree(value);}catch(Exception e){throw new IllegalStateException("Gespeicherter Kalenderauftrag ist ungültig.",e);}}
    private Job view(PmsBeds24Publication row){return new Job(row.getId(),row.getRequestKey(),row.getFromDate(),row.getToDate(),row.getStatus(),row.getAttempts(),row.getLastError(),row.getCreatedAt(),row.getCompletedAt());}
    private ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}
