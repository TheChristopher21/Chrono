package com.chrono.chrono.services.pms;
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
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@DataJpaTest(properties="spring.jpa.hibernate.ddl-auto=create-drop",showSql=false)
@Import(PmsHistoryService.class) @ActiveProfiles("test")
class PmsHistoryServiceIntegrationTest {
    @Autowired PmsHistoryService service;
    @Autowired CompanyRepository companies;
    @Autowired HotelPropertyRepository properties;
    @Autowired MigrationBatchRepository batches;
    @MockBean PmsPropertyAccessService access;
    Company company; HotelProperty property;
    @BeforeEach void setup() {
        company=companies.save(new Company("Chain")); property=hotel("A");
        when(access.access("staff")).thenReturn(new PmsPropertyAccessService.Access(1L,company.getId(),false,Map.of(property.getId(),Map.of("INTEGRATIONS","VIEW"))));
    }
    HotelProperty hotel(String code) { HotelProperty hotel=new HotelProperty(); hotel.setCompany(company);hotel.setCode(code);hotel.setName(code);return properties.save(hotel); }
    void batch(HotelProperty hotel,String key) { MigrationBatch batch=new MigrationBatch();batch.setProperty(hotel);batch.setIdempotencyKey(key);batch.setSourceSystem("Legacy");batch.setStatus(MigrationBatchStatus.COMPLETED);batch.setCreatedBy("Test");batches.saveAndFlush(batch); }
    @Test void historyPaginatesInDatabaseCountsOnlySelectedHotelAndEscapesSearchWildcards() {
        batch(property,"literal_1"); batch(property,"literalX1"); batch(property,"third"); batch(hotel("B"),"foreign");
        var first=service.page("staff",property.getId(),"migration-batches",0,2,null);
        var second=service.page("staff",property.getId(),"migration-batches",1,2,null);
        assertThat(first.totalElements()).isEqualTo(3); assertThat(first.items()).hasSize(2); assertThat(first.hasNext()).isTrue();
        assertThat(second.items()).hasSize(1); assertThat(second.hasNext()).isFalse();
        assertThat(service.page("staff",property.getId(),"migration-batches",0,20,"literal_").items()).hasSize(1);
        assertThat(service.newest(MigrationBatch.class,"property",property.getId(),1)).hasSize(1);
    }
    @Test void allWhitelistedProjectionsParseAndUnsupportedOrUnboundedRequestsFail() {
        for(String section:List.of("invoices","night-audits","communications","outbox","audit","guest-registrations","resource-bookings","pos-tickets","access-credentials","migration-batches"))
            assertThat(service.page("staff",property.getId(),section,0,10,"needle").items()).as(section).isEmpty();
        assertThatThrownBy(() -> service.page("staff",property.getId(),"invoices",0,101,null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.page("staff",property.getId(),"secret-table",0,10,null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.newest(GuestProfile.class,"company",company.getId(),10)).isInstanceOf(IllegalArgumentException.class);
    }
}
