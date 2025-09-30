package com.chrono.chrono.controller.inventory;

import com.chrono.chrono.dto.inventory.*;
import com.chrono.chrono.entities.inventory.Product;
import com.chrono.chrono.entities.inventory.PurchaseOrder;
import com.chrono.chrono.entities.inventory.PurchaseOrderLine;
import com.chrono.chrono.entities.inventory.SalesOrder;
import com.chrono.chrono.entities.inventory.SalesOrderLine;
import com.chrono.chrono.entities.inventory.StockMovement;
import com.chrono.chrono.entities.inventory.Warehouse;
import com.chrono.chrono.repositories.inventory.ProductRepository;
import com.chrono.chrono.repositories.inventory.WarehouseRepository;
import com.chrono.chrono.services.inventory.SupplyChainService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/supply-chain")
public class SupplyChainController {

    private final SupplyChainService supplyChainService;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;

    public SupplyChainController(SupplyChainService supplyChainService,
                                 ProductRepository productRepository,
                                 WarehouseRepository warehouseRepository) {
        this.supplyChainService = supplyChainService;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
    }

    @GetMapping("/products")
    public ResponseEntity<Page<ProductDTO>> listProducts(@RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<ProductDTO> products = productRepository.findAll(pageable).map(ProductDTO::from);
        return ResponseEntity.ok(products);
    }

    @PostMapping("/products")
    public ResponseEntity<ProductDTO> createProduct(@RequestBody CreateProductRequest request) {
        Product saved = supplyChainService.saveProduct(request.toEntity());
        return ResponseEntity.created(URI.create("/api/supply-chain/products/" + saved.getId()))
                .body(ProductDTO.from(saved));
    }

    @GetMapping("/warehouses")
    public ResponseEntity<List<WarehouseDTO>> listWarehouses() {
        List<WarehouseDTO> warehouses = warehouseRepository.findAll().stream()
                .map(WarehouseDTO::from)
                .toList();
        return ResponseEntity.ok(warehouses);
    }

    @PostMapping("/warehouses")
    public ResponseEntity<WarehouseDTO> createWarehouse(@RequestBody CreateWarehouseRequest request) {
        Warehouse saved = supplyChainService.saveWarehouse(request.toEntity());
        return ResponseEntity.created(URI.create("/api/supply-chain/warehouses/" + saved.getId()))
                .body(WarehouseDTO.from(saved));
    }

    @GetMapping("/stock")
    public ResponseEntity<Page<StockLevelDTO>> listStock(@RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<StockLevelDTO> levels = supplyChainService.listStockLevels(pageable)
                .map(StockLevelDTO::from);
        return ResponseEntity.ok(levels);
    }

    @PostMapping("/stock/adjust")
    public ResponseEntity<StockMovementDTO> adjustStock(@RequestBody StockAdjustmentRequest request) {
        Product product = productRepository.findById(request.getProductId()).orElseThrow();
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId()).orElseThrow();
        StockMovement movement = supplyChainService.adjustStock(product, warehouse,
                request.getQuantityChange(), request.getType(), request.getReference());
        return ResponseEntity.created(URI.create("/api/supply-chain/stock-movements/" + movement.getId()))
                .body(StockMovementDTO.from(movement));
    }

    @PostMapping("/purchase-orders")
    public ResponseEntity<PurchaseOrderDTO> createPurchaseOrder(@RequestBody CreatePurchaseOrderRequest request) {
        List<PurchaseOrderLine> lines = request.getLines() == null ? List.of() : request.getLines().stream()
                .map(line -> {
                    PurchaseOrderLine pol = new PurchaseOrderLine();
                    pol.setProduct(productRepository.findById(line.getProductId()).orElseThrow());
                    pol.setQuantity(line.getQuantity());
                    pol.setUnitCost(line.getUnitCost());
                    return pol;
                }).toList();
        PurchaseOrder order = request.toEntity(lines);
        PurchaseOrder saved = supplyChainService.createPurchaseOrder(order);
        return ResponseEntity.created(URI.create("/api/supply-chain/purchase-orders/" + saved.getId()))
                .body(PurchaseOrderDTO.from(saved));
    }

    @PostMapping("/purchase-orders/{id}/receive")
    public ResponseEntity<PurchaseOrderDTO> receivePurchaseOrder(@PathVariable Long id,
                                                                 @RequestBody WarehouseReferenceRequest ref) {
        Warehouse warehouse = warehouseRepository.findById(ref.getWarehouseId()).orElseThrow();
        return ResponseEntity.ok(PurchaseOrderDTO.from(supplyChainService.receivePurchaseOrder(id, warehouse)));
    }

    @PostMapping("/sales-orders")
    public ResponseEntity<SalesOrderDTO> createSalesOrder(@RequestBody CreateSalesOrderRequest request) {
        List<SalesOrderLine> lines = request.getLines() == null ? List.of() : request.getLines().stream()
                .map(line -> {
                    SalesOrderLine sol = new SalesOrderLine();
                    sol.setProduct(productRepository.findById(line.getProductId()).orElseThrow());
                    sol.setQuantity(line.getQuantity());
                    sol.setUnitPrice(line.getUnitPrice());
                    return sol;
                }).toList();
        SalesOrder order = request.toEntity(lines);
        SalesOrder saved = supplyChainService.createSalesOrder(order);
        return ResponseEntity.created(URI.create("/api/supply-chain/sales-orders/" + saved.getId()))
                .body(SalesOrderDTO.from(saved));
    }

    @PostMapping("/sales-orders/{id}/fulfill")
    public ResponseEntity<SalesOrderDTO> fulfillSalesOrder(@PathVariable Long id,
                                                           @RequestBody WarehouseReferenceRequest ref) {
        Warehouse warehouse = warehouseRepository.findById(ref.getWarehouseId()).orElseThrow();
        return ResponseEntity.ok(SalesOrderDTO.from(supplyChainService.fulfillSalesOrder(id, warehouse)));
    }
}
