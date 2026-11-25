package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
@Entity @Getter @Setter
@Table(name="pms_bank_imports",uniqueConstraints=@UniqueConstraint(name="uk_pms_bank_import_hash",columnNames={"property_id","file_hash"}))
public class PmsBankImport {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="property_id") private HotelProperty property;
 @Column(nullable=false,length=180) private String filename;
 @Column(name="file_hash",nullable=false,length=64) private String fileHash;
 private int rowCount;
 private LocalDateTime createdAt;
 @Column(length=120) private String createdBy;
}
