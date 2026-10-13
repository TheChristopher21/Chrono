package com.chrono.chrono.services.pms;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PmsBeds24ServiceTest {
    private final PmsBeds24SettingsRepository settings = mock(PmsBeds24SettingsRepository.class);
    private final PmsBeds24PublicationRepository jobs = mock(PmsBeds24PublicationRepository.class);
    private final HotelPropertyRepository properties = mock(HotelPropertyRepository.class);
    private final RatePlanRepository rates = mock(RatePlanRepository.class);
    private final RateOverrideRepository overrides = mock(RateOverrideRepository.class);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final RoomBlockRepository blocks = mock(RoomBlockRepository.class);
    private final PmsGroupInventoryService groups = mock(PmsGroupInventoryService.class);
    private final ExternalBookingReferenceRepository references = mock(ExternalBookingReferenceRepository.class);
    private final PmsBeds24Client client = mock(PmsBeds24Client.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private PmsBeds24Service service;
    private HotelProperty hotel;
    private RoomType type;
    private RatePlan rate;
    private PmsBeds24Settings config;
    private PmsBeds24Publication saved;
    private final LocalDate from = LocalDate.now(ZoneId.of("Europe/Zurich")).plusDays(2);
    private final List<PmsBeds24Service.Mapping> mappings = List.of(new PmsBeds24Service.Mapping(3L, 71L, 1));

    @BeforeEach void setup() throws Exception {
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        service = new PmsBeds24Service(settings, jobs, properties, rates, overrides, rooms, reservations, blocks, groups,
                references, client, mapper, transactions, mock(PmsAuditWriter.class), true);
        var company = new Company("Hotel"); company.setId(1L);
        hotel = new HotelProperty(); ReflectionTestUtils.setField(hotel, "id", 2L); hotel.setCompany(company); hotel.setCurrencyCode("CHF"); hotel.setTimezone("Europe/Zurich");
        type = new RoomType(); ReflectionTestUtils.setField(type, "id", 4L);
        rate = rate(3L); rate.setNightlyRate(new BigDecimal("100"));
        when(properties.findByIdAndCompany_IdForUpdate(2L, 1L)).thenReturn(Optional.of(hotel));
        when(properties.findById(2L)).thenReturn(Optional.of(hotel));
        when(rooms.findAllByProperty_IdOrderByFloorAscNumberAsc(2L)).thenReturn(List.of(room(10), room(11), room(12)));
        when(groups.heldByNight(eq(2L), eq(4L), any(), any(), isNull())).thenReturn(Map.of());
        config = new PmsBeds24Settings(); config.setPropertyId(2L); config.setExternalPropertyId(41L); config.setSecretReference("env:HOTEL_BEDS24");
        config.setEnabled(true); config.setVerifiedAt(LocalDateTime.now()); config.setMappingsJson(mapper.writeValueAsString(mappings));
        when(settings.findById(2L)).thenReturn(Optional.of(config));
        when(jobs.saveAndFlush(any())).thenAnswer(i -> { saved = i.getArgument(0); saved.setId(9L); saved.setCreatedAt(LocalDateTime.now()); saved.setNextAttemptAt(LocalDateTime.now().minusMinutes(1)); return saved; });
        when(jobs.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jobs.findLocked(9L)).thenAnswer(i -> Optional.ofNullable(saved));
        when(client.verifyProperty("env:HOTEL_BEDS24", 41L, "CHF")).thenReturn(Set.of(71L));
        when(client.bookings(anyString(), anyLong(), any(), any())).thenReturn(List.of());
    }

    @Test void calendarUsesGrossPricesHalfOpenDatesInventoryAndExplicitBlackout() {
        rate.setTaxIncluded(false); rate.setVatRate(new BigDecimal("8.1"));
        var closed = new RateOverride(); closed.setRatePlan(rate); closed.setStayDate(from.plusDays(1)); closed.setPrice(new BigDecimal("80")); closed.setMinStay(1); closed.setClosed(true);
        when(overrides.findAllByRatePlan_Property_IdAndStayDateBetweenOrderByStayDateAsc(2L, from, from.plusDays(1))).thenReturn(List.of(closed));
        when(groups.heldByNight(2L, 4L, from, from.plusDays(2), null)).thenReturn(Map.of(from, 1L));
        var calendar = service.calendar(hotel, mappings, from, from.plusDays(2)).get(0).path("calendar");
        assertThat(calendar).hasSize(2);
        assertThat(calendar.get(0).path("to").asText()).isEqualTo(from.toString());
        assertThat(calendar.get(0).path("numAvail").asInt()).isEqualTo(2);
        assertThat(calendar.get(0).path("price1").decimalValue()).isEqualByComparingTo("108.10");
        assertThat(calendar.get(1).path("override").asText()).isEqualTo("blackout");
        assertThat(calendar.get(1).path("price1").isNull()).isFalse();
    }

    @Test void independentPriceSlotsCannotSilentlyOverwriteConflictingRoomLevelRestrictions() {
        var second = rate(5L); second.setMinStay(3);
        assertThatThrownBy(() -> service.calendar(hotel, List.of(mappings.get(0), new PmsBeds24Service.Mapping(5L, 71L, 2)), from, from.plusDays(1)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("widersprechen");
    }

    @Test void advanceRulesAndNetPricesWithoutTaxCannotPublishAnIncorrectPublicPrice() {
        rate.setMinAdvanceDays(3);
        assertThatThrownBy(() -> service.calendar(hotel, mappings, from, from.plusDays(1))).hasMessageContaining("Vorausbuchungsfristen");
        rate.setMinAdvanceDays(null); rate.setTaxIncluded(false); rate.setVatRate(null);
        assertThatThrownBy(() -> service.calendar(hotel, mappings, from, from.plusDays(1))).hasMessageContaining("Steuersätze fehlen");
    }

    @Test void roomMovesAndDuplicateBlocksUseTheirActualNightAndDoNotInflateInventory() {
        var otherType = new RoomType(); ReflectionTestUtils.setField(otherType, "id", 99L);
        var movedRoom = room(15); movedRoom.setRoomType(otherType);
        var reservation = stay();
        var beforeMove = new ReservationRoomSegment(); beforeMove.setRoom(room(10)); beforeMove.setStartDate(from); beforeMove.setEndDate(from.plusDays(1));
        var afterMove = new ReservationRoomSegment(); afterMove.setRoom(movedRoom); afterMove.setStartDate(from.plusDays(1)); afterMove.setEndDate(from.plusDays(2));
        reservation.getRoomSegments().addAll(List.of(beforeMove, afterMove));
        var block = new RoomBlock(); block.setRoom(room(11)); block.setType(RoomBlockType.OUT_OF_ORDER); block.setStartDate(from); block.setEndDate(from.plusDays(1));
        var allRooms = List.of(room(10), room(11), room(12));
        assertThat(PmsBeds24Service.inventory(4L, from, allRooms, List.of(reservation), List.of(block, block), 1)).isZero();
        assertThat(PmsBeds24Service.inventory(4L, from.plusDays(1), allRooms, List.of(reservation), List.of(block), 0)).isEqualTo(3);
    }

    @Test void repeatedPublishIdentityReturnsOriginalSnapshotAndRejectsAnotherPeriod() {
        var first = service.publish(1L, 2L, new PmsBeds24Service.Publish("calendar-one", from, from.plusDays(2)), "staff");
        when(jobs.findByPropertyIdAndRequestKey(2L, "calendar-one")).thenReturn(Optional.of(saved));
        rate.setNightlyRate(new BigDecimal("500"));
        var repeated = service.publish(1L, 2L, new PmsBeds24Service.Publish("calendar-one", from, from.plusDays(2)), "staff");
        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(saved.getPayload()).contains("100").doesNotContain("500");
        verify(jobs, times(1)).saveAndFlush(any());
        assertThatThrownBy(() -> service.publish(1L, 2L, new PmsBeds24Service.Publish("calendar-one", from, from.plusDays(3)), "staff")).hasMessageContaining("anderen Zeitraum");
    }

    @Test void workerOnlyCompletesAfterProviderConfirmsEveryCalendarPart() throws Exception {
        queue();
        doThrow(new PmsBeds24Client.ProviderFailure("Teilfehler", 120)).when(client).publishCalendar(anyString(), any());
        service.process(9L);
        assertThat(saved.getStatus()).isEqualTo("PENDING"); assertThat(saved.getCompletedAt()).isNull();
        assertThat(saved.getNextAttemptAt()).isAfter(LocalDateTime.now().plusSeconds(100));
        var original = saved.getPayload(); saved.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        doNothing().when(client).publishCalendar(anyString(), any()); service.process(9L);
        assertThat(saved.getStatus()).isEqualTo("COMPLETE"); assertThat(saved.getAttempts()).isEqualTo(2);
        verify(client, times(2)).publishCalendar("env:HOTEL_BEDS24", mapper.readTree(original));
    }

    @Test void changedInventorySnapshotIsStoppedBeforeProviderWrite() throws Exception {
        queue(); rate.setNightlyRate(new BigDecimal("150")); service.process(9L);
        assertThat(saved.getStatus()).isEqualTo("FAILED"); assertThat(saved.getLastError()).contains("geändert").doesNotContain("env:");
        verify(client, never()).publishCalendar(anyString(), any());
    }

    @Test void unmatchedProviderBookingBlocksAvailabilityPublicationUntilLocalReservationIsReconciled() throws Exception {
        queue(); var booking = new PmsBeds24Client.Booking(101, 41, 71, "confirmed", from.toString(), from.plusDays(2).toString(), 1, 0, new BigDecimal("200"), "CHF", "", "", "");
        when(client.bookings(anyString(), anyLong(), any(), any())).thenReturn(List.of(booking)); service.process(9L);
        assertThat(saved.getStatus()).isEqualTo("FAILED"); assertThat(saved.getLastError()).contains("101", "Buchungsabgleich");
        verify(client, never()).publishCalendar(anyString(), any());
    }

    @Test void staleOperatorRetryCannotRestoreAnOlderAvailabilityPublication() {
        queue(); saved.setStatus("FAILED"); when(jobs.existsByPropertyIdAndIdGreaterThan(2L, 9L)).thenReturn(true);
        assertThatThrownBy(() -> service.retry(1L, 2L, 9L, false, "staff")).hasMessageContaining("neuerer Auftrag");
        assertThat(saved.getStatus()).isEqualTo("FAILED");
    }

    @Test void linkingRequiresExactProviderPriceAndNeverChangesAcceptedReservationAmounts() throws Exception {
        var reservation = stay(); reservation.setCurrencyCode("CHF");
        when(reservations.findByIdAndProperty_Company_Id(80L, 1L)).thenReturn(Optional.of(reservation));
        var wrongPrice = new PmsBeds24Client.Booking(101, 41, 71, "confirmed", from.toString(), from.plusDays(2).toString(), 1, 0, new BigDecimal("199"), "CHF", "", "", "");
        when(client.bookings(anyString(), anyLong(), any(), any())).thenReturn(List.of(wrongPrice));
        assertThatThrownBy(() -> service.link(1L, 2L, 101L, new PmsBeds24Service.Link(80L), "staff")).hasMessageContaining("Preis stimmen nicht");
        verify(references, never()).saveAndFlush(any());
        assertThat(reservation.getTotalAmount()).isEqualByComparingTo("200");
        var matching = new PmsBeds24Client.Booking(101, 41, 71, "confirmed", from.toString(), from.plusDays(2).toString(), 1, 0, new BigDecimal("200"), "CHF", "", "", "");
        when(client.bookings(anyString(), anyLong(), any(), any())).thenReturn(List.of(matching));
        assertThat(service.link(1L, 2L, 101L, new PmsBeds24Service.Link(80L), "staff").result()).isEqualTo("MATCHED");
        verify(references).saveAndFlush(argThat(ref -> ref.getProperty().getId().equals(2L) && ref.getReservation().getId().equals(80L) && ref.getExternalId().equals("101") && ref.getChannelCode().equals("BEDS24")));
        verify(reservations, never()).save(any());
    }

    @Test void linkingCannotReplaceAnExistingProviderReference() throws Exception {
        var reservation = stay(); reservation.setCurrencyCode("CHF");
        when(reservations.findByIdAndProperty_Company_Id(80L, 1L)).thenReturn(Optional.of(reservation));
        var booking = new PmsBeds24Client.Booking(101, 41, 71, "confirmed", from.toString(), from.plusDays(2).toString(), 1, 0, new BigDecimal("200"), "CHF", "", "", "");
        when(client.bookings(anyString(), anyLong(), any(), any())).thenReturn(List.of(booking));
        var previous = new ExternalBookingReference(); previous.setId(1000L); previous.setReservation(reservation); previous.setChannelCode("OTHER_PROVIDER"); previous.setExternalId("previous");
        when(references.findByReservation_Id(80L)).thenReturn(Optional.of(previous));
        assertThatThrownBy(() -> service.link(1L, 2L, 101L, new PmsBeds24Service.Link(80L), "staff")).hasMessageContaining("andere Anbieterreferenz");
        verify(references, never()).saveAndFlush(any());
    }

    private void queue() { service.publish(1L, 2L, new PmsBeds24Service.Publish("calendar-one", from, from.plusDays(2)), "staff"); }
    private Room room(long id) { var room = new Room(); ReflectionTestUtils.setField(room, "id", id); room.setProperty(hotel); room.setRoomType(type); return room; }
    private RatePlan rate(Long id) { var r = new RatePlan(); r.setId(id); r.setProperty(hotel); r.setRoomType(type); r.setCurrencyCode("CHF"); r.setNightlyRate(new BigDecimal("100")); when(rates.findByIdAndProperty_Company_Id(id, 1L)).thenReturn(Optional.of(r)); return r; }
    private Reservation stay() { var r = new Reservation(); r.setId(80L); r.setProperty(hotel); r.setRoomType(type); r.setRatePlan(rate); r.setStatus(ReservationStatus.CONFIRMED); r.setArrivalDate(from); r.setDepartureDate(from.plusDays(2)); r.setAdults(1); r.setTotalAmount(new BigDecimal("200")); return r; }
}
