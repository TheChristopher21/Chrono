package com.chrono.chrono.services.pms;
import com.chrono.chrono.dto.pms.PmsHousekeepingDtos;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class PmsHousekeepingServiceTest {
    HotelPropertyRepository properties=mock(HotelPropertyRepository.class);
    RoomRepository rooms=mock(RoomRepository.class);
    HousekeepingTaskRepository tasks=mock(HousekeepingTaskRepository.class);
    HousekeepingTaskEventRepository events=mock(HousekeepingTaskEventRepository.class);
    PmsAuditWriter audit=mock(PmsAuditWriter.class);
    PmsHousekeepingService service=new PmsHousekeepingService(properties,rooms,tasks,events,audit);
    Company company=new Company("Hotel chain"); HotelProperty property=new HotelProperty(); Room room=new Room();
    Map<Long,HousekeepingTask> stored=new LinkedHashMap<>();
    LocalDate date=LocalDate.now(ZoneId.of("Europe/Zurich"));
    @BeforeEach void setup() {
        company.setId(3L); org.springframework.test.util.ReflectionTestUtils.setField(property, "id", 5L); property.setCompany(company); org.springframework.test.util.ReflectionTestUtils.setField(room, "id", 8L); room.setProperty(property); room.setNumber("101"); room.setHousekeepingStatus(HousekeepingStatus.DIRTY);
        when(properties.findByIdAndCompany_IdForUpdate(5L,3L)).thenReturn(Optional.of(property));
        when(properties.findByIdAndCompany_Id(5L,3L)).thenReturn(Optional.of(property));
        when(rooms.findByIdAndProperty_Company_Id(8L,3L)).thenReturn(Optional.of(room));
        when(tasks.findByRoom_IdAndServiceDateAndWorkType(eq(8L),any(),any())).thenAnswer(call -> stored.values().stream().filter(task -> task.getServiceDate().equals(call.getArgument(1)) && task.getWorkType()==call.getArgument(2)).findFirst());
        when(tasks.saveAndFlush(any())).thenAnswer(call -> { HousekeepingTask task=call.getArgument(0); if(task.getId()==null) task.setId((long)stored.size()+1); else task.setVersion(task.getVersion()+1); stored.put(task.getId(),task); return task; });
        when(tasks.findByIdAndProperty_Company_Id(anyLong(),eq(3L))).thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
    }
    PmsHousekeepingDtos.Task create(HousekeepingWorkType type) { return service.create(company,5L,new PmsHousekeepingDtos.Create(8L,date,type,HousekeepingTaskType.MANUAL,50,30,null,"Anna")); }
    PmsHousekeepingDtos.Task update(PmsHousekeepingDtos.Task task,HousekeepingWorkStatus status,String notes) { return service.update(company,5L,task.id(),new PmsHousekeepingDtos.Update(task.version(),status,50,30,notes,"Ben")); }
    @Test void cleaningInspectionAndTurndownCoexistAndRequireCleaningBeforeInspection() {
        var clean=create(HousekeepingWorkType.CLEAN); var inspection=create(HousekeepingWorkType.INSPECTION); var turndown=create(HousekeepingWorkType.TURNDOWN);
        assertThat(stored).hasSize(3);
        assertThatThrownBy(() -> update(inspection,HousekeepingWorkStatus.DONE,null)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Reinigung abgeschlossen");
        update(turndown,HousekeepingWorkStatus.DONE,null);
        assertThat(room.getHousekeepingStatus()).isEqualTo(HousekeepingStatus.DIRTY);
        update(clean,HousekeepingWorkStatus.DONE,null);
        assertThat(room.getHousekeepingStatus()).isEqualTo(HousekeepingStatus.INSPECTION);
        update(inspection,HousekeepingWorkStatus.DONE,null);
        assertThat(room.getHousekeepingStatus()).isEqualTo(HousekeepingStatus.CLEAN);
        verify(events,times(6)).save(any(HousekeepingTaskEvent.class));
    }
    @Test void assignmentDndAndDeferralAreTrackedAndOldVersionIsRejected() {
        var task=create(HousekeepingWorkType.CLEAN);
        assertThatThrownBy(() -> update(task,HousekeepingWorkStatus.DND,null)).isInstanceOf(ResponseStatusException.class);
        var dnd=update(task,HousekeepingWorkStatus.DND,"Bitte nicht stören bis 14 Uhr");
        assertThat(dnd.assignedTo()).isEqualTo("Ben"); assertThat(dnd.completedAt()).isNull();
        assertThatThrownBy(() -> update(task,HousekeepingWorkStatus.DONE,null)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("zwischenzeitlich");
        var deferred=update(dnd,HousekeepingWorkStatus.DEFERRED,"Nach Rückkehr des Gastes");
        assertThat(deferred.workStatus()).isEqualTo(HousekeepingWorkStatus.DEFERRED);
        verify(events,times(3)).save(any());
    }
    @Test void departureReopensCleaningAndInspectionWithoutChangingCompletedTurndown() {
        room.setHousekeepingStatus(HousekeepingStatus.CLEAN);
        var clean=create(HousekeepingWorkType.CLEAN); var inspection=create(HousekeepingWorkType.INSPECTION); var turndown=create(HousekeepingWorkType.TURNDOWN);
        update(clean,HousekeepingWorkStatus.DONE,null); update(inspection,HousekeepingWorkStatus.DONE,null); update(turndown,HousekeepingWorkStatus.DONE,null);
        service.departure(property,room,date);
        assertThat(stored.get(clean.id()).getWorkStatus()).isEqualTo(HousekeepingWorkStatus.OPEN);
        assertThat(stored.get(inspection.id()).getCompletedAt()).isNull();
        assertThat(stored.get(turndown.id()).getWorkStatus()).isEqualTo(HousekeepingWorkStatus.DONE);
    }
    @Test void futureWorkDoesNotChangeTodaysReadinessAndDuplicateKindIsRejected() {
        room.setHousekeepingStatus(HousekeepingStatus.CLEAN); date=date.plusDays(1);
        create(HousekeepingWorkType.CLEAN);
        assertThat(room.getHousekeepingStatus()).isEqualTo(HousekeepingStatus.CLEAN);
        assertThatThrownBy(() -> create(HousekeepingWorkType.CLEAN)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("bereits eine Aufgabe");
    }
    @Test void legacyReasonChangeDoesNotInvalidateItsOwnVersionedUpdate() {
        var created=create(HousekeepingWorkType.CLEAN);
        when(properties.findByIdAndCompany_IdForUpdate(5L,3L)).thenAnswer(call -> {
            // Simulate JPA auto-flushing a changed reason before a query.
            stored.values().stream().filter(task -> task.getType()!=HousekeepingTaskType.MANUAL)
                    .forEach(task -> task.setVersion(task.getVersion()+1));
            return Optional.of(property);
        });
        service.legacyUpdate(company,5L,created.id(),new com.chrono.chrono.dto.pms.UpdateHousekeepingTaskRequest(HousekeepingTaskType.INSPECTION,HousekeepingStatus.CLEAN,50,30,null,"Anna"));
        assertThat(stored.get(created.id()).getWorkStatus()).isEqualTo(HousekeepingWorkStatus.DONE);
        assertThat(stored.get(created.id()).getType()).isEqualTo(HousekeepingTaskType.INSPECTION);
    }
}
