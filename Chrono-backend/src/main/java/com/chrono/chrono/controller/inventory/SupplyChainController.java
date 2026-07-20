package com.chrono.chrono.controller.inventory;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import com.chrono.chrono.dto.inventory.*;
import com.chrono.chrono.entities.inventory.Product;
import com.chrono.chrono.entities.inventory.ProductionOrder;
import com.chrono.chrono.entities.inventory.PurchaseOrder;
import com.chrono.chrono.entities.inventory.PurchaseOrderLine;
import com.chrono.chrono.entities.inventory.SalesOrder;
import com.chrono.chrono.entities.inventory.SalesOrderLine;
import com.chrono.chrono.entities.inventory.ServiceRequest;
import com.chrono.chrono.entities.inventory.CycleCount;
import com.chrono.chrono.entities.inventory.StockMovement;
import com.chrono.chrono.entities.inventory.Warehouse;
import com.chrono.chrono.repositories.inventory.ProductRepository;
import com.chrono.chrono.repositories.inventory.WarehouseRepository;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.services.UserService;
import com.chrono.chrono.services.inventory.SupplyChainService;
import com.chrono.chrono.utils.RegistrationFeatures;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/supply-chain")
public class SupplyChainController {

    private final SupplyChainService supplyChainService;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserService userService;
    private final UserPermissionService userPermissionService;

    public SupplyChainController(SupplyChainService supplyChainService,
                                 ProductRepository productRepository,
                                 WarehouseRepository warehouseRepository,
                                 UserService userService,
                                 UserPermissionService userPermissionService) {
        this.supplyChainService = supplyChainService;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.userService = userService;
        this.userPermissionService = userPermissionService;
    }

    private User requireUser(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return userService.getUserByUsername(principal.getName());
    }

    private boolean isSuperAdmin(User user) {
        return user.getRoles().stream().map(Role::getRoleName).anyMatch("ROLE_SUPERADMIN"::equals);
    }

    private Company requireAccessibleCompany(Principal principal, String requiredAccessLevel) {
        User user = requireUser(principal);
        Company company = user.getCompany();
        if (company == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No company assigned");
        }
        if (!isSuperAdmin(user)) {
            boolean enabled = RegistrationFeatures.sanitizeOptionalFeatures(company.getEnabledFeatures())
                    .contains("supplyChain");
            if (!enabled) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Supply Chain feature disabled");
            }
            userPermissionService.assertPageAccess(
                    user,
                    UserPermissionService.PAGE_SUPPLY_CHAIN,
                    requiredAccessLevel,
                    "Missing permission for Supply Chain."
            );
        }
        return company;
    }

    @GetMapping("/products")
    public ResponseEntity<Page<ProductDTO>> listProducts(@RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size,
                                                         Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_VIEW);
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<ProductDTO> products = productRepository.findAllByCompany_Id(company.getId(), pageable).map(ProductDTO::from);
        return ResponseEntity.ok(products);
    }

    @PostMapping("/products")
    public ResponseEntity<ProductDTO> createProduct(@Valid @RequestBody CreateProductRequest request, Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_MANAGE);
        Product saved = supplyChainService.saveProduct(request.toEntity(), company);
        return ResponseEntity.created(URI.create("/api/supply-chain/products/" + saved.getId()))
                .body(ProductDTO.from(saved));
    }

    @GetMapping("/warehouses")
    public ResponseEntity<List<WarehouseDTO>> listWarehouses(Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_VIEW);
        List<WarehouseDTO> warehouses = warehouseRepository.findAllByCompany_Id(company.getId()).stream()
                .map(WarehouseDTO::from)
                .toList();
        return ResponseEntity.ok(warehouses);
    }

    @PostMapping("/warehouses")
    public ResponseEntity<WarehouseDTO> createWarehouse(@Valid @RequestBody CreateWarehouseRequest request, Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_MANAGE);
        Warehouse saved = supplyChainService.saveWarehouse(request.toEntity(), company);
        return ResponseEntity.created(URI.create("/api/supply-chain/warehouses/" + saved.getId()))
                .body(WarehouseDTO.from(saved));
    }

    @GetMapping("/warehouses/{warehouseId}/bins")
    public ResponseEntity<List<WarehouseBinDTO>> listWarehouseBins(@PathVariable Long warehouseId,
                                                                   Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_VIEW);
        return ResponseEntity.ok(supplyChainService.listWarehouseBins(company.getId(), warehouseId));
    }

    @PostMapping("/warehouses/{warehouseId}/bins")
    public ResponseEntity<WarehouseBinDTO> createWarehouseBin(@PathVariable Long warehouseId,
                                                              @Valid @RequestBody CreateWarehouseBinRequest request,
                                                              Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_MANAGE);
        WarehouseBinDTO saved = supplyChainService.createWarehouseBin(company.getId(), warehouseId, request);
        return ResponseEntity.created(URI.create("/api/supply-chain/warehouses/" + warehouseId + "/bins/" + saved.id()))
                .body(saved);
    }

    @GetMapping("/stock")
    public ResponseEntity<Page<StockLevelDTO>> listStock(@RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size,
                                                         Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_VIEW);
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<StockLevelDTO> levels = supplyChainService.listStockLevels(company.getId(), pageable)
                .map(StockLevelDTO::from);
        return ResponseEntity.ok(levels);
    }

    @PostMapping("/stock/adjust")
    public ResponseEntity<StockMovementDTO> adjustStock(@Valid @RequestBody StockAdjustmentRequest request, Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_MANAGE);
        StockMovement movement = supplyChainService.adjustStock(company.getId(), request, principal.getName());
        return ResponseEntity.created(URI.create("/api/supply-chain/stock-movements/" + movement.getId()))
                .body(StockMovementDTO.from(movement));
    }

    @GetMapping("/stock-movements")
    public ResponseEntity<Page<StockMovementDTO>> listStockMovements(@RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "50") int size,
                                                                      Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_VIEW);
        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        Page<StockMovementDTO> movements = supplyChainService.listStockMovements(company.getId(), pageable)
                .map(StockMovementDTO::from);
        return ResponseEntity.ok(movements);
    }

    @GetMapping("/cycle-counts")
    public ResponseEntity<Page<CycleCountDTO>> listCycleCounts(@RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "50") int size,
                                                               Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_VIEW);
        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        Page<CycleCountDTO> counts = supplyChainService.listCycleCounts(company.getId(), pageable).map(CycleCountDTO::from);
        return ResponseEntity.ok(counts);
    }

    @PostMapping("/cycle-counts")
    public ResponseEntity<CycleCountDTO> createCycleCount(@Valid @RequestBody CreateCycleCountRequest request, Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_MANAGE);
        Product product = productRepository.findByIdAndCompany_Id(request.getProductId(), company.getId()).orElseThrow();
        Warehouse warehouse = warehouseRepository.findByIdAndCompany_Id(request.getWarehouseId(), company.getId()).orElseThrow();
        CycleCount saved = supplyChainService.createCycleCount(company.getId(), product, warehouse, principal != null ? principal.getName() : "system");
        return ResponseEntity.created(URI.create("/api/supply-chain/cycle-counts/" + saved.getId()))
                .body(CycleCountDTO.from(saved));
    }

    @PostMapping("/cycle-counts/{id}/submit")
    public ResponseEntity<CycleCountDTO> submitCycleCount(@PathVariable Long id,
                                                          @Valid @RequestBody SubmitCycleCountRequest request,
                                                          Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_MANAGE);
        CycleCount updated = supplyChainService.submitCycleCount(company.getId(), id, request.getCountedQuantity(),
                principal != null ? principal.getName() : "system");
        return ResponseEntity.ok(CycleCountDTO.from(updated));
    }

    @PostMapping("/cycle-counts/{id}/approve")
    public ResponseEntity<CycleCountDTO> approveCycleCount(@PathVariable Long id, Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_MANAGE);
        CycleCount updated = supplyChainService.approveCycleCount(company.getId(), id, principal != null ? principal.getName() : "system");
        return ResponseEntity.ok(CycleCountDTO.from(updated));
    }

    @PostMapping("/receiving/preview")
    public ResponseEntity<ReceivingPreviewResponse> previewReceiving(@Valid @RequestBody ReceivingPreviewRequest request,
                                                                    Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_VIEW);
        return ResponseEntity.ok(supplyChainService.previewReceiving(company.getId(), request));
    }

    @PostMapping(value = "/receiving/document-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReceivingPreviewResponse> previewReceivingDocument(@RequestParam("file") MultipartFile file,
                                                                             Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_VIEW);
        ReceivingPreviewRequest request = new ReceivingPreviewRequest();
        request.setFileName(file.getOriginalFilename());
        request.setDocumentText(extractDocumentText(file));
        return ResponseEntity.ok(supplyChainService.previewReceiving(company.getId(), request));
    }

    @PostMapping("/receiving/apply")
    public ResponseEntity<ReceivingApplyResponse> applyReceiving(@Valid @RequestBody ReceivingApplyRequest request,
                                                                 Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_MANAGE);
        Warehouse warehouse = warehouseRepository.findByIdAndCompany_Id(request.getWarehouseId(), company.getId()).orElseThrow();
        return ResponseEntity.ok(supplyChainService.applyReceiving(company.getId(), request, warehouse));
    }


    @GetMapping("/purchase-orders")
    public ResponseEntity<Page<PurchaseOrderDTO>> listPurchaseOrders(@RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size,
                                                                     Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_VIEW);
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<PurchaseOrderDTO> orders = supplyChainService.listPurchaseOrders(company.getId(), pageable)
                .map(PurchaseOrderDTO::from);
        return ResponseEntity.ok(orders);
    }

    @PostMapping("/purchase-orders")
    public ResponseEntity<PurchaseOrderDTO> createPurchaseOrder(@Valid @RequestBody CreatePurchaseOrderRequest request,
                                                                Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_MANAGE);
        List<PurchaseOrderLine> lines = request.getLines() == null ? List.of() : request.getLines().stream()
                .map(line -> {
                    PurchaseOrderLine pol = new PurchaseOrderLine();
                    pol.setProduct(productRepository.findByIdAndCompany_Id(line.getProductId(), company.getId()).orElseThrow());
                    pol.setQuantity(line.getQuantity());
                    pol.setUnitCost(line.getUnitCost());
                    return pol;
                }).toList();
        PurchaseOrder order = request.toEntity(lines);
        PurchaseOrder saved = supplyChainService.createPurchaseOrder(order, company);
        return ResponseEntity.created(URI.create("/api/supply-chain/purchase-orders/" + saved.getId()))
                .body(PurchaseOrderDTO.from(saved));
    }

    @PostMapping("/purchase-orders/{id}/receive")
    public ResponseEntity<PurchaseOrderDTO> receivePurchaseOrder(@PathVariable Long id,
                                                                 @Valid @RequestBody WarehouseReferenceRequest ref,
                                                                 Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_MANAGE);
        Warehouse warehouse = warehouseRepository.findByIdAndCompany_Id(ref.getWarehouseId(), company.getId()).orElseThrow();
        return ResponseEntity.ok(PurchaseOrderDTO.from(supplyChainService.receivePurchaseOrder(company.getId(), id, warehouse)));
    }

    @PostMapping("/procurement/auto-replenish")
    public ResponseEntity<AutoReplenishResponse> autoReplenish(@RequestBody AutoReplenishRequest request,
                                                               Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_MANAGE);
        AutoReplenishResponse response = supplyChainService.autoReplenish(company.getId(), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/procurement/replenishment-preview")
    public ResponseEntity<ReplenishmentPreviewResponse> previewReplenishment(@RequestBody AutoReplenishRequest request,
                                                                             Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_VIEW);
        ReplenishmentPreviewResponse response = supplyChainService.previewReplenishment(company.getId(), request);
        return ResponseEntity.ok(response);
    }


    @GetMapping("/sales-orders")
    public ResponseEntity<Page<SalesOrderDTO>> listSalesOrders(@RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size,
                                                               Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_VIEW);
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<SalesOrderDTO> orders = supplyChainService.listSalesOrders(company.getId(), pageable)
                .map(SalesOrderDTO::from);
        return ResponseEntity.ok(orders);
    }

    @PostMapping("/sales-orders")
    public ResponseEntity<SalesOrderDTO> createSalesOrder(@Valid @RequestBody CreateSalesOrderRequest request,
                                                          Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_MANAGE);
        List<SalesOrderLine> lines = request.getLines() == null ? List.of() : request.getLines().stream()
                .map(line -> {
                    SalesOrderLine sol = new SalesOrderLine();
                    sol.setProduct(productRepository.findByIdAndCompany_Id(line.getProductId(), company.getId()).orElseThrow());
                    sol.setQuantity(line.getQuantity());
                    sol.setUnitPrice(line.getUnitPrice());
                    return sol;
                }).toList();
        SalesOrder order = request.toEntity(lines);
        SalesOrder saved = supplyChainService.createSalesOrder(order, company);
        return ResponseEntity.created(URI.create("/api/supply-chain/sales-orders/" + saved.getId()))
                .body(SalesOrderDTO.from(saved));
    }

    @PostMapping("/sales-orders/{id}/fulfill")
    public ResponseEntity<SalesOrderDTO> fulfillSalesOrder(@PathVariable Long id,
                                                           @Valid @RequestBody WarehouseReferenceRequest ref,
                                                           Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_MANAGE);
        Warehouse warehouse = warehouseRepository.findByIdAndCompany_Id(ref.getWarehouseId(), company.getId()).orElseThrow();
        return ResponseEntity.ok(SalesOrderDTO.from(supplyChainService.fulfillSalesOrder(company.getId(), id, warehouse,
                principal.getName())));
    }

    @PostMapping("/sales-orders/{id}/reserve")
    public ResponseEntity<SalesOrderDTO> reserveSalesOrder(@PathVariable Long id,
                                                           @Valid @RequestBody WarehouseReferenceRequest ref,
                                                           Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_MANAGE);
        Warehouse warehouse = warehouseRepository.findByIdAndCompany_Id(ref.getWarehouseId(), company.getId()).orElseThrow();
        return ResponseEntity.ok(SalesOrderDTO.from(supplyChainService.reserveSalesOrder(company.getId(), id, warehouse,
                principal.getName())));
    }

    @PostMapping("/sales-orders/pick-waves")
    public ResponseEntity<WavePickResponse> planWavePicking(@RequestBody PlanWavePickRequest request,
                                                            Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_VIEW);
        return ResponseEntity.ok(supplyChainService.planWavePicking(company.getId(), request));
    }

    @GetMapping("/production-orders")
    public ResponseEntity<Page<ProductionOrderDTO>> listProductionOrders(@RequestParam(defaultValue = "0") int page,
                                                                         @RequestParam(defaultValue = "20") int size,
                                                                         Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_VIEW);
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<ProductionOrderDTO> orders = supplyChainService.listProductionOrders(company.getId(), pageable)
                .map(ProductionOrderDTO::from);
        return ResponseEntity.ok(orders);
    }

    @PostMapping("/production-orders")
    public ResponseEntity<ProductionOrderDTO> createProductionOrder(@RequestBody CreateProductionOrderRequest request,
                                                                    Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_MANAGE);
        Product product = productRepository.findByIdAndCompany_Id(request.getProductId(), company.getId()).orElseThrow();
        ProductionOrder saved = supplyChainService.saveProductionOrder(request.toEntity(product), company);
        return ResponseEntity.created(URI.create("/api/supply-chain/production-orders/" + saved.getId()))
                .body(ProductionOrderDTO.from(saved));
    }

    @PostMapping("/production-orders/{id}/status")
    public ResponseEntity<ProductionOrderDTO> updateProductionOrderStatus(@PathVariable Long id,
                                                                          @RequestBody UpdateProductionOrderStatusRequest request,
                                                                          Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_MANAGE);
        ProductionOrder updated = supplyChainService.updateProductionOrderStatus(company.getId(), id, request.getStatus(),
                request.getStartDate(), request.getCompletionDate());
        return ResponseEntity.ok(ProductionOrderDTO.from(updated));
    }

    @GetMapping("/service-requests")
    public ResponseEntity<Page<ServiceRequestDTO>> listServiceRequests(@RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "20") int size,
                                                                       Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_VIEW);
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<ServiceRequestDTO> requests = supplyChainService.listServiceRequests(company.getId(), pageable)
                .map(ServiceRequestDTO::from);
        return ResponseEntity.ok(requests);
    }

    @PostMapping("/service-requests")
    public ResponseEntity<ServiceRequestDTO> createServiceRequest(@RequestBody CreateServiceRequestRequest request,
                                                                  Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_MANAGE);
        ServiceRequest saved = supplyChainService.logServiceRequest(request.toEntity(), company);
        return ResponseEntity.created(URI.create("/api/supply-chain/service-requests/" + saved.getId()))
                .body(ServiceRequestDTO.from(saved));
    }

    @PostMapping("/service-requests/{id}/status")
    public ResponseEntity<ServiceRequestDTO> updateServiceRequestStatus(@PathVariable Long id,
                                                                        @RequestBody UpdateServiceRequestStatusRequest request,
                                                                        Principal principal) {
        Company company = requireAccessibleCompany(principal, UserPermissionService.ACCESS_MANAGE);
        ServiceRequest updated = supplyChainService.updateServiceRequestStatus(company.getId(), id, request.getStatus(), request.getClosedDate());
        return ResponseEntity.ok(ServiceRequestDTO.from(updated));
    }

    private String extractDocumentText(MultipartFile file) {
        try {
            String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
            String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();

            if (contentType.contains("pdf") || fileName.endsWith(".pdf")) {
                PdfReader reader = new PdfReader(file.getBytes());
                try {
                    StringBuilder builder = new StringBuilder();
                    for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                        builder.append(PdfTextExtractor.getTextFromPage(reader, page)).append('\n');
                    }
                    return builder.toString();
                } finally {
                    reader.close();
                }
            }

            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Document could not be read for receiving preview", ex);
        }
    }
}
