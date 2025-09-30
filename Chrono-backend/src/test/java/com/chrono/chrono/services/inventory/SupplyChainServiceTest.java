package com.chrono.chrono.services.inventory;

import com.chrono.chrono.entities.accounting.InvoiceStatus;
import com.chrono.chrono.entities.accounting.VendorInvoice;
import com.chrono.chrono.entities.inventory.*;
import com.chrono.chrono.repositories.accounting.VendorInvoiceRepository;
import com.chrono.chrono.repositories.inventory.StockLevelRepository;
import com.chrono.chrono.services.accounting.AccountingService;
import com.chrono.chrono.services.accounting.AccountsPayableService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({AccountingService.class, AccountsPayableService.class, SupplyChainService.class})
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

        supplyChainService.adjustStock(product, warehouse, new BigDecimal("10"), StockMovementType.RECEIPT, "Initial");
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

        supplyChainService.adjustStock(product, warehouse, new BigDecimal("20"), StockMovementType.RECEIPT, "Initial");

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
}
