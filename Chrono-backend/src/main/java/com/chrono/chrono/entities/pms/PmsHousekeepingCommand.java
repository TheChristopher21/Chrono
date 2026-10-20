package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
@Entity @Getter @Setter @Table(name="pms_housekeeping_commands", uniqueConstraints=@UniqueConstraint(name="ux_pms_hk_command", columnNames={"property_id","command_id"}))
public class PmsHousekeepingCommand {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="property_id",nullable=false) private HotelProperty property;
    @Column(name="command_id",nullable=false,length=36) private String commandId;
    @Column(name="request_hash",nullable=false,length=64) private String requestHash;
    @Column(nullable=false,length=120) private String actor;
    @Lob @Column(name="result_json",nullable=false,columnDefinition="longtext") private String resultJson;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
}
