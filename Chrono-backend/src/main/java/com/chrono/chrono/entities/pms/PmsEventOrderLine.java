package com.chrono.chrono.entities.pms;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity @Getter @Setter @Table(name="pms_event_order_lines",indexes=@Index(name="idx_pms_event_order_line",columnList="event_order_id"))
public class PmsEventOrderLine {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="event_order_id",nullable=false) private PmsEventOrder eventOrder;
    @Column(nullable=false,length=240) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private FolioItemType type;
    @Column(nullable=false,precision=10,scale=2) private BigDecimal quantity;
    @Column(name="net_unit_price",nullable=false,precision=19,scale=4) private BigDecimal netUnitPrice;
    @Column(name="tax_rate",nullable=false,precision=7,scale=4) private BigDecimal taxRate;
    @Column(name="net_amount",nullable=false,precision=19,scale=4) private BigDecimal netAmount;
    @Column(name="tax_amount",nullable=false,precision=19,scale=4) private BigDecimal taxAmount;
    @Column(name="gross_amount",nullable=false,precision=19,scale=4) private BigDecimal grossAmount;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="folio_item_id",unique=true) private FolioItem folioItem;
}
