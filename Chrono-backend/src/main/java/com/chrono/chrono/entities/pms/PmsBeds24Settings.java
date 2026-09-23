package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;import lombok.Getter;import lombok.Setter;import java.time.LocalDateTime;
@Entity @Getter @Setter @Table(name="pms_beds24_settings")
public class PmsBeds24Settings {
 @Id @Column(name="property_id") private Long propertyId;
 @Column(name="external_property_id") private Long externalPropertyId;
 @Column(name="secret_reference",length=180) private String secretReference;
 @Column(name="mappings_json",columnDefinition="TEXT") private String mappingsJson;
 @Column(nullable=false) private boolean enabled;
 @Column(name="verified_at") private LocalDateTime verifiedAt;
 @Version private long version;
}
