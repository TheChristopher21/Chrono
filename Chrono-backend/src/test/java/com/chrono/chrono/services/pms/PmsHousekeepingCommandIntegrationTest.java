package com.chrono.chrono.services.pms;
import com.chrono.chrono.dto.pms.PmsHousekeepingDtos;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.pms.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties="spring.jpa.hibernate.ddl-auto=create-drop",showSql=false)
@ActiveProfiles("test")
@Import({PmsHousekeepingCommandService.class,PmsHousekeepingService.class,PmsAuditWriter.class,PmsHousekeepingCommandIntegrationTest.Json.class})
class PmsHousekeepingCommandIntegrationTest {
    @TestConfiguration static class Json { @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); } }
    @MockitoBean PmsPropertyAccessService access;
    @Autowired PmsHousekeepingCommandService commands; @Autowired PmsHousekeepingService housekeeping;
    @Autowired CompanyRepository companies; @Autowired HotelPropertyRepository properties;
    @Autowired RoomTypeRepository types; @Autowired RoomRepository rooms; @Autowired HousekeepingTaskRepository tasks;
    @Autowired EntityManager em;
    Company company; HotelProperty property; Room room; PmsHousekeepingDtos.Task task;
    @BeforeEach void setup() {
        company=companies.save(new Company("Command test")); property=new HotelProperty(); property.setCompany(company); property.setCode("CMD"); property.setName("Command Hotel"); property=properties.save(property);
        RoomType type=new RoomType(); type.setProperty(property);type.setCode("SGL");type.setName("Single");type=types.save(type);
        room=new Room();room.setProperty(property);room.setRoomType(type);room.setNumber("101");room=rooms.save(room);
        var actor=new PmsPropertyAccessService.Access(1L,company.getId(),true,Map.of()); when(access.access("anna")).thenReturn(actor);
        task=housekeeping.create(company,property.getId(),new PmsHousekeepingDtos.Create(room.getId(),LocalDate.now(),HousekeepingWorkType.CLEAN,HousekeepingTaskType.MANUAL,50,30,null,"Anna"));
    }
    PmsHousekeepingCommandService.Command input(UUID id,long version,HousekeepingWorkStatus state) { return new PmsHousekeepingCommandService.Command(id,task.id(),new PmsHousekeepingDtos.Update(version,state,50,30,null,"Anna")); }
    @Test void replaysExactlyOnceEvenAfterAnotherEmployeeHasChangedTheTask() {
        UUID key=UUID.randomUUID(); var request=input(key,task.version(),HousekeepingWorkStatus.DONE);
        var first=commands.execute("anna",property.getId(),request);
        housekeeping.update(company,property.getId(),task.id(),new PmsHousekeepingDtos.Update(first.version(),HousekeepingWorkStatus.IN_PROGRESS,70,40,null,"Ben"));
        var replay=commands.execute("anna",property.getId(),request);
        assertThat(replay).isEqualTo(first);
        assertThat(tasks.findById(task.id()).orElseThrow().getWorkStatus()).isEqualTo(HousekeepingWorkStatus.IN_PROGRESS);
        assertThat(em.createQuery("select count(c) from PmsHousekeepingCommand c",Long.class).getSingleResult()).isEqualTo(1);
    }
    @Test void rejectsChangedPayloadForAnAlreadyUsedCommand() {
        UUID key=UUID.randomUUID(); commands.execute("anna",property.getId(),input(key,task.version(),HousekeepingWorkStatus.DONE));
        assertThatThrownBy(() -> commands.execute("anna",property.getId(),input(key,task.version(),HousekeepingWorkStatus.OPEN))).hasMessageContaining("anderen Angaben");
        assertThat(tasks.findById(task.id()).orElseThrow().getWorkStatus()).isEqualTo(HousekeepingWorkStatus.DONE);
    }
    @Test void rejectsStaleOfflineVersionsWithoutRecordingASuccessfulCommand() {
        housekeeping.update(company,property.getId(),task.id(),new PmsHousekeepingDtos.Update(task.version(),HousekeepingWorkStatus.IN_PROGRESS,50,30,null,"Ben"));
        assertThatThrownBy(() -> commands.execute("anna",property.getId(),input(UUID.randomUUID(),task.version(),HousekeepingWorkStatus.DONE))).hasMessageContaining("zwischenzeitlich");
        assertThat(em.createQuery("select count(c) from PmsHousekeepingCommand c",Long.class).getSingleResult()).isZero();
    }
    @Test void batchCreatesIndependentTasksAndRetryPreservesExistingWork() {
        var request=new PmsHousekeepingDtos.BatchCreate(List.of(room.getId()),LocalDate.now(),LocalDate.now().plusDays(2),HousekeepingWorkType.TURNDOWN,"Evening","Bitte prüfen",50,30);
        assertThat(housekeeping.createBatch(company,property.getId(),request)).isEqualTo(new PmsHousekeepingDtos.BatchResult(2,0));
        assertThat(housekeeping.createBatch(company,property.getId(),request)).isEqualTo(new PmsHousekeepingDtos.BatchResult(0,2));
        assertThat(tasks.findById(task.id()).orElseThrow().getWorkStatus()).isEqualTo(HousekeepingWorkStatus.OPEN);
    }
}
