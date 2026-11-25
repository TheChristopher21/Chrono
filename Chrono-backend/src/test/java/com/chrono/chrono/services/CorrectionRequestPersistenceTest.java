package com.chrono.chrono.services;

import com.chrono.chrono.dto.CorrectionRequest;
import com.chrono.chrono.entities.TimeTrackingEntry;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.CorrectionRequestRepository;
import com.chrono.chrono.repositories.TimeTrackingEntryRepository;
import com.chrono.chrono.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false"})
@ActiveProfiles("test")
@Import(CorrectionRequestService.class)
class CorrectionRequestPersistenceTest {
    @Autowired CorrectionRequestService service;
    @Autowired CorrectionRequestRepository requests;
    @Autowired TimeTrackingEntryRepository entries;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @MockitoBean TimeTrackingService timeTrackingService;
    @MockitoBean AccessControlService accessControlService;

    @Test
    void approvalReplacesTheDayOnceAndKeepsOriginalSnapshotsAfterDeletingTheOldStamps() {
        LocalDate day = LocalDate.of(2026, 10, 6);
        User employee = user("snapshot-employee");
        User admin = user("snapshot-admin");
        when(accessControlService.isSuperAdmin(any(User.class))).thenReturn(true);
        TimeTrackingEntry start = entries.save(new TimeTrackingEntry(employee, day.atTime(8, 0), TimeTrackingEntry.PunchType.START, TimeTrackingEntry.PunchSource.MANUAL_PUNCH));
        TimeTrackingEntry end = entries.save(new TimeTrackingEntry(employee, day.atTime(17, 0), TimeTrackingEntry.PunchType.ENDE, TimeTrackingEntry.PunchSource.MANUAL_PUNCH));
        // A historical request may still reference the soon-to-be-deleted stamp.
        CorrectionRequest history = new CorrectionRequest(employee, day, end, day.atTime(17, 15), "Früherer Antrag");
        history.setDenied(true);
        requests.saveAndFlush(history);

        CorrectionRequest first = service.createCorrectionRequest(employee.getUsername(), start.getId(), day.atTime(8, 0), "START", "Tag korrigieren", day);
        CorrectionRequest second = service.createCorrectionRequest(employee.getUsername(), end.getId(), day.atTime(18, 0), "ENDE", "Tag korrigieren", day);
        requests.flush();
        assertThat(entriesFor(employee, day)).extracting(TimeTrackingEntry::getEntryTimestamp).containsExactly(day.atTime(8, 0), day.atTime(17, 0));

        service.approveRequest(first.getId(), "Passt", admin.getUsername());
        em.flush();
        em.clear();
        assertThat(entriesFor(employee, day)).extracting(TimeTrackingEntry::getEntryTimestamp).containsExactly(day.atTime(8, 0), day.atTime(18, 0));
        assertThat(entries.findById(end.getId())).isEmpty();
        CorrectionRequest accepted = requests.findById(second.getId()).orElseThrow();
        assertThat(accepted.isApproved()).isTrue();
        assertThat(accepted.getOriginalTimestamp()).isEqualTo(day.atTime(17, 0));
        assertThat(accepted.getOriginalDayEntries()).extracting(stamp -> stamp.getEntryTimestamp()).containsExactly(day.atTime(8, 0), day.atTime(17, 0));
        assertThat(requests.findById(history.getId()).orElseThrow().getTargetEntry()).isNull();
        service.approveRequest(first.getId(), "Wiederholt", admin.getUsername());
        em.flush();
        assertThat(entriesFor(employee, day)).hasSize(2);
    }

    private java.util.List<TimeTrackingEntry> entriesFor(User user, LocalDate day) {
        return entries.findByUserAndEntryTimestampBetweenOrderByEntryTimestampAsc(user, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
    }

    private User user(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("test-password");
        user.setPersonnelNumber(username);
        user.setCountry("CH");
        return users.saveAndFlush(user);
    }
}
