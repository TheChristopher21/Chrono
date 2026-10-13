package com.chrono.chrono.services.pms;
import com.chrono.chrono.dto.pms.PmsRatePublicationDtos.*;
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
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties="spring.jpa.hibernate.ddl-auto=create-drop")
@Import({PmsRatePublicationService.class,PmsSetupService.class,PmsAuditWriter.class})
@ActiveProfiles("test")
class PmsRatePublicationServiceTest {
    @Autowired PmsRatePublicationService service; @Autowired CompanyRepository companies;
    @Autowired HotelPropertyRepository properties; @Autowired RoomTypeRepository roomTypes; @Autowired RatePlanRepository rates;
    @Autowired PmsAuditEventRepository audit; @MockBean PmsPropertyAccessService access;
    @Autowired PmsSetupService setupService;
    Company company; RatePlan source; HotelProperty kwd,jpy; RoomType kwdType,jpyType;
    @BeforeEach void setup() {
        company=companies.save(new Company("Hotelkette"));
        HotelProperty origin=hotel("CH","CHF");RoomType originType=roomType(origin);
        kwd=hotel("KW","KWD");kwdType=roomType(kwd);jpy=hotel("JP","JPY");jpyType=roomType(jpy);
        source=new RatePlan();source.setProperty(origin);source.setRoomType(originType);source.setCode("BAR");source.setName("Chain template");
        source.setCurrencyCode("CHF");source.setNightlyRate(new BigDecimal("100"));source.setMinStay(2);
        source.setBreakfastIncluded(true);source.setBreakfastAmount(new BigDecimal("20"));source.setVatRate(new BigDecimal("7.7"));
        source.setCancellationDeadlineHours(48);source.setDepositPercent(new BigDecimal("25"));source=rates.saveAndFlush(source);
        when(access.access("Master")).thenReturn(new PmsPropertyAccessService.Access(9L,company.getId(),true,Map.of()));
    }
    HotelProperty hotel(String code,String currency) {
        HotelProperty p=new HotelProperty();p.setCompany(company);p.setCode(code);p.setName(code);p.setCurrencyCode(currency);return properties.save(p);
    }
    RoomType roomType(HotelProperty p) {RoomType t=new RoomType();t.setProperty(p);t.setCode("DBL");t.setName("Double");return roomTypes.save(t);}
    Target target(HotelProperty p,RoomType t,String nightly,String breakfast) {
        return new Target(p.getId(),t.getId(),null,"CHAIN","Chain rate",p.getCurrencyCode(),new BigDecimal(nightly),new BigDecimal(breakfast),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null);
    }
    @Test void publishesExplicitLocalPricesAndTaxToSelectedHotelsPreservingSharedPolicies() {
        var result=service.publish("Master",source.getId(),new Publish(List.of(target(kwd,kwdType,"12.345","1.234"),target(jpy,jpyType,"10000","1000"))));
        assertThat(result.targets()).hasSize(2);
        RatePlan local=rates.findById(result.targets().get(0).ratePlanId()).orElseThrow();
        assertThat(local.getNightlyRate()).isEqualByComparingTo("12.345");assertThat(local.getCurrencyCode()).isEqualTo("KWD");
        assertThat(local.getVatRate()).isZero();assertThat(local.getMinStay()).isEqualTo(2);assertThat(local.getDepositPercent()).isEqualByComparingTo("25");
        assertThat(source.getNightlyRate()).isEqualByComparingTo("100");
        assertThat(audit.findTop100ByProperty_IdOrderByCreatedAtDesc(kwd.getId())).anySatisfy(row -> assertThat(row.getEventType()).isEqualTo("rate_plan.published"));
    }
    @Test void refusesImplicitFxAndCrossTenantTargetsBeforeWritingAnyRate() {
        Target mismatched=new Target(kwd.getId(),kwdType.getId(),null,"CHAIN","Chain rate","CHF",BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null);
        assertThatThrownBy(()->service.publish("Master",source.getId(),new Publish(List.of(mismatched)))).hasMessageContaining("Zielwährung");
        Company other=companies.save(new Company("Other"));HotelProperty outside=new HotelProperty();outside.setCompany(other);outside.setCode("OTHER");outside.setName("Other");outside.setCurrencyCode("CHF");outside=properties.save(outside);
        Target invalid=target(outside,kwdType,"1","0");
        assertThatThrownBy(()->service.publish("Master",source.getId(),new Publish(List.of(target(kwd,kwdType,"1","0"),invalid)))).hasMessageContaining("Zielhotel");
        assertThat(rates.findAllByProperty_IdOrderByRoomType_SortOrderAscNameAsc(kwd.getId())).isEmpty();
    }
    @Test void requiresMasterAndExplicitExistingRateSelectionInsteadOfSilentOverwrite() {
        when(access.access("Staff")).thenReturn(new PmsPropertyAccessService.Access(10L,company.getId(),false,Map.of()));
        assertThatThrownBy(()->service.publish("Staff",source.getId(),new Publish(List.of(target(kwd,kwdType,"1","0"))))).hasMessageContaining("PMS-Master");
        service.publish("Master",source.getId(),new Publish(List.of(target(kwd,kwdType,"1","0"))));
        assertThatThrownBy(()->service.publish("Master",source.getId(),new Publish(List.of(target(kwd,kwdType,"2","0"))))).hasMessageContaining("ausdrücklich auswählen");
    }

    @Test void hotelCurrencyCanBeCorrectedOnlyBeforeCurrencyDependentDataExists() {
        HotelProperty origin=source.getProperty();
        var blocked=new com.chrono.chrono.dto.pms.UpsertHotelPropertyRequest(origin.getCode(),origin.getName(),null,"CH","KWD","Europe/Zurich",
                null,null,null,null,null,java.time.LocalTime.of(15,0),java.time.LocalTime.of(11,0),true);
        assertThatThrownBy(()->setupService.updateProperty(company,origin.getId(),blocked)).hasMessageContaining("nicht mehr geändert");
        var allowed=new com.chrono.chrono.dto.pms.UpsertHotelPropertyRequest(kwd.getCode(),kwd.getName(),null,"CH","CHF","Europe/Zurich",
                null,null,null,null,null,java.time.LocalTime.of(15,0),java.time.LocalTime.of(11,0),true);
        setupService.updateProperty(company,kwd.getId(),allowed);
        assertThat(properties.findById(kwd.getId()).orElseThrow().getCurrencyCode()).isEqualTo("CHF");
    }
}
