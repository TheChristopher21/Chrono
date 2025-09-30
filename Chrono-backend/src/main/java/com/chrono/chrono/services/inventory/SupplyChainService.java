package com.chrono.chrono.services.inventory;

import com.chrono.chrono.entities.inventory.*;
import com.chrono.chrono.entities.accounting.InvoiceStatus;
import com.chrono.chrono.entities.accounting.VendorInvoice;
import com.chrono.chrono.repositories.inventory.*;
import com.chrono.chrono.services.accounting.AccountsPayableService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class SupplyChainService {

    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockLevelRepository stockLevelRepository;
    private final StockMovementRepository stockMovementRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final ProductionOrderRepository productionOrderRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final AccountsPayableService accountsPayableService;

    public SupplyChainService(ProductRepository productRepository,
                              WarehouseRepository warehouseRepository,
                              StockLevelRepository stockLevelRepository,
                              StockMovementRepository stockMovementRepository,
                              PurchaseOrderRepository purchaseOrderRepository,
                              SalesOrderRepository salesOrderRepository,
                              ProductionOrderRepository productionOrderRepository,
                              ServiceRequestRepository serviceRequestRepository,
                              AccountsPayableService accountsPayableService) {
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.stockLevelRepository = stockLevelRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.productionOrderRepository = productionOrderRepository;
        this.serviceRequestRepository = serviceRequestRepository;
        this.accountsPayableService = accountsPayableService;
    }

    @Transactional
    public Product saveProduct(Product product) {
        return productRepository.save(product);
    }

    @Transactional
    public Warehouse saveWarehouse(Warehouse warehouse) {
        return warehouseRepository.save(warehouse);
    }

    @Transactional
    public StockMovement adjustStock(Product product, Warehouse warehouse, BigDecimal quantity, StockMovementType type, String reference) {
        if (quantity == null) {
            throw new IllegalArgumentException("Quantity change is required");
        }
        StockLevel level = stockLevelRepository.findByProductAndWarehouse(product, warehouse)
                .orElseGet(() -> {
                    StockLevel sl = new StockLevel();
                    sl.setProduct(product);
                    sl.setWarehouse(warehouse);
                    return stockLevelRepository.save(sl);
                });
        BigDecimal newQty = level.getQuantity().add(quantity);
        level.setQuantity(newQty);
        stockLevelRepository.save(level);

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setWarehouse(warehouse);
        movement.setType(type);
        movement.setQuantityChange(quantity);
        movement.setReference(reference);
        return stockMovementRepository.save(movement);
    }

    @Transactional
    public PurchaseOrder createPurchaseOrder(PurchaseOrder purchaseOrder) {
        BigDecimal total = BigDecimal.ZERO;
        if (purchaseOrder.getLines() != null) {
            for (PurchaseOrderLine line : purchaseOrder.getLines()) {
                line.setPurchaseOrder(purchaseOrder);
                BigDecimal lineTotal = line.getUnitCost().multiply(line.getQuantity());
                total = total.add(lineTotal);
            }
        }
        purchaseOrder.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));
        return purchaseOrderRepository.save(purchaseOrder);
    }

    @Transactional
    public PurchaseOrder receivePurchaseOrder(Long purchaseOrderId, Warehouse warehouse) {
        PurchaseOrder order = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow();
        if (order.getStatus() == PurchaseOrderStatus.RECEIVED) {
            return order;
        }
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled purchase orders cannot be received");
        }
        order.setStatus(PurchaseOrderStatus.RECEIVED);
        order.setExpectedDate(LocalDate.now());
        purchaseOrderRepository.save(order);
        if (order.getLines() != null) {
            for (PurchaseOrderLine line : order.getLines()) {
                adjustStock(line.getProduct(), warehouse, line.getQuantity(), StockMovementType.RECEIPT,
                        order.getOrderNumber());
            }
        }
        VendorInvoice invoice = new VendorInvoice();
        invoice.setVendorName(order.getVendorName());
        invoice.setInvoiceNumber(order.getOrderNumber());
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setAmount(order.getTotalAmount());
        invoice.setStatus(InvoiceStatus.OPEN);
        accountsPayableService.recordVendorInvoice(invoice);
        return order;
    }

    @Transactional
    public SalesOrder createSalesOrder(SalesOrder salesOrder) {
        BigDecimal total = BigDecimal.ZERO;
        if (salesOrder.getLines() != null) {
            for (SalesOrderLine line : salesOrder.getLines()) {
                line.setSalesOrder(salesOrder);
                BigDecimal lineTotal = line.getUnitPrice().multiply(line.getQuantity());
                total = total.add(lineTotal);
            }
        }
        salesOrder.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));
        return salesOrderRepository.save(salesOrder);
    }

    @Transactional
    public SalesOrder fulfillSalesOrder(Long salesOrderId, Warehouse warehouse) {
        SalesOrder order = salesOrderRepository.findById(salesOrderId).orElseThrow();
        if (order.getStatus() == SalesOrderStatus.FULFILLED) {
            return order;
        }
        if (order.getStatus() == SalesOrderStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled sales orders cannot be fulfilled");
        }
        if (order.getLines() != null) {
            for (SalesOrderLine line : order.getLines()) {
                adjustStock(line.getProduct(), warehouse, line.getQuantity().negate(), StockMovementType.ISSUE,
                        order.getOrderNumber());
            }
        }
        order.setStatus(SalesOrderStatus.FULFILLED);
        order.setDueDate(LocalDate.now());
        return salesOrderRepository.save(order);
    }

    @Transactional
    public ProductionOrder saveProductionOrder(ProductionOrder productionOrder) {
        return productionOrderRepository.save(productionOrder);
    }

    @Transactional
    public ServiceRequest logServiceRequest(ServiceRequest request) {
        if (request.getOpenedDate() == null) {
            request.setOpenedDate(LocalDate.now());
        }
        return serviceRequestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public Page<StockLevel> listStockLevels(Pageable pageable) {
        return stockLevelRepository.findAll(pageable);
    }
}
