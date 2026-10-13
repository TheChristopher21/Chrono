package com.chrono.chrono.services.pms;
import com.chrono.chrono.dto.pms.PmsRevenuePlanningDtos.*;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.pms.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties="spring.jpa.hibernate.ddl-auto=create-drop",showSql=false)
@Import({PmsRevenuePlanningService.class,PmsFinancialPeriodService.class,PmsAuditWriter.class})
@ActiveProfiles("test")
class PmsRevenuePlanningServiceTest {
 @Autowired PmsRevenuePlanningService service;@Autowired CompanyRepository companies;@Autowired HotelPropertyRepository properties;
 @Autowired RoomTypeRepository roomTypes;@Autowired GuestProfileRepository guests;@Autowired RatePlanRepository rates;
 @Autowired ReservationRepository reservations;@Autowired FolioRepository folios;@Autowired FolioItemRepository items;
 @Autowired PmsRevenueSnapshotRepository snapshots;@MockBean PmsPropertyAccessService access;
 HotelProperty hotel;RoomType type;RatePlan rate;GuestProfile guest;Company company;LocalDate today;PmsPropertyAccessService.Access actor;
 @BeforeEach void setup(){
  company=companies.save(new Company("Revenue Hotel"));hotel=new HotelProperty();hotel.setCompany(company);hotel.setCode("REV");hotel.setName("Revenue Hotel");hotel.setCurrencyCode("CHF");hotel.setTimezone("UTC");hotel=properties.save(hotel);
  today=LocalDate.now(ZoneId.of("UTC"));type=new RoomType();type.setProperty(hotel);type.setCode("DBL");type.setName("Double");type=roomTypes.save(type);
  rate=new RatePlan();rate.setProperty(hotel);rate.setRoomType(type);rate.setCode("BAR");rate.setName("Rate");rate.setCurrencyCode("CHF");rate.setNightlyRate(new BigDecimal("110"));rate=rates.save(rate);
  guest=new GuestProfile();guest.setCompany(company);guest.setFirstName("Revenue");guest.setLastName("Guest");guest=guests.save(guest);
  actor=new PmsPropertyAccessService.Access(1L,company.getId(),false,Map.of(hotel.getId(),Map.of("REPORTS","MANAGE")));
  when(access.access("Manager")).thenReturn(actor);
 }
 Reservation stay(LocalDate date){
  Reservation r=new Reservation();r.setProperty(hotel);r.setRoomType(type);r.setRatePlan(rate);r.setGuest(guest);r.setArrivalDate(date);r.setDepartureDate(date.plusDays(1));
  r.setConfirmationCode(UUID.randomUUID().toString());r.setCreatedBy("Manager");r.setCurrencyCode("CHF");r=reservations.save(r);
  Folio f=new Folio();f.setReservation(r);f.setCurrencyCode("CHF");f=folios.save(f);
  FolioItem line=new FolioItem();line.setFolio(f);line.setSourceReservation(r);line.setServiceDate(date);line.setType(FolioItemType.ROOM);line.setDescription("Room");
  line.setTotalAmount(new BigDecimal("110"));line.setUnitPrice(new BigDecimal("110"));line.setTaxRate(BigDecimal.TEN);line.setTaxIncluded(true);line.setRateGenerated(true);items.saveAndFlush(line);return r;
 }
 @Test void absentHistoryRemainsUnknownAndDateBoundsAreEnforced(){
  stay(today.plusDays(2));var report=service.report("Manager",hotel.getId(),today,today.plusDays(4),null);
  assertThat(report.summary().netRoomRevenue()).isEqualByComparingTo("100");assertThat(report.summary().roomNights()).isEqualTo(1);
  assertThat(report.comparisonSnapshot()).isNull();assertThat(report.summary().pickupNetRoomRevenue()).isNull();
  assertThatThrownBy(()->service.report("Manager",hotel.getId(),today,today.plusDays(367),null)).hasMessageContaining("366");
  assertThatThrownBy(()->service.report("Manager",hotel.getId(),today.minusDays(1),today.plusDays(1),null)).hasMessageContaining("Betriebstag");
 }
 @Test void immutableSnapshotReportsRealPickupAndCancellationRatherThanInventingPriorHistory(){
  Reservation first=stay(today.plusDays(1));var frozen=service.capture("Manager",hotel.getId());
  stay(today.plusDays(1));var changed=service.report("Manager",hotel.getId(),today,today.plusDays(3),frozen.id());
  assertThat(changed.summary().previousNetRoomRevenue()).isEqualByComparingTo("100");assertThat(changed.summary().pickupNetRoomRevenue()).isEqualByComparingTo("100");
  assertThat(changed.summary().pickupRoomNights()).isEqualTo(1);assertThat(service.capture("Manager",hotel.getId()).id()).isEqualTo(frozen.id());
  assertThat(snapshots.count()).isEqualTo(1);
  first.setStatus(ReservationStatus.CANCELLED);reservations.saveAndFlush(first);
  var cancelled=service.report("Manager",hotel.getId(),today,today.plusDays(3),frozen.id());
  assertThat(cancelled.summary().netRoomRevenue()).isEqualByComparingTo("100");assertThat(cancelled.summary().pickupNetRoomRevenue()).isZero();
 }
 @Test void partialSnapshotCoverageDoesNotProduceATotalPickup(){
  var saved=service.capture("Manager",hotel.getId());
  var report=service.report("Manager",hotel.getId(),today.plusDays(365),today.plusDays(367),saved.id());
  assertThat(report.summary().comparisonCoverageDays()).isEqualTo(1);assertThat(report.summary().requestedDays()).isEqualTo(2);
  assertThat(report.summary().previousRoomNights()).isNull();assertThat(report.summary().pickupNetRoomRevenue()).isNull();
  assertThat(report.days().get(0).previousNetRoomRevenue()).isZero();assertThat(report.days().get(1).previousNetRoomRevenue()).isNull();
 }
 @Test void sourceRoutingAndMissingTaxAreHandledWithoutDuplicatingRoomNightsOrCallingGrossNet(){
  Reservation first=stay(today.plusDays(1));Reservation second=stay(today.plusDays(1));
  Folio target=folios.findFirstByReservation_IdOrderByIdAsc(second.getId()).orElseThrow();
  FolioItem moved=items.findAllByFolio_Reservation_IdAndRateGeneratedTrueOrderByServiceDateAscIdAsc(first.getId()).get(0);moved.setFolio(target);items.saveAndFlush(moved);
  var known=service.report("Manager",hotel.getId(),today,today.plusDays(3),null);
  assertThat(known.summary().roomNights()).isEqualTo(2);assertThat(known.summary().netRoomRevenue()).isEqualByComparingTo("200");
  FolioItem credit=new FolioItem();credit.setFolio(target);credit.setSourceReservation(first);credit.setServiceDate(today.plusDays(1));
  credit.setType(FolioItemType.ROOM);credit.setDescription("Room service credit");credit.setTotalAmount(new BigDecimal("-11"));credit.setUnitPrice(new BigDecimal("-11"));
  credit.setTaxRate(BigDecimal.TEN);credit.setTaxIncluded(true);items.saveAndFlush(credit);
  assertThat(service.report("Manager",hotel.getId(),today,today.plusDays(3),null).summary().netRoomRevenue()).isEqualByComparingTo("190");
  moved.setTaxRate(null);items.saveAndFlush(moved);
  var unknown=service.report("Manager",hotel.getId(),today,today.plusDays(3),null);
  assertThat(unknown.summary().netRoomRevenue()).isNull();assertThat(unknown.summary().unknownRevenueRoomNights()).isEqualTo(1);
 }
 @Test void monthlyBudgetUsesHotelPrecisionCalendarProrationAndConcurrentEditProtection(){
  LocalDate month=today.plusMonths(1).withDayOfMonth(1);String key=YearMonth.from(month).toString();
  var budget=service.saveBudget("Manager",hotel.getId(),key,new BudgetInput(new BigDecimal("3100"),310,null));
  var report=service.report("Manager",hotel.getId(),month,month.plusDays(10),null);var row=report.months().get(0);
  assertThat(row.proratedBudgetNetRoomRevenue()).isEqualByComparingTo(PmsMoney.round(new BigDecimal("31000").divide(BigDecimal.valueOf(month.lengthOfMonth()),8,java.math.RoundingMode.HALF_UP),"CHF"));
  assertThat(row.coveredDays()).isEqualTo(10);assertThat(row.budget().roomNights()).isEqualTo(310);
  service.saveBudget("Manager",hotel.getId(),key,new BudgetInput(new BigDecimal("3200"),320,budget.version()));
  assertThatThrownBy(()->service.saveBudget("Manager",hotel.getId(),key,new BudgetInput(BigDecimal.TEN,1,budget.version()))).hasMessageContaining("inzwischen geändert");
  assertThatThrownBy(()->service.saveBudget("Manager",hotel.getId(),key,new BudgetInput(new BigDecimal("0.001"),1,budget.version()))).hasMessageContaining("kleinsten Einheit");
 }
 @Test void incompleteOrDuplicatePricingCountsEachAffectedRoomNightOnce(){
  Reservation missing=stay(today.plusDays(1));Reservation duplicate=stay(today.plusDays(1));
  FolioItem unpriced=items.findAllByFolio_Reservation_IdAndRateGeneratedTrueOrderByServiceDateAscIdAsc(missing.getId()).get(0);
  unpriced.setRateGenerated(false);unpriced.setTaxRate(null);items.saveAndFlush(unpriced);
  FolioItem original=items.findAllByFolio_Reservation_IdAndRateGeneratedTrueOrderByServiceDateAscIdAsc(duplicate.getId()).get(0);
  FolioItem repeated=new FolioItem();repeated.setFolio(original.getFolio());repeated.setSourceReservation(duplicate);
  repeated.setServiceDate(original.getServiceDate());repeated.setType(FolioItemType.ROOM);repeated.setDescription("Duplicate room charge");
  repeated.setTotalAmount(original.getTotalAmount());repeated.setUnitPrice(original.getUnitPrice());repeated.setTaxRate(BigDecimal.TEN);repeated.setRateGenerated(true);items.saveAndFlush(repeated);
  var result=service.report("Manager",hotel.getId(),today,today.plusDays(3),null);
  assertThat(result.summary().roomNights()).isEqualTo(2);assertThat(result.summary().unknownRevenueRoomNights()).isEqualTo(2);
  assertThat(result.summary().netRoomRevenue()).isNull();
 }
 @Test void captureAndBudgetRequireReportWriteAccessAndForeignPropertiesRemainUnavailable(){
  doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(access).require(actor,hotel.getId(),"REPORTS",true);
  assertThatThrownBy(()->service.capture("Manager",hotel.getId())).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->service.saveBudget("Manager",hotel.getId(),YearMonth.from(today).toString(),new BudgetInput(BigDecimal.ZERO,0,null))).isInstanceOf(ResponseStatusException.class);
  assertThat(snapshots.count()).isZero();
  service.report("Manager",hotel.getId(),today,today.plusDays(1),null);verify(access).require(actor,hotel.getId(),"REPORTS",false);
  assertThatThrownBy(()->service.report("Manager",999999L,today,today.plusDays(1),null)).hasMessageContaining("Hotel nicht gefunden");
 }
}
