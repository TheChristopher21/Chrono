package com.chrono.chrono.entities.pms;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "pms_rooms",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pms_room_property_number",
                columnNames = {"property_id", "room_number"}
        ),
        indexes = {
                @Index(name = "idx_pms_room_property", columnList = "property_id"),
                @Index(name = "idx_pms_room_type", columnList = "room_type_id")
        }
)
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private HotelProperty property;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    @Column(name = "room_number", nullable = false, length = 40)
    private String number;

    @Column(length = 120)
    private String name;

    @Column(length = 40)
    private String floor;

    @Column(name = "housekeeping_section", length = 80)
    private String housekeepingSection;

    @Enumerated(EnumType.STRING)
    @Column(name = "operational_status", nullable = false, length = 32)
    private RoomOperationalStatus operationalStatus = RoomOperationalStatus.IN_SERVICE;

    @Enumerated(EnumType.STRING)
    @Column(name = "housekeeping_status", nullable = false, length = 24)
    private HousekeepingStatus housekeepingStatus = HousekeepingStatus.CLEAN;

    @Column(nullable = false)
    private boolean active = true;

    public Long getId() {
        return id;
    }

    public HotelProperty getProperty() {
        return property;
    }

    public void setProperty(HotelProperty property) {
        this.property = property;
    }

    public RoomType getRoomType() {
        return roomType;
    }

    public void setRoomType(RoomType roomType) {
        this.roomType = roomType;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFloor() {
        return floor;
    }

    public void setFloor(String floor) {
        this.floor = floor;
    }

    public String getHousekeepingSection() {
        return housekeepingSection;
    }

    public void setHousekeepingSection(String housekeepingSection) {
        this.housekeepingSection = housekeepingSection;
    }

    public RoomOperationalStatus getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(RoomOperationalStatus operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    public HousekeepingStatus getHousekeepingStatus() {
        return housekeepingStatus;
    }

    public void setHousekeepingStatus(HousekeepingStatus housekeepingStatus) {
        this.housekeepingStatus = housekeepingStatus;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
