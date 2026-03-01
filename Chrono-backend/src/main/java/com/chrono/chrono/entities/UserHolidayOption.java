package com.chrono.chrono.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Objects;

@Setter
@Getter
@Entity
@Table(name = "user_holiday_options",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "holiday_date"})
        })
public class UserHolidayOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "holiday_handling_option", nullable = false)
    private HolidayHandlingOption holidayHandlingOption = HolidayHandlingOption.PENDING_DECISION;

    public enum HolidayHandlingOption {
        PENDING_DECISION, // Admin muss noch entscheiden
        DEDUCT_FROM_WEEKLY_TARGET, // Feiertag reduziert das Wochensoll (Standard für normale Mitarbeiter)
        DO_NOT_DEDUCT_FROM_WEEKLY_TARGET // Feiertag reduziert das Wochensoll NICHT (relevant für prozentuale)
    }

    public UserHolidayOption() {
    }

    public UserHolidayOption(User user, LocalDate holidayDate, HolidayHandlingOption holidayHandlingOption) {
        this.user = user;
        this.holidayDate = holidayDate;
        this.holidayHandlingOption = holidayHandlingOption;
    }

    // Getter and Setter

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserHolidayOption that = (UserHolidayOption) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(user.getId(), that.user.getId()) && // Vergleiche IDs für Entitäten
                Objects.equals(holidayDate, that.holidayDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, user != null ? user.getId() : null, holidayDate); // Verwende ID für Hash
    }
}