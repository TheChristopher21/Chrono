package com.chrono.chrono.entities.inventory;

import com.chrono.chrono.entities.Company;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "inv_stock_reservations",
        indexes = {
                @Index(name = "idx_inv_stock_reservations_company", columnList = "company_id"),
                @Index(name = "idx_inv_stock_reservations_order_line", columnList = "sales_order_line_id,status"),
                @Index(name = "idx_inv_stock_reservations_stock", columnList = "stock_level_id,status")
        }
)
public class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_line_id", nullable = false)
    private SalesOrderLine salesOrderLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_level_id", nullable = false)
    private StockLevel stockLevel;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private StockReservationStatus status = StockReservationStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Version
    private Long version;

    @PreUpdate
    public void touch() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public SalesOrderLine getSalesOrderLine() { return salesOrderLine; }
    public void setSalesOrderLine(SalesOrderLine salesOrderLine) { this.salesOrderLine = salesOrderLine; }
    public StockLevel getStockLevel() { return stockLevel; }
    public void setStockLevel(StockLevel stockLevel) { this.stockLevel = stockLevel; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public StockReservationStatus getStatus() { return status; }
    public void setStatus(StockReservationStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Long getVersion() { return version; }
}
