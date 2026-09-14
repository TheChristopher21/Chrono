package com.chrono.chrono.services.pms;
import com.chrono.chrono.dto.pms.*;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.pms.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@DataJpaTest(properties="spring.jpa.hibernate.ddl-auto=create-drop",showSql=false)
@ActiveProfiles("test")
@Import({PmsCommercialExpansionTest.Json.class,PmsRevenueAutomationService.class,PmsRevenuePlanningService.class,PmsFinancialPeriodService.class,PmsAuditWriter.class,PmsEventOrderService.class,PmsEventOfferService.class,PmsRateInheritanceService.class})
class PmsCommercialExpansionTest {
 @org.springframework.boot.test.context.TestConfiguration static class Json {@Bean ObjectMapper objectMapper(){return new ObjectMapper().findAndRegisterModules();}}
 @Autowired EntityManager em;@Autowired CompanyRepository companies;@Autowired HotelPropertyRepository properties;@Autowired RoomTypeRepository roomTypes;@Autowired RatePlanRepository rates;@Autowired RoomRepository rooms;
 @Autowired PmsRevenueSnapshotRepository snapshots;@Autowired RateOverrideRepository overrides;@Autowired HotelResourceRepository resources;@Autowired ResourceBookingRepository bookings;
 @Autowired PmsRevenueAutomationService revenue;@Autowired PmsRateInheritanceService inheritance;@Autowired PmsEventOfferService offers;@Autowired PmsEventOrderService orders;
 @MockBean PmsPropertyAccessService access;
 Company company;HotelProperty hotel;RoomType type;RatePlan rate;LocalDate today;
 @BeforeEach void setup(){company=companies.save(new Company("Commercial test"));hotel=new HotelProperty();hotel.setCompany(company);hotel.setCode("COMM");hotel.setName("Commercial hotel");hotel.setTimezone("UTC");hotel.setCurrencyCode("CHF");hotel=properties.save(hotel);today=LocalDate.now(ZoneOffset.UTC);type=new RoomType();type.setProperty(hotel);type.setCode("DBL");type.setName("Double");type=roomTypes.save(type);rate=rate("BASE");Room room=new Room();room.setProperty(hotel);room.setRoomType(type);room.setNumber("101");rooms.save(room);var actor=new PmsPropertyAccessService.Access(1L,company.getId(),true,Map.of());when(access.access("Master")).thenReturn(actor);when(access.requireMaster("Master")).thenReturn(actor);}
 RatePlan rate(String code){RatePlan r=new RatePlan();r.setProperty(hotel);r.setRoomType(type);r.setCode(code);r.setName(code);r.setCurrencyCode("CHF");r.setNightlyRate(new BigDecimal("100"));r.setVatRate(BigDecimal.TEN);return rates.saveAndFlush(r);}
 @Test void scheduledSnapshotIsIdempotentAndDoesNotInventHistoricalData(){revenue.runProperty(hotel.getId());revenue.runProperty(hotel.getId());assertThat(snapshots.count()).isEqualTo(1);var f=revenue.forecast("Master",hotel.getId(),today,2);assertThat(f.observedDays()).isZero();assertThat(f.days()).allSatisfy(d->assertThat(d.expectedRoomNights()).isNull());}
 @Test void automationSkipsManualPricesAndIsLimitedToOneRunPerDay(){
  for(int i=1;i<=4;i++){NightAudit audit=new NightAudit();audit.setProperty(hotel);audit.setBusinessDate(today.minusWeeks(i));audit.setInHouseCount(1);audit.setClosedBy("Master");audit.setClosedAt(today.minusWeeks(i).atTime(23,59));audit.setOpenBalance(BigDecimal.ZERO);em.persist(audit);}
  var rule=new PmsRevenueAutomationDtos.Rule(rate.getId(),new BigDecimal("50"),new BigDecimal("200"),new BigDecimal("5"),40,80,BigDecimal.TEN);
  revenue.save("Master",hotel.getId(),new PmsRevenueAutomationDtos.Config(hotel.getId(),0,true,true,1,4,List.of(rule)));
  RateOverride manual=new RateOverride();manual.setRatePlan(rate);manual.setStayDate(today);manual.setPrice(new BigDecimal("80"));overrides.saveAndFlush(manual);
  revenue.runProperty(hotel.getId());assertThat(overrides.findById(manual.getId()).orElseThrow().getPrice()).isEqualByComparingTo("80");
  manual.setRevenueManaged(true);overrides.saveAndFlush(manual);revenue.runProperty(hotel.getId());assertThat(manual.getPrice()).isEqualByComparingTo("80");
 }
 PmsRateInheritanceDtos.Input rule(RatePlan source,RatePlan target,Long version,boolean frozen){return new PmsRateInheritanceDtos.Input(source.getId(),target.getId(),version,true,frozen,true,BigDecimal.ONE,today,today.plusDays(30),"Same-currency policy",BigDecimal.TEN,new BigDecimal("50"),new BigDecimal("200"));}
 @Test void inheritedRatesKeepLocalTaxAndLocalEditsFreezeFurtherChanges(){RatePlan target=rate("TARGET");target.setVatRate(new BigDecimal("8.1"));rates.saveAndFlush(target);var rule=inheritance.save("Master",hotel.getId(),rule(rate,target,null,false));inheritance.apply("Master",hotel.getId(),rule.id());assertThat(target.getNightlyRate()).isEqualByComparingTo("110");assertThat(target.getVatRate()).isEqualByComparingTo("8.1");inheritance.freezeOnManualEdit(target.getId());rate.setNightlyRate(new BigDecimal("130"));rates.saveAndFlush(rate);assertThatThrownBy(()->inheritance.apply("Master",hotel.getId(),rule.id())).hasMessageContaining("eingefroren");assertThat(target.getNightlyRate()).isEqualByComparingTo("110");}
 @Test void inheritanceRejectsCyclesAndFictitiousSameCurrencyFx(){RatePlan target=rate("CHILD");inheritance.save("Master",hotel.getId(),rule(rate,target,null,false));assertThatThrownBy(()->inheritance.save("Master",hotel.getId(),rule(target,rate,null,false))).hasMessageContaining("Kreis");var wrong=new PmsRateInheritanceDtos.Input(rate.getId(),target.getId(),0L,false,false,false,new BigDecimal("2"),today,today.plusDays(1),"Invalid",BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("200"));assertThatThrownBy(()->inheritance.save("Master",hotel.getId(),wrong)).hasMessageContaining("gleicher Währung");}
 ResourceBooking booking(){HotelResource resource=new HotelResource();resource.setProperty(hotel);resource.setName("Ballroom");resource.setCode("BALL");resource.setType(HotelResourceType.CONFERENCE_ROOM);resource.setCapacity(100);resource.setCurrencyCode("CHF");resource.setHourlyRate(BigDecimal.ZERO);resources.save(resource);ResourceBooking b=new ResourceBooking();b.setProperty(hotel);b.setResource(resource);b.setTitle("Conference");b.setOrganizerName("Client");b.setStartAt(today.plusDays(3).atTime(10,0));b.setEndAt(today.plusDays(3).atTime(12,0));b.setOccupiedFrom(b.getStartAt());b.setOccupiedUntil(b.getEndAt());b.setAttendees(20);b.setTotalAmount(BigDecimal.ZERO);b.setCreatedBy("Master");b.setStatus(ResourceBookingStatus.CONFIRMED);return bookings.saveAndFlush(b);}
 void order(ResourceBooking b,int price){orders.save(company,hotel.getId(),b.getId(),new PmsEventOrderDto.Save(30,30,"Welcome","Boardroom","Vegetarian",List.of(new PmsEventOrderDto.Line("Conference",BigDecimal.ONE,new BigDecimal(price),BigDecimal.TEN,FolioItemType.SERVICE))));}
 @Test void immutableOfferVersionsSupersedeOldLinksAndDecisionIsSingleUse(){ResourceBooking b=booking();order(b,100);var first=offers.create("Master",hotel.getId(),b.getId(),new PmsEventOfferDtos.Create(today.plusDays(1).atTime(12,0),"Payment after event"));var oldLink=offers.link("Master",hotel.getId(),b.getId(),first.id());order(b,200);var second=offers.create("Master",hotel.getId(),b.getId(),new PmsEventOfferDtos.Create(today.plusDays(1).atTime(12,0),"Revised terms"));assertThat(offers.list("Master",hotel.getId(),b.getId()).get(1).order().netAmount()).isEqualByComparingTo("100");assertThatThrownBy(()->offers.decide(oldLink.token(),new PmsEventOfferDtos.Decision("ACCEPTED","Client",null,true))).hasMessageContaining("ersetzt");var link=offers.link("Master",hotel.getId(),b.getId(),second.id());var accepted=offers.decide(link.token(),new PmsEventOfferDtos.Decision("ACCEPTED","Client",null,true));assertThat(accepted.acceptanceHash()).hasSize(64);assertThatThrownBy(()->offers.decide(link.token(),new PmsEventOfferDtos.Decision("DECLINED","Other",null,false))).hasMessageContaining("beantwortet");}
 @Test void acceptedOfferMustBeAppliedAndChangedBeoCannotBeCharged(){ResourceBooking b=booking();order(b,100);var offer=offers.create("Master",hotel.getId(),b.getId(),new PmsEventOfferDtos.Create(today.plusDays(1).atTime(12,0),"Approved terms"));var link=offers.link("Master",hotel.getId(),b.getId(),offer.id());offers.decide(link.token(),new PmsEventOfferDtos.Decision("ACCEPTED","Client",null,true));offers.apply("Master",hotel.getId(),b.getId(),offer.id());order(b,200);assertThatThrownBy(()->orders.post(company,hotel.getId(),b.getId(),new PmsEventOrderDto.Post(null),"Master")).hasMessageContaining("weicht");}
}
