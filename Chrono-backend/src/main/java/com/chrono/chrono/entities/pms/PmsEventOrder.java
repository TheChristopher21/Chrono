package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Getter @Setter @Table(name="pms_event_orders")
public class PmsEventOrder {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="resource_booking_id",nullable=false,unique=true) private ResourceBooking resourceBooking;
    @Column(name="setup_minutes",nullable=false) private int setupMinutes;
    @Column(name="teardown_minutes",nullable=false) private int teardownMinutes;
    @Column(length=8000) private String agenda;
    @Column(name="setup_instructions",length=4000) private String setupInstructions;
    @Column(name="catering_notes",length=4000) private String cateringNotes;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="posted_folio_id") private Folio postedFolio;
    @Column(name="posted_at") private LocalDateTime postedAt;
    @Column(name="posted_by",length=120) private String postedBy;
}
