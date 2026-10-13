package com.chrono.chrono.entities.pms;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(
        name = "pms_communication_templates",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pms_template_property_code",
                columnNames = {"property_id", "code"}
        )
)
public class CommunicationTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private HotelProperty property;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(nullable = false, length = 240)
    private String subject;

    @Column(nullable = false, length = 8000)
    private String body;

    @Column(name = "language_code", nullable = false, length = 8)
    private String languageCode = "de";

    @Column(nullable = false)
    private boolean active = true;
}
