package com.chrono.chrono.services.inventory;

import com.chrono.chrono.dto.inventory.AutoReplenishPlanDTO;
import com.chrono.chrono.dto.inventory.AutoReplenishRequest;
import com.chrono.chrono.dto.inventory.AutoReplenishResponse;
import com.chrono.chrono.dto.inventory.PlanWavePickRequest;
import com.chrono.chrono.dto.inventory.WavePickResponse;
import com.chrono.chrono.dto.inventory.WavePickWaveDTO;
import com.chrono.chrono.entities.accounting.InvoiceStatus;
import com.chrono.chrono.entities.accounting.VendorInvoice;
import com.chrono.chrono.entities.inventory.*;
import com.chrono.chrono.repositories.accounting.VendorInvoiceRepository;
import com.chrono.chrono.repositories.inventory.StockLevelRepository;
import com.chrono.chrono.services.accounting.AccountingService;
import com.chrono.chrono.services.accounting.AccountsPayableService;
import com.chrono.chrono.warehouse.service.WarehouseIntelligenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({AccountingService.class, AccountsPayableService.class, WarehouseIntelligenceService.class, SupplyChainService.class})
@ActiveProfiles("test")
class SupplyChainServiceTest {

    @Autowired
    private SupplyChainService supplyChainService;

    @Autowired
    private StockLevelRepository stockLevelRepository;

    @Autowired
    private VendorInvoiceRepository vendorInvoiceRepository;

    @Test
    void receivePurchaseOrderUpdatesStockAndCreatesInvoice() {
        Product product = new Product();
        product.setSku("SKU-1");
        product.setName("Widget");
        product.setUnitCost(new BigDecimal("20.00"));
        product = supplyChainService.saveProduct(product);

        Warehouse warehouse = new Warehouse();
        warehouse.setCode("MAIN");
        warehouse.setName("Main Warehouse");
        warehouse = supplyChainService.saveWarehouse(warehouse);

        supplyChainService.adjustStock(product, warehouse, new BigDecimal("10"), StockMovementType.RECEIPT, "Initial", null, null, null);
        assertThat(stockLevelRepository.findByProductAndWarehouse(product, warehouse))
                .isPresent()
                .get()
                .extracting(StockLevel::getQuantity)
                .isEqualTo(new BigDecimal("10"));

        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNumber("PO-1000");
        order.setVendorName("Supply AG");

        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setProduct(product);
        line.setQuantity(new BigDecimal("5"));
        line.setUnitCost(new BigDecimal("20.00"));
        order.setLines(new java.util.ArrayList<>(List.of(line)));

        order = supplyChainService.createPurchaseOrder(order);
        supplyChainService.receivePurchaseOrder(order.getId(), warehouse);

        StockLevel updated = stockLevelRepository.findByProductAndWarehouse(product, warehouse).orElseThrow();
        assertThat(updated.getQuantity()).isEqualByComparingTo(new BigDecimal("15"));

        VendorInvoice invoice = vendorInvoiceRepository.findAll().stream()
                .filter(inv -> "PO-1000".equals(inv.getInvoiceNumber()))
                .findFirst()
                .orElseThrow();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OPEN);
        assertThat(invoice.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void adjustStockPersistsTraceabilityMetadata() {
        Product product = new Product();
        product.setSku("SKU-TRACE");
        product.setName("Traceable Widget");
        product = supplyChainService.saveProduct(product);

        Warehouse warehouse = new Warehouse();
        warehouse.setCode("TRACE");
        warehouse.setName("Traceability Hub");
        warehouse = supplyChainService.saveWarehouse(warehouse);

        LocalDate expiration = LocalDate.now().plusMonths(6);
        StockMovement movement = supplyChainService.adjustStock(product, warehouse, new BigDecimal("4.5"),
                StockMovementType.RECEIPT, "LOT-INIT", "LOT-2024-01", "SN-0001", expiration);

        assertThat(movement.getLotNumber()).isEqualTo("LOT-2024-01");
        assertThat(movement.getSerialNumber()).isEqualTo("SN-0001");
        assertThat(movement.getExpirationDate()).isEqualTo(expiration);

        StockMovement persisted = supplyChainService.adjustStock(product, warehouse, new BigDecimal("-1.5"),
                StockMovementType.ADJUSTMENT, "LOT-CORR", "LOT-2024-01", "SN-0001", expiration);

        assertThat(persisted.getLotNumber()).isEqualTo("LOT-2024-01");
        assertThat(persisted.getSerialNumber()).isEqualTo("SN-0001");
        assertThat(persisted.getExpirationDate()).isEqualTo(expiration);
    }

    @Test
    void fulfillSalesOrderIssuesStock() {
        Product product = new Product();
        product.setSku("SKU-2");
        product.setName("Gadget");
        product.setUnitPrice(new BigDecimal("50.00"));
        product = supplyChainService.saveProduct(product);

        Warehouse warehouse = new Warehouse();
        warehouse.setCode("SEC");
        warehouse.setName("Secondary");
        warehouse = supplyChainService.saveWarehouse(warehouse);

        supplyChainService.adjustStock(product, warehouse, new BigDecimal("20"), StockMovementType.RECEIPT, "Initial", null, null, null);

        SalesOrder order = new SalesOrder();
        order.setOrderNumber("SO-2000");
        order.setCustomerName("Client");

        SalesOrderLine line = new SalesOrderLine();
        line.setProduct(product);
        line.setQuantity(new BigDecimal("3"));
        line.setUnitPrice(new BigDecimal("50.00"));
        order.setLines(new java.util.ArrayList<>(List.of(line)));

        order = supplyChainService.createSalesOrder(order);
        supplyChainService.fulfillSalesOrder(order.getId(), warehouse);

        StockLevel updated = stockLevelRepository.findByProductAndWarehouse(product, warehouse).orElseThrow();
        assertThat(updated.getQuantity()).isEqualByComparingTo(new BigDecimal("17"));
    }

    @Test
    void productionOrderStatusUpdatesTrackDates() {
        Product product = new Product();
        product.setSku("SKU-3");
        product.setName("Assembly");
        product = supplyChainService.saveProduct(product);

        ProductionOrder order = new ProductionOrder();
        order.setOrderNumber("PRO-1");
        order.setProduct(product);
        order.setQuantity(new BigDecimal("8"));

        order = supplyChainService.saveProductionOrder(order);
        ProductionOrder inProgress = supplyChainService.updateProductionOrderStatus(order.getId(), ProductionOrderStatus.IN_PROGRESS, null, null);

        assertThat(inProgress.getStatus()).isEqualTo(ProductionOrderStatus.IN_PROGRESS);
        assertThat(inProgress.getStartDate()).isNotNull();

        ProductionOrder completed = supplyChainService.updateProductionOrderStatus(order.getId(), ProductionOrderStatus.COMPLETED, null, null);
        assertThat(completed.getStatus()).isEqualTo(ProductionOrderStatus.COMPLETED);
        assertThat(completed.getCompletionDate()).isNotNull();
    }

    @Test
    void serviceRequestStatusTransitionClosesTicket() {
        ServiceRequest request = new ServiceRequest();
        request.setCustomerName("ACME");
        request.setSubject("Maintenance");

        request = supplyChainService.logServiceRequest(request);
        ServiceRequest inProgress = supplyChainService.updateServiceRequestStatus(request.getId(), ServiceRequestStatus.IN_PROGRESS, null);

        assertThat(inProgress.getStatus()).isEqualTo(ServiceRequestStatus.IN_PROGRESS);
        assertThat(inProgress.getClosedDate()).isNull();

        ServiceRequest resolved = supplyChainService.updateServiceRequestStatus(request.getId(), ServiceRequestStatus.RESOLVED, null);
        assertThat(resolved.getStatus()).isEqualTo(ServiceRequestStatus.RESOLVED);
        assertThat(resolved.getClosedDate()).isNotNull();
    }

    @Test
    void autoReplenishCreatesDraftPurchaseOrder() {
        Product product = new Product();
        product.setSku("SKU-AUTO-PLAN");
        product.setName("Predictive Sensor");
        product.setUnitCost(new BigDecimal("75.00"));
        product = supplyChainService.saveProduct(product);

        AutoReplenishRequest request = new AutoReplenishRequest();
        request.setProductIds(List.of(product.getId()));
        request.setPlanningHorizonDays(21);
        request.setSafetyDays(5);
        request.setServiceLevelTarget(0.9);

        AutoReplenishResponse response = supplyChainService.autoReplenish(request);

        assertThat(response.getPlans()).isNotEmpty();
        AutoReplenishPlanDTO plan = response.getPlans().get(0);
        assertThat(plan.getPurchaseOrder().getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
        assertThat(plan.getItems()).isNotEmpty();
        assertThat(plan.getPurchaseOrder().getLines()).isNotEmpty();
        assertThat(response.getTotalBudget()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void planWavePickingAggregatesOrdersAcrossZones() {
        Product bot = new Product();
        bot.setSku("SKU-AR-01");
        bot.setName("Chrono Bot");
        bot = supplyChainService.saveProduct(bot);

        Product glove = new Product();
        glove.setSku("SKU-AR-GLV");
        glove.setName("Picking Glove");
        glove = supplyChainService.saveProduct(glove);

        SalesOrder alpha = new SalesOrder();
        alpha.setOrderNumber("SO-9001");
        alpha.setCustomerName("Alpha");
        alpha.setStatus(SalesOrderStatus.CONFIRMED);
        SalesOrderLine alphaLine = new SalesOrderLine();
        alphaLine.setProduct(bot);
        alphaLine.setQuantity(new BigDecimal("3"));
        alphaLine.setUnitPrice(new BigDecimal("100"));
        alpha.setLines(new java.util.ArrayList<>(List.of(alphaLine)));
        alpha = supplyChainService.createSalesOrder(alpha);

        SalesOrder beta = new SalesOrder();
        beta.setOrderNumber("SO-9002");
        beta.setCustomerName("Beta");
        beta.setStatus(SalesOrderStatus.CONFIRMED);
        SalesOrderLine betaLine = new SalesOrderLine();
        betaLine.setProduct(glove);
        betaLine.setQuantity(new BigDecimal("5"));
        betaLine.setUnitPrice(new BigDecimal("25"));
        beta.setLines(new java.util.ArrayList<>(List.of(betaLine)));
        beta = supplyChainService.createSalesOrder(beta);

        PlanWavePickRequest request = new PlanWavePickRequest();
        request.setSalesOrderIds(List.of(alpha.getId(), beta.getId()));
        request.setMaxOrdersPerWave(4);

        WavePickResponse response = supplyChainService.planWavePicking(request);

        assertThat(response.getTotalOrders()).isEqualTo(2);
        assertThat(response.getTotalUnits()).isEqualTo(8);
        assertThat(response.getWaves()).isNotEmpty();
        WavePickWaveDTO firstWave = response.getWaves().get(0);
        assertThat(firstWave.getOrders()).isNotEmpty();
        assertThat(firstWave.getStops()).isNotEmpty();
        assertThat(firstWave.getStops().get(0).getQuantity()).isPositive();
    }
}
