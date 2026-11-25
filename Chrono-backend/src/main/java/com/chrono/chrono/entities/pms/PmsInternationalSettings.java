package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity @Getter @Setter @Table(name="pms_international_settings")
public class PmsInternationalSettings {
 @Id @Column(name="property_id") private Long propertyId;
 @Version private long version;
 @Column(name="rule_code",nullable=false,length=40) private String ruleCode;
 @Column(name="jurisdiction_label",nullable=false,length=180) private String jurisdictionLabel;
 @Column(name="source_reference",nullable=false,length=500) private String sourceReference;
 @Column(name="required_fields_json",nullable=false,columnDefinition="TEXT") private String requiredFieldsJson;
 @Column(name="tax_scheme",nullable=false,length=10) private String taxScheme="VAT";
 @Column(name="zero_tax_category",length=3) private String zeroTaxCategory;
 @Column(name="zero_tax_reason",length=500) private String zeroTaxReason;
}
