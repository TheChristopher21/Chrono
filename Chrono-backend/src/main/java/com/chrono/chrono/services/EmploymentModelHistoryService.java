package com.chrono.chrono.services;

import com.chrono.chrono.entities.EmploymentModelType;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.UserEmploymentModelHistory;
import com.chrono.chrono.repositories.UserEmploymentModelHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EmploymentModelHistoryService {
    private final UserEmploymentModelHistoryRepository historyRepository;

    public EmploymentModelHistoryService(UserEmploymentModelHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    public EmploymentModelType resolveModelForDate(User user, LocalDate date) {
        return resolveHistoryForDate(user, date)
                .map(UserEmploymentModelHistory::getModelType)
                .orElseGet(() -> deriveCurrentModel(user));
    }

    public User resolveUserSnapshotForDate(User user, LocalDate date) {
        return createUserSnapshot(user, resolveHistoryForDate(user, date).orElse(null));
    }

    public Map<LocalDate, User> resolveUserSnapshotsForRange(User user, LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, User> snapshots = new HashMap<>();
        if (user == null || startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return snapshots;
        }

        List<UserEmploymentModelHistory> history = historyRepository.findByUserOrderByEffectiveFromAsc(user);
        UserEmploymentModelHistory active = history.isEmpty() ? null : history.get(0);
        int nextIndex = 0;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            while (nextIndex < history.size()
                    && !history.get(nextIndex).getEffectiveFrom().isAfter(date)) {
                active = history.get(nextIndex);
                nextIndex++;
            }
            snapshots.put(date, createUserSnapshot(user, active));
        }
        return snapshots;
    }

    private User createUserSnapshot(User user, UserEmploymentModelHistory history) {
        User snapshot = new User();
        snapshot.setId(user.getId());
        snapshot.setUsername(user.getUsername());
        snapshot.setCompany(user.getCompany());
        snapshot.setEntryDate(user.getEntryDate());
        snapshot.setIsHourly(user.getIsHourly());
        snapshot.setIsPercentage(user.getIsPercentage());
        snapshot.setWorkPercentage(user.getWorkPercentage());
        snapshot.setExpectedWorkDays(user.getExpectedWorkDays());
        snapshot.setDailyWorkHours(user.getDailyWorkHours());
        snapshot.setScheduleCycle(user.getScheduleCycle());
        snapshot.setWeeklySchedule(copyWeeklySchedule(user.getWeeklySchedule()));
        snapshot.setScheduleEffectiveDate(user.getScheduleEffectiveDate());
        snapshot.setBreakDuration(user.getBreakDuration());
        snapshot.setAnnualVacationDays(user.getAnnualVacationDays());

        if (history == null) {
            return snapshot;
        }

        snapshot.setIsHourly(history.getModelType() == EmploymentModelType.HOURLY);
        snapshot.setIsPercentage(history.getModelType() == EmploymentModelType.PERCENTAGE);
        snapshot.setWorkPercentage(history.getWorkPercentage());
        snapshot.setExpectedWorkDays(history.getExpectedWorkDays());
        snapshot.setDailyWorkHours(history.getDailyWorkHours());
        snapshot.setScheduleCycle(history.getScheduleCycle());
        snapshot.setWeeklySchedule(copyWeeklySchedule(history.getWeeklySchedule()));
        snapshot.setScheduleEffectiveDate(history.getScheduleEffectiveDate());
        return snapshot;
    }

    public EmploymentModelType deriveCurrentModel(User user) {
        if (Boolean.TRUE.equals(user.getIsPercentage())) {
            return EmploymentModelType.PERCENTAGE;
        }
        if (Boolean.TRUE.equals(user.getIsHourly())) {
            return EmploymentModelType.HOURLY;
        }
        return EmploymentModelType.STANDARD;
    }

    public LocalDate currentBerlinDate() {
        return LocalDate.now(ZoneId.of("Europe/Berlin"));
    }

    public LocalDate resolveCurrentOvertimeStreakStart(User user) {
        if (user == null) {
            return null;
        }

        LocalDate today = currentBerlinDate();
        LocalDate fallback = user.getEntryDate();
        List<UserEmploymentModelHistory> history = historyRepository.findByUserOrderByEffectiveFromAsc(user);
        if (history.isEmpty()) {
            return fallback;
        }

        LocalDate streakStart = null;
        for (UserEmploymentModelHistory row : history) {
            if (row.getEffectiveFrom() == null || row.getEffectiveFrom().isAfter(today)) {
                continue;
            }
            if (row.getModelType() == EmploymentModelType.HOURLY) {
                streakStart = null;
                continue;
            }
            if (streakStart == null) {
                streakStart = row.getEffectiveFrom();
            }
        }

        return streakStart != null ? streakStart : fallback;
    }

    public boolean needsBaselineBefore(User user, LocalDate baselineDate) {
        if (user == null || baselineDate == null) {
            return false;
        }
        return historyRepository.findFirstByUserOrderByEffectiveFromAsc(user)
                .map(first -> first.getEffectiveFrom().isAfter(baselineDate))
                .orElse(true);
    }

    @Transactional
    public void initializeIfMissing(User user) {
        if (historyRepository.findFirstByUserOrderByEffectiveFromDesc(user).isPresent()) {
            return;
        }
        UserEmploymentModelHistory row = new UserEmploymentModelHistory();
        LocalDate initialEffectiveFrom = user.getEntryDate() != null ? user.getEntryDate() : currentBerlinDate();
        fillHistoryRowFromUser(row, user, deriveCurrentModel(user), initialEffectiveFrom);
        historyRepository.save(row);
    }

    @Transactional
    public void recordModelChange(User user, EmploymentModelType newModel, LocalDate effectiveFrom) {
        LocalDate effective = effectiveFrom != null ? effectiveFrom : currentBerlinDate();
        Optional<UserEmploymentModelHistory> existingAtDate = historyRepository
                .findFirstByUserAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, effective);
        if (existingAtDate.isPresent()
                && existingAtDate.get().getEffectiveFrom().equals(effective)
                && existingAtDate.get().getModelType() == newModel) {
            return;
        }
        if (existingAtDate.isPresent() && existingAtDate.get().getModelType() == newModel) {
            return;
        }
        Optional<UserEmploymentModelHistory> exactDay = historyRepository.findByUserOrderByEffectiveFromAsc(user)
                .stream()
                .filter(h -> effective.equals(h.getEffectiveFrom()))
                .findFirst();
        UserEmploymentModelHistory row = exactDay.orElseGet(UserEmploymentModelHistory::new);
        fillHistoryRowFromUser(row, user, newModel, effective);
        historyRepository.save(row);
    }

    @Transactional
    public void recordSnapshotChange(User user, LocalDate effectiveFrom) {
        LocalDate effective = effectiveFrom != null ? effectiveFrom : currentBerlinDate();
        EmploymentModelType model = deriveCurrentModel(user);
        Optional<UserEmploymentModelHistory> exactDay = historyRepository.findByUserOrderByEffectiveFromAsc(user)
                .stream()
                .filter(h -> effective.equals(h.getEffectiveFrom()))
                .findFirst();
        UserEmploymentModelHistory row = exactDay.orElseGet(UserEmploymentModelHistory::new);
        fillHistoryRowFromUser(row, user, model, effective);
        historyRepository.save(row);
    }

    @Transactional
    public void ensureBaselineEntry(User user, EmploymentModelType baselineModel, LocalDate baselineFrom) {
        ensureBaselineEntry(user, baselineModel, baselineFrom, user);
    }

    @Transactional
    public void ensureBaselineEntry(User user, EmploymentModelType baselineModel, LocalDate baselineFrom, User snapshotSource) {
        if (baselineModel == null || baselineFrom == null) {
            return;
        }
        Optional<UserEmploymentModelHistory> earliest = historyRepository.findFirstByUserOrderByEffectiveFromAsc(user);
        if (earliest.isPresent()) {
            UserEmploymentModelHistory first = earliest.get();
            if (!first.getEffectiveFrom().isAfter(baselineFrom)) {
                return;
            }
            if (first.getModelType() == baselineModel) {
                fillHistoryRowFromUser(first, snapshotSource != null ? snapshotSource : user, baselineModel, baselineFrom);
                first.setId(earliest.get().getId());
                first.setUser(user);
                historyRepository.save(first);
                return;
            }
        }

        UserEmploymentModelHistory row = new UserEmploymentModelHistory();
        fillHistoryRowFromUser(row, snapshotSource != null ? snapshotSource : user, baselineModel, baselineFrom);
        row.setUser(user);
        historyRepository.save(row);
    }

    private Optional<UserEmploymentModelHistory> resolveHistoryForDate(User user, LocalDate date) {
        Optional<UserEmploymentModelHistory> atOrBefore = historyRepository
                .findFirstByUserAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, date);
        if (atOrBefore.isPresent()) {
            return atOrBefore;
        }
        return historyRepository.findFirstByUserOrderByEffectiveFromAsc(user);
    }

    private void fillHistoryRowFromUser(UserEmploymentModelHistory row, User user, EmploymentModelType model, LocalDate effectiveFrom) {
        row.setUser(user);
        row.setModelType(model);
        row.setEffectiveFrom(effectiveFrom);
        row.setWorkPercentage(user.getWorkPercentage());
        row.setExpectedWorkDays(user.getExpectedWorkDays());
        row.setDailyWorkHours(user.getDailyWorkHours());
        row.setScheduleCycle(user.getScheduleCycle());
        row.setScheduleEffectiveDate(user.getScheduleEffectiveDate());
        row.setWeeklySchedule(copyWeeklySchedule(user.getWeeklySchedule()));
    }

    private List<Map<String, Double>> copyWeeklySchedule(List<Map<String, Double>> source) {
        if (source == null) {
            return new ArrayList<>();
        }
        List<Map<String, Double>> copy = new ArrayList<>();
        for (Map<String, Double> week : source) {
            copy.add(week == null ? new HashMap<>() : new HashMap<>(week));
        }
        return copy;
    }
}
