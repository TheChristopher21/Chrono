package com.chrono.chrono.entities.pms;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.converters.UserPagePermissionsConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.HashMap;
import java.util.Map;

@Entity @Getter @Setter
@Table(name = "pms_property_grants", uniqueConstraints = @UniqueConstraint(
        name = "uk_pms_property_grant", columnNames = {"user_id", "property_id"}))
public class PmsPropertyGrant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "property_id", nullable = false)
    private HotelProperty property;
    @Convert(converter = UserPagePermissionsConverter.class)
    @Column(name = "permissions", nullable = false, columnDefinition = "TEXT")
    private Map<String, String> permissions = new HashMap<>();
}
