package com.chrono.chrono.services.inventory;

import com.chrono.chrono.dto.inventory.AutoReplenishItemDTO;
import com.chrono.chrono.dto.inventory.AutoReplenishPlanDTO;
import com.chrono.chrono.dto.inventory.AutoReplenishRequest;
import com.chrono.chrono.dto.inventory.AutoReplenishResponse;
import com.chrono.chrono.dto.inventory.AutoReplenishSupplierDTO;
import com.chrono.chrono.dto.inventory.PlanWavePickRequest;
import com.chrono.chrono.dto.inventory.PurchaseOrderDTO;
import com.chrono.chrono.dto.inventory.ReceivingApplyRequest;
import com.chrono.chrono.dto.inventory.ReceivingApplyResponse;
import com.chrono.chrono.dto.inventory.ReceivingPreviewRequest;
import com.chrono.chrono.dto.inventory.ReceivingPreviewResponse;
import com.chrono.chrono.dto.inventory.ReplenishmentPreviewResponse;
import com.chrono.chrono.dto.inventory.WavePickOrderSummaryDTO;
import com.chrono.chrono.dto.inventory.WavePickResponse;
import com.chrono.chrono.dto.inventory.WavePickStopDTO;
import com.chrono.chrono.dto.inventory.WavePickWaveDTO;
import com.chrono.chrono.entities.inventory.*;
import com.chrono.chrono.entities.accounting.InvoiceStatus;
import com.chrono.chrono.entities.accounting.VendorInvoice;
import com.chrono.chrono.repositories.inventory.*;
import com.chrono.chrono.services.accounting.AccountsPayableService;
import com.chrono.chrono.warehouse.dto.PickRouteRequest;
import com.chrono.chrono.warehouse.dto.PickRouteResponse;
import com.chrono.chrono.warehouse.dto.PredictiveInventoryResponse;
import com.chrono.chrono.warehouse.dto.PredictiveReplenishmentResponse;
import com.chrono.chrono.warehouse.dto.SmartSourcingRequest;
import com.chrono.chrono.warehouse.dto.SmartSourcingResponse;
import com.chrono.chrono.warehouse.model.InventoryItem;
import com.chrono.chrono.warehouse.model.WarehouseLocation;
import com.chrono.chrono.warehouse.service.WarehouseIntelligenceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SupplyChainService {
    private static final Pattern LABELED_REFERENCE_PATTERN = Pattern.compile(
            "(?i)(?:BESTELL(?:NUMMER|NR)|ORDER(?:\\s+NUMBER|\\s+NO\\.?|\\s+NR)?|PURCHASE\\s+ORDER|ASN|LIEFERSCHEIN(?:NUMMER|NR)?|DELIVERY\\s+NOTE|REFERENCE|REFERENZ)\\s*[:#-]?\\s*([A-Z0-9][A-Z0-9\\-/]{2,})"
    );
    private static final Pattern GENERIC_REFERENCE_PATTERN = Pattern.compile("\\b[A-Z]{1,6}-\\d{2,}(?:-\\d+)?\\b");
    private static final Pattern LABELED_VENDOR_PATTERN = Pattern.compile("(?i)(?:LIEFERANT|VENDOR|SUPPLIER)\\s*[:#-]?\\s*([^\\r\\n]+)");
    private static final Pattern LABELED_QUANTITY_PATTERN = Pattern.compile("(?i)(?:MENGE|QTY|QUANTITY|ANZAHL)\\s*[:x-]?\\s*(\\d+(?:[.,]\\d+)?)");
    private static final Pattern GENERIC_NUMBER_PATTERN = Pattern.compile("(?<![A-Z0-9])(\\d{1,5}(?:[.,]\\d{1,3})?)(?![A-Z0-9])");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\b\\d{4}-\\d{2}-\\d{2}\\b|\\b\\d{2}[./-]\\d{2}[./-]\\d{4}\\b)");

    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockLevelRepository stockLevelRepository;
    private final StockMovementRepository stockMovementRepository;
    private final CycleCountRepository cycleCountRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final ProductionOrderRepository productionOrderRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final AccountsPayableService accountsPayableService;
    private final WarehouseIntelligenceService warehouseIntelligenceService;

    public SupplyChainService(ProductRepository productRepository,
                              WarehouseRepository warehouseRepository,
                              StockLevelRepository stockLevelRepository,
                              StockMovementRepository stockMovementRepository,
                              CycleCountRepository cycleCountRepository,
                              PurchaseOrderRepository purchaseOrderRepository,
                              SalesOrderRepository salesOrderRepository,
                              ProductionOrderRepository productionOrderRepository,
                              ServiceRequestRepository serviceRequestRepository,
                              AccountsPayableService accountsPayableService,
                              WarehouseIntelligenceService warehouseIntelligenceService) {
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.stockLevelRepository = stockLevelRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.cycleCountRepository = cycleCountRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.productionOrderRepository = productionOrderRepository;
        this.serviceRequestRepository = serviceRequestRepository;
        this.accountsPayableService = accountsPayableService;
        this.warehouseIntelligenceService = warehouseIntelligenceService;
    }

    @Transactional
    public Product saveProduct(Product product) {
        Product saved = productRepository.save(product);
        warehouseIntelligenceService.registerProduct(saved);
        return saved;
    }

    @Transactional
    public Warehouse saveWarehouse(Warehouse warehouse) {
        Warehouse saved = warehouseRepository.save(warehouse);
        warehouseIntelligenceService.registerWarehouse(saved);
        return saved;
    }

    @Transactional
    public StockMovement adjustStock(Product product,
                                     Warehouse warehouse,
                                     BigDecimal quantity,
                                     StockMovementType type,
                                     String reference,
                                     String lotNumber,
                                     String serialNumber,
                                     LocalDate expirationDate) {
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
        movement.setLotNumber(lotNumber);
        movement.setSerialNumber(serialNumber);
        movement.setExpirationDate(expirationDate);
        StockMovement savedMovement = stockMovementRepository.save(movement);
        warehouseIntelligenceService.applyStockMovement(product, warehouse, quantity, level.getQuantity());
        return savedMovement;
    }

    @Transactional(readOnly = true)
    public Page<StockMovement> listStockMovements(Pageable pageable) {
        return stockMovementRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public ReceivingPreviewResponse previewReceiving(ReceivingPreviewRequest request) {
        String documentText = safeText(request.getDocumentText());
        List<PurchaseOrder> openOrders = purchaseOrderRepository.findAll().stream()
                .filter(this::isOpenPurchaseOrder)
                .toList();
        LinkedHashSet<String> detectedCodes = collectDetectedCodes(request);
        PurchaseOrder matchedOrder = findMatchingPurchaseOrder(detectedCodes, documentText, openOrders);

        String reference = matchedOrder != null
                ? matchedOrder.getOrderNumber()
                : detectedCodes.stream().findFirst().orElseGet(() -> fallbackReferenceFromFile(request.getFileName()));
        String vendorName = matchedOrder != null
                ? matchedOrder.getVendorName()
                : extractVendorName(documentText, openOrders);
        String documentDate = extractDate(documentText);

        List<String> warnings = new ArrayList<>();
        List<ReceivingPreviewResponse.ReceivingPreviewItem> items = matchedOrder != null
                ? buildPreviewItemsForOrder(matchedOrder)
                : detectPreviewItems(documentText, detectedCodes);

        boolean ready = matchedOrder != null || !items.isEmpty();
        String mode = matchedOrder != null ? "PURCHASE_ORDER" : (!items.isEmpty() ? "DIRECT_RECEIPT" : "NONE");
        String matchType = matchedOrder != null ? "PURCHASE_ORDER_MATCH" : (!items.isEmpty() ? "SKU_MATCH" : "NO_MATCH");
        String message;

        if (matchedOrder != null) {
            message = "Open purchase order matched and ready for receiving review.";
        } else if (!items.isEmpty()) {
            message = "Products detected from scan or document. Review quantities before posting.";
            warnings.add("This booking is posted as direct goods receipt and does not close a purchase order automatically.");
        } else {
            message = "No reliable match found yet. Scan an order number, ASN, or upload a clearer delivery note.";
            warnings.add("No purchase order or SKU could be matched from the current scan/document.");
        }

        if ((!safeText(request.getFileName()).isBlank() || !safeText(request.getDocumentText()).isBlank()) && documentText.isBlank()) {
            warnings.add("No readable document text was available. Matching is based on detected codes and file name only.");
        }

        return new ReceivingPreviewResponse(
                ready,
                mode,
                matchType,
                message,
                reference,
                vendorName,
                documentDate,
                matchedOrder != null ? matchedOrder.getId() : null,
                matchedOrder != null ? matchedOrder.getOrderNumber() : null,
                matchedOrder != null ? matchedOrder.getStatus().name() : null,
                matchedOrder != null,
                new ArrayList<>(detectedCodes),
                warnings,
                abbreviate(documentText, 560),
                items
        );
    }

    @Transactional
    public ReceivingApplyResponse applyReceiving(ReceivingApplyRequest request, Warehouse warehouse) {
        if (request.getWarehouseId() == null) {
            throw new IllegalArgumentException("Warehouse is required");
        }

        PurchaseOrder order = request.getPurchaseOrderId() == null
                ? null
                : purchaseOrderRepository.findById(request.getPurchaseOrderId()).orElseThrow();

        String reference = safeText(request.getReference());
        if (reference.isBlank() && order != null) {
            reference = order.getOrderNumber();
        }
        if (reference.isBlank()) {
            reference = "SCAN-RECEIPT";
        }

        if (order != null && request.isCompletePurchaseOrder() && matchesOrderExactly(order, request.getItems())) {
            PurchaseOrder received = receivePurchaseOrder(order.getId(), warehouse);
            BigDecimal totalQuantity = received.getLines() == null ? BigDecimal.ZERO : received.getLines().stream()
                    .map(PurchaseOrderLine::getQuantity)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int bookedItemCount = received.getLines() == null ? 0 : received.getLines().size();
            return new ReceivingApplyResponse(
                    "PURCHASE_ORDER",
                    "Purchase order fully received and stock posted.",
                    received.getOrderNumber(),
                    received.getOrderNumber(),
                    true,
                    bookedItemCount,
                    totalQuantity
            );
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("At least one receiving item is required");
        }

        BigDecimal totalQuantity = BigDecimal.ZERO;
        int bookedItemCount = 0;

        for (ReceivingApplyRequest.ReceivingApplyItem item : request.getItems()) {
            if (item == null || item.getProductId() == null || item.getQuantity() == null) {
                continue;
            }
            if (item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Product product = productRepository.findById(item.getProductId()).orElseThrow();
            adjustStock(product, warehouse, item.getQuantity(), StockMovementType.RECEIPT, reference, null, null, null);
            totalQuantity = totalQuantity.add(item.getQuantity());
            bookedItemCount++;
        }

        if (bookedItemCount == 0) {
            throw new IllegalArgumentException("No positive quantities were provided for receiving");
        }

        return new ReceivingApplyResponse(
                order != null ? "PURCHASE_ORDER_PARTIAL" : "DIRECT_RECEIPT",
                order != null
                        ? "Goods receipt posted. Purchase order remains open until it is fully confirmed."
                        : "Goods receipt posted from scan/document analysis.",
                reference,
                order != null ? order.getOrderNumber() : null,
                false,
                bookedItemCount,
                totalQuantity
        );
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
                        order.getOrderNumber(), null, null, null);
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
                        order.getOrderNumber(), null, null, null);
            }
        }
        order.setStatus(SalesOrderStatus.FULFILLED);
        order.setDueDate(LocalDate.now());
        return salesOrderRepository.save(order);
    }

    @Transactional
    public ProductionOrder saveProductionOrder(ProductionOrder productionOrder) {
        if (productionOrder.getStatus() == ProductionOrderStatus.IN_PROGRESS && productionOrder.getStartDate() == null) {
            productionOrder.setStartDate(LocalDate.now());
        }
        if (productionOrder.getStatus() == ProductionOrderStatus.COMPLETED && productionOrder.getCompletionDate() == null) {
            productionOrder.setCompletionDate(LocalDate.now());
        }
        return productionOrderRepository.save(productionOrder);
    }

    @Transactional
    public ProductionOrder updateProductionOrderStatus(Long productionOrderId,
                                                       ProductionOrderStatus status,
                                                       LocalDate startDate,
                                                       LocalDate completionDate) {
        ProductionOrder order = productionOrderRepository.findById(productionOrderId).orElseThrow();
        order.setStatus(status);
        if (status == ProductionOrderStatus.IN_PROGRESS) {
            if (startDate != null) {
                order.setStartDate(startDate);
            } else if (order.getStartDate() == null) {
                order.setStartDate(LocalDate.now());
            }
        } else if (startDate != null) {
            order.setStartDate(startDate);
        }
        if (status == ProductionOrderStatus.COMPLETED) {
            order.setCompletionDate(completionDate != null ? completionDate : LocalDate.now());
        } else if (completionDate != null) {
            order.setCompletionDate(completionDate);
        }
        return productionOrderRepository.save(order);
    }

    @Transactional
    public ServiceRequest logServiceRequest(ServiceRequest request) {
        if (request.getOpenedDate() == null) {
            request.setOpenedDate(LocalDate.now());
        }
        return serviceRequestRepository.save(request);
    }

    @Transactional
    public ServiceRequest updateServiceRequestStatus(Long serviceRequestId,
                                                     ServiceRequestStatus status,
                                                     LocalDate closedDate) {
        ServiceRequest request = serviceRequestRepository.findById(serviceRequestId).orElseThrow();
        request.setStatus(status);
        if (status == ServiceRequestStatus.OPEN) {
            request.setClosedDate(null);
        } else if (status == ServiceRequestStatus.RESOLVED || status == ServiceRequestStatus.CLOSED) {
            request.setClosedDate(closedDate != null ? closedDate : LocalDate.now());
        } else if (closedDate != null) {
            request.setClosedDate(closedDate);
        }
        return serviceRequestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public Page<StockLevel> listStockLevels(Pageable pageable) {
        return stockLevelRepository.findAll(pageable);
    }


    @Transactional(readOnly = true)
    public Page<PurchaseOrder> listPurchaseOrders(Pageable pageable) {
        return purchaseOrderRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<SalesOrder> listSalesOrders(Pageable pageable) {
        return salesOrderRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<ProductionOrder> listProductionOrders(Pageable pageable) {
        return productionOrderRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<ServiceRequest> listServiceRequests(Pageable pageable) {
        return serviceRequestRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<CycleCount> listCycleCounts(Pageable pageable) {
        return cycleCountRepository.findAll(pageable);
    }

    @Transactional
    public CycleCount createCycleCount(Product product, Warehouse warehouse, String requestedBy) {
        Optional<CycleCount> existing = cycleCountRepository.findFirstByProductAndWarehouseAndStatusInOrderByCreatedAtDesc(
                product,
                warehouse,
                List.of(CycleCountStatus.PLANNED, CycleCountStatus.APPROVAL_REQUIRED));
        if (existing.isPresent()) {
            return existing.get();
        }

        BigDecimal expectedQuantity = stockLevelRepository.findByProductAndWarehouse(product, warehouse)
                .map(StockLevel::getQuantity)
                .orElse(BigDecimal.ZERO);

        CycleCount cycleCount = new CycleCount();
        cycleCount.setPlanNumber(nextCycleCountPlanNumber());
        cycleCount.setProduct(product);
        cycleCount.setWarehouse(warehouse);
        cycleCount.setExpectedQuantity(expectedQuantity);
        cycleCount.setVariance(BigDecimal.ZERO);
        cycleCount.setStatus(CycleCountStatus.PLANNED);
        cycleCount.setRequestedBy(requestedBy);
        return cycleCountRepository.save(cycleCount);
    }

    @Transactional
    public CycleCount submitCycleCount(Long cycleCountId, BigDecimal countedQuantity, String countedBy) {
        if (countedQuantity == null) {
            throw new IllegalArgumentException("Counted quantity is required");
        }
        if (countedQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Counted quantity cannot be negative");
        }

        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId).orElseThrow();
        BigDecimal variance = countedQuantity.subtract(Optional.ofNullable(cycleCount.getExpectedQuantity()).orElse(BigDecimal.ZERO));

        cycleCount.setCountedQuantity(countedQuantity);
        cycleCount.setVariance(variance);
        cycleCount.setCountedBy(countedBy);
        cycleCount.setCountedAt(LocalDateTime.now());
        cycleCount.setStatus(variance.compareTo(BigDecimal.ZERO) == 0
                ? CycleCountStatus.COMPLETED
                : CycleCountStatus.APPROVAL_REQUIRED);
        return cycleCountRepository.save(cycleCount);
    }

    @Transactional
    public CycleCount approveCycleCount(Long cycleCountId, String approvedBy) {
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId).orElseThrow();
        if (cycleCount.getStatus() != CycleCountStatus.APPROVAL_REQUIRED) {
            return cycleCount;
        }
        BigDecimal expectedQuantity = Optional.ofNullable(cycleCount.getExpectedQuantity()).orElse(BigDecimal.ZERO);
        BigDecimal countedQuantity = Optional.ofNullable(cycleCount.getCountedQuantity()).orElse(expectedQuantity);
        BigDecimal delta = countedQuantity.subtract(expectedQuantity);

        if (delta.compareTo(BigDecimal.ZERO) != 0) {
            adjustStock(cycleCount.getProduct(), cycleCount.getWarehouse(), delta, StockMovementType.ADJUSTMENT,
                    "Cycle count " + cycleCount.getPlanNumber(), null, null, null);
        }

        cycleCount.setApprovedBy(approvedBy);
        cycleCount.setApprovedAt(LocalDateTime.now());
        cycleCount.setStatus(CycleCountStatus.COMPLETED);
        cycleCount.setExpectedQuantity(countedQuantity);
        return cycleCountRepository.save(cycleCount);
    }

    @Transactional
    public AutoReplenishResponse autoReplenish(AutoReplenishRequest request) {
        ReplenishmentComputation computation = computeReplenishmentPlans(request);
        if (computation.candidates().isEmpty()) {
            return new AutoReplenishResponse(List.of(), 0, 0, 0, BigDecimal.ZERO);
        }

        if (computation.plansBySupplier().isEmpty()) {
            return new AutoReplenishResponse(List.of(), computation.candidates().size(), 0, 0, BigDecimal.ZERO);
        }

        List<AutoReplenishPlanDTO> planDTOs = new ArrayList<>();
        BigDecimal totalBudget = BigDecimal.ZERO;
        int counter = 1;
        DateTimeFormatter formatter = DateTimeFormatter.BASIC_ISO_DATE;

        for (PurchasePlan plan : computation.plansBySupplier().values()) {
            if (plan.lines().isEmpty()) {
                continue;
            }
            PurchaseOrder order = new PurchaseOrder();
            order.setOrderNumber(String.format(Locale.ROOT, "PO-AUTO-%s-%02d", LocalDate.now().format(formatter), counter++));
            order.setVendorName(plan.supplier().getSupplierName());
            order.setOrderDate(LocalDate.now());
            order.setExpectedDate(LocalDate.now().plusDays(Math.max(plan.supplier().getLeadTimeDays(), 1)));
            order.setStatus(PurchaseOrderStatus.DRAFT);
            order.setLines(plan.lines());
            plan.lines().forEach(line -> line.setPurchaseOrder(order));

            PurchaseOrder saved = createPurchaseOrder(order);
            totalBudget = totalBudget.add(Optional.ofNullable(saved.getTotalAmount()).orElse(BigDecimal.ZERO));

            planDTOs.add(new AutoReplenishPlanDTO(
                    plan.supplier().getSupplierId(),
                    plan.supplier().getSupplierName(),
                    plan.supplier().getLeadTimeDays(),
                    plan.supplier().getScore(),
                    PurchaseOrderDTO.from(saved),
                    List.copyOf(plan.items()),
                    plan.rankedSuppliers()));
        }

        return new AutoReplenishResponse(planDTOs,
                computation.candidates().size(),
                computation.replenishedSkus(),
                planDTOs.size(),
                totalBudget);
    }

    public ReplenishmentPreviewResponse previewReplenishment(AutoReplenishRequest request) {
        ReplenishmentComputation computation = computeReplenishmentPlans(request);
        if (computation.candidates().isEmpty() || computation.plansBySupplier().isEmpty()) {
            return new ReplenishmentPreviewResponse(List.of(), computation.candidates().size(), 0);
        }

        List<AutoReplenishItemDTO> items = computation.plansBySupplier().values().stream()
                .flatMap(plan -> plan.items().stream())
                .sorted(Comparator.comparing(AutoReplenishItemDTO::getProductName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        return new ReplenishmentPreviewResponse(items, computation.candidates().size(), computation.replenishedSkus());
    }

    @Transactional(readOnly = true)
    public WavePickResponse planWavePicking(PlanWavePickRequest request) {
        int maxPerWave = Optional.ofNullable(request.getMaxOrdersPerWave()).orElse(6);
        maxPerWave = Math.min(Math.max(maxPerWave, 2), 25);
        boolean includeDrafts = Optional.ofNullable(request.getIncludeDrafts()).orElse(Boolean.FALSE);

        List<SalesOrder> candidateOrders;
        List<Long> ids = request.getSalesOrderIds();
        if (ids == null || ids.isEmpty()) {
            candidateOrders = new ArrayList<>(salesOrderRepository.findByStatus(SalesOrderStatus.CONFIRMED));
        } else {
            candidateOrders = salesOrderRepository.findAllById(ids);
        }

        List<SalesOrder> eligibleOrders = candidateOrders.stream()
                .filter(order -> order.getLines() != null && !order.getLines().isEmpty())
                .filter(order -> order.getStatus() != SalesOrderStatus.CANCELLED)
                .filter(order -> order.getStatus() != SalesOrderStatus.FULFILLED)
                .filter(order -> includeDrafts || order.getStatus() != SalesOrderStatus.DRAFT)
                .toList();

        if (eligibleOrders.isEmpty()) {
            return new WavePickResponse(List.of(), 0, 0, 0, 0, 0, 0);
        }

        Map<String, WarehouseLocation> locationIndex = warehouseIntelligenceService.listLocations().stream()
                .collect(HashMap::new, (map, location) -> map.put(location.getId(), location), Map::putAll);
        Map<String, String> productZones = buildPrimaryZoneIndex(locationIndex);

        Map<String, List<SalesOrder>> zoneBuckets = new HashMap<>();
        for (SalesOrder order : eligibleOrders) {
            String zone = determinePrimaryZone(order, productZones);
            zoneBuckets.computeIfAbsent(zone, key -> new ArrayList<>()).add(order);
        }

        zoneBuckets.values().forEach(list -> list.sort(Comparator
                .comparing((SalesOrder so) -> Optional.ofNullable(so.getDueDate()).orElse(LocalDate.MAX))
                .thenComparing(SalesOrder::getOrderNumber, Comparator.nullsLast(String::compareTo))));

        List<WavePickWaveDTO> waves = new ArrayList<>();
        Set<String> globalSkus = new HashSet<>();
        AtomicInteger waveSequence = new AtomicInteger(1);
        int totalUnits = 0;
        double totalDistance = 0;
        double totalDuration = 0;

        for (Map.Entry<String, List<SalesOrder>> entry : zoneBuckets.entrySet()) {
            List<SalesOrder> ordersInZone = entry.getValue();
            for (int index = 0; index < ordersInZone.size(); index += maxPerWave) {
                List<SalesOrder> waveOrders = ordersInZone.subList(index,
                        Math.min(index + maxPerWave, ordersInZone.size()));
                WaveAssemblyResult waveResult = assembleWave(entry.getKey(), waveOrders,
                        waveSequence.getAndIncrement());
                if (waveResult == null) {
                    continue;
                }
                waves.add(waveResult.wave());
                globalSkus.addAll(waveResult.skus());
                totalUnits += waveResult.units();
                totalDistance += waveResult.wave().getTotalDistance();
                totalDuration += waveResult.wave().getEstimatedDurationSeconds();
            }
        }

        int totalOrders = waves.stream().mapToInt(wave -> wave.getOrders().size()).sum();
        double averageUnitsPerWave = waves.isEmpty() ? 0 : (double) totalUnits / waves.size();
        return new WavePickResponse(waves, totalOrders, totalUnits, globalSkus.size(),
                Math.round(totalDistance * 10.0) / 10.0,
                Math.round(totalDuration * 10.0) / 10.0,
                Math.round(averageUnitsPerWave * 100.0) / 100.0);
    }

    private List<Product> resolveProductsForReplenishment(AutoReplenishRequest request) {
        List<Long> productIds = request.getProductIds();
        Collection<Product> pool;
        if (productIds == null || productIds.isEmpty()) {
            pool = productRepository.findAll();
        } else {
            pool = productRepository.findAllById(productIds);
        }
        return pool.stream()
                .sorted(Comparator.comparing(Product::getId, Comparator.nullsLast(Long::compareTo)))
                .collect(Collectors.toList());
    }

    private ReplenishmentNeed evaluateReplenishmentNeed(Product product,
                                                        int planningHorizonDays,
                                                        int safetyDays,
                                                        double serviceLevel) {
        BigDecimal totalQuantity = stockLevelRepository.findByProduct(product).stream()
                .map(StockLevel::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int currentOnHand = totalQuantity == null ? 0
                : totalQuantity.setScale(0, RoundingMode.HALF_UP).intValue();

        PredictiveInventoryResponse forecast;
        try {
            forecast = warehouseIntelligenceService.forecastInventory(product.getSku());
        } catch (RuntimeException ex) {
            forecast = null;
        }

        PredictiveReplenishmentResponse replenishmentAnalytics;
        try {
            replenishmentAnalytics = warehouseIntelligenceService.analyseReplenishment(product.getSku(), serviceLevel);
        } catch (RuntimeException ex) {
            replenishmentAnalytics = null;
        }

        Map<LocalDate, Integer> forecastMap = forecast != null && forecast.getForecast() != null
                ? forecast.getForecast()
                : Map.of();
        int projectedMinimum = forecastMap.values().stream()
                .min(Integer::compareTo)
                .orElse(currentOnHand);
        projectedMinimum = Math.min(projectedMinimum, currentOnHand);

        int terminalProjection = forecastMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .reduce((first, second) -> second)
                .map(Map.Entry::getValue)
                .orElse(currentOnHand);

        int consumption = Math.max(0, currentOnHand - terminalProjection);
        double dailyDemand = planningHorizonDays <= 0 ? 0 : (double) consumption / Math.max(planningHorizonDays, 1);
        if (replenishmentAnalytics != null && replenishmentAnalytics.getAverageDailyDemand() > 0) {
            dailyDemand = replenishmentAnalytics.getAverageDailyDemand();
        }

        int safetyStock = Math.max((int) Math.ceil(Math.max(dailyDemand, 0) * Math.max(safetyDays, 1)),
                (int) Math.ceil(5 * Math.max(serviceLevel, 0.5)));
        int projectedShortfall = Math.max(safetyStock - projectedMinimum, 0);

        boolean stockOutRisk = forecast != null && forecast.isStockOutRisk();
        boolean overstockRisk = forecast != null && forecast.isOverstockRisk();

        int horizonDemand = (int) Math.ceil(Math.max(dailyDemand, 0) * Math.max(planningHorizonDays, 1));
        int recommendedQuantity = 0;
        if (stockOutRisk || projectedShortfall > 0) {
            recommendedQuantity = Math.max(projectedShortfall + Math.max(horizonDemand, safetyStock), 0);
        }

        if (replenishmentAnalytics != null) {
            safetyStock = Math.max(safetyStock, replenishmentAnalytics.getReorderPoint());
            projectedShortfall = Math.max(projectedShortfall, Math.max(safetyStock - projectedMinimum, 0));
            recommendedQuantity = Math.max(recommendedQuantity, replenishmentAnalytics.getRecommendedOrderQuantity());
            if (replenishmentAnalytics.getAverageDailyDemand() > 0) {
                dailyDemand = replenishmentAnalytics.getAverageDailyDemand();
            }
            if (replenishmentAnalytics.getDaysUntilStockout() <= Math.max(safetyDays, 1)) {
                stockOutRisk = true;
            }
            if (replenishmentAnalytics.getRecommendedOrderQuantity() == 0
                    && replenishmentAnalytics.getConfidence() > 0.75
                    && currentOnHand > safetyStock * 1.5) {
                overstockRisk = true;
            }
        }

        return new ReplenishmentNeed(currentOnHand, projectedMinimum, safetyStock, projectedShortfall,
                Math.max(recommendedQuantity, 0), stockOutRisk, overstockRisk, dailyDemand, serviceLevel,
                planningHorizonDays, forecast, replenishmentAnalytics);
    }

    private SmartSourcingRequest buildSmartSourcingRequest(Product product,
                                                           ReplenishmentNeed need,
                                                           List<AutoReplenishRequest.SupplierPreference> preferences) {
        SmartSourcingRequest sourcingRequest = new SmartSourcingRequest();
        sourcingRequest.setProductId(product.getSku());
        sourcingRequest.setQuantity(Math.max(need.recommendedQuantity(), 1));
        if (preferences != null && !preferences.isEmpty()) {
            sourcingRequest.setPreferences(preferences.stream()
                    .map(this::toSmartPreference)
                    .collect(Collectors.toList()));
        }
        return sourcingRequest;
    }

    private SmartSourcingRequest.SupplierPreference toSmartPreference(AutoReplenishRequest.SupplierPreference preference) {
        SmartSourcingRequest.SupplierPreference supplierPreference = new SmartSourcingRequest.SupplierPreference();
        supplierPreference.setSupplierId(preference.getSupplierId());
        supplierPreference.setWeightPrice(Optional.ofNullable(preference.getWeightPrice()).orElse(0.4d));
        supplierPreference.setWeightReliability(Optional.ofNullable(preference.getWeightReliability()).orElse(0.4d));
        supplierPreference.setWeightSustainability(Optional.ofNullable(preference.getWeightSustainability()).orElse(0.2d));
        return supplierPreference;
    }

    private int adjustQuantityForSupplier(ReplenishmentNeed need, SmartSourcingResponse.SupplierScore supplier) {
        int leadTime = Math.max(supplier.getLeadTimeDays(), 1);
        int leadCover = (int) Math.ceil(Math.max(need.dailyDemand(), 0) * leadTime);
        int baseline = Math.max(need.projectedShortfall(), 0) + leadCover;
        return Math.max(Math.max(need.recommendedQuantity(), baseline), 0);
    }

    private AutoReplenishItemDTO buildAutoReplenishItem(Product product,
                                                        ReplenishmentNeed need,
                                                        int finalQuantity,
                                                        double serviceLevel) {
        PredictiveReplenishmentResponse analytics = need.analytics();
        String aiInsight = analytics != null ? analytics.getRationale() : "Keine KI-Abweichung erkannt.";
        String rationale = String.format(Locale.ROOT,
                "Forecast floor %d vs safety %d → order %d units über %d Tage. %s",
                need.projectedMinimum(), need.safetyStock(), finalQuantity, need.planningHorizonDays(), aiInsight);
        return new AutoReplenishItemDTO(
                product.getId(),
                product.getSku(),
                product.getName(),
                finalQuantity,
                Math.max(need.projectedShortfall(), 0),
                need.safetyStock(),
                need.stockOutRisk(),
                need.overstockRisk(),
                serviceLevel,
                rationale,
                analytics != null ? analytics.getDaysUntilStockout() : -1,
                analytics != null ? analytics.getConfidence() : 0.0,
                aiInsight,
                analytics != null ? analytics.getDailyDemandForecast() : Map.of());
    }

    private List<AutoReplenishSupplierDTO> convertSuppliers(List<SmartSourcingResponse.SupplierScore> scores) {
        if (scores == null) {
            return List.of();
        }
        return scores.stream()
                .map(score -> new AutoReplenishSupplierDTO(score.getSupplierId(), score.getSupplierName(),
                        score.getScore(), score.getLeadTimeDays()))
                .collect(Collectors.toList());
    }

    private BigDecimal determineUnitCost(Product product) {
        if (product.getUnitCost() != null && product.getUnitCost().compareTo(BigDecimal.ZERO) > 0) {
            return product.getUnitCost();
        }
        if (product.getUnitPrice() != null && product.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
            return product.getUnitPrice();
        }
        return BigDecimal.TEN;
    }

    private String nextCycleCountPlanNumber() {
        long sequence = cycleCountRepository.count() + 1;
        return String.format(Locale.ROOT, "CC-%s-%04d", LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE), sequence);
    }

    private boolean isOpenPurchaseOrder(PurchaseOrder order) {
        return order.getStatus() != PurchaseOrderStatus.RECEIVED && order.getStatus() != PurchaseOrderStatus.CANCELLED;
    }

    private LinkedHashSet<String> collectDetectedCodes(ReceivingPreviewRequest request) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        addCandidate(codes, request.getScanValue());
        if (request.getDetectedCodes() != null) {
            request.getDetectedCodes().forEach(code -> addCandidate(codes, code));
        }
        extractReferenceCandidates(request.getDocumentText()).forEach(code -> addCandidate(codes, code));
        addCandidate(codes, fallbackReferenceFromFile(request.getFileName()));
        return codes;
    }

    private void addCandidate(Set<String> target, String value) {
        String trimmed = safeText(value).trim();
        if (!trimmed.isBlank()) {
            target.add(trimmed);
        }
    }

    private PurchaseOrder findMatchingPurchaseOrder(Set<String> detectedCodes,
                                                    String documentText,
                                                    List<PurchaseOrder> openOrders) {
        for (String code : detectedCodes) {
            Optional<PurchaseOrder> exact = purchaseOrderRepository.findByOrderNumberIgnoreCase(code)
                    .filter(this::isOpenPurchaseOrder);
            if (exact.isPresent()) {
                return exact.get();
            }
        }

        String normalizedText = normalizeLookup(documentText);
        PurchaseOrder bestMatch = null;
        int bestScore = 0;

        for (PurchaseOrder order : openOrders) {
            int score = 0;
            String orderNumber = normalizeLookup(order.getOrderNumber());
            String vendorName = normalizeLookup(order.getVendorName());

            if (!orderNumber.isBlank() && normalizedText.contains(orderNumber)) {
                score += 90;
            }
            if (!vendorName.isBlank() && normalizedText.contains(vendorName)) {
                score += 20;
            }
            if (order.getLines() != null) {
                for (PurchaseOrderLine line : order.getLines()) {
                    if (line.getProduct() == null || line.getProduct().getSku() == null) {
                        continue;
                    }
                    if (normalizedText.contains(normalizeLookup(line.getProduct().getSku()))) {
                        score += 18;
                    }
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestMatch = order;
            }
        }

        return bestScore >= 60 ? bestMatch : null;
    }

    private List<ReceivingPreviewResponse.ReceivingPreviewItem> buildPreviewItemsForOrder(PurchaseOrder order) {
        if (order.getLines() == null) {
            return List.of();
        }
        return order.getLines().stream()
                .filter(line -> line.getProduct() != null)
                .map(line -> new ReceivingPreviewResponse.ReceivingPreviewItem(
                        line.getProduct().getId(),
                        line.getProduct().getSku(),
                        line.getProduct().getName(),
                        Optional.ofNullable(line.getQuantity()).orElse(BigDecimal.ZERO),
                        line.getProduct().getUnitOfMeasure(),
                        "PURCHASE_ORDER"))
                .toList();
    }

    private List<ReceivingPreviewResponse.ReceivingPreviewItem> detectPreviewItems(String documentText,
                                                                                   Set<String> detectedCodes) {
        List<Product> products = productRepository.findAll().stream()
                .filter(product -> product.getSku() != null && !product.getSku().isBlank())
                .sorted(Comparator.comparingInt((Product product) -> product.getSku().length()).reversed())
                .toList();
        if (products.isEmpty()) {
            return List.of();
        }

        Map<Long, Product> productsById = products.stream().collect(Collectors.toMap(Product::getId, product -> product));
        Map<Long, BigDecimal> quantitiesByProduct = new LinkedHashMap<>();
        Map<Long, String> sourceByProduct = new LinkedHashMap<>();

        for (String line : safeText(documentText).split("\\R+")) {
            String normalizedLine = normalizeLookup(line);
            if (normalizedLine.isBlank()) {
                continue;
            }
            for (Product product : products) {
                String sku = normalizeLookup(product.getSku());
                if (sku.isBlank() || !normalizedLine.contains(sku)) {
                    continue;
                }
                BigDecimal quantity = extractQuantityFromLine(line, product.getSku());
                quantitiesByProduct.merge(product.getId(), quantity != null ? quantity : BigDecimal.ONE, BigDecimal::add);
                sourceByProduct.putIfAbsent(product.getId(), "DOCUMENT_TEXT");
            }
        }

        for (String code : detectedCodes) {
            productRepository.findBySkuIgnoreCase(code).ifPresent(product -> {
                quantitiesByProduct.putIfAbsent(product.getId(), BigDecimal.ONE);
                sourceByProduct.putIfAbsent(product.getId(), "BARCODE");
            });
        }

        return quantitiesByProduct.entrySet().stream()
                .map(entry -> {
                    Product product = productsById.get(entry.getKey());
                    return product == null ? null : new ReceivingPreviewResponse.ReceivingPreviewItem(
                            product.getId(),
                            product.getSku(),
                            product.getName(),
                            entry.getValue(),
                            product.getUnitOfMeasure(),
                            sourceByProduct.getOrDefault(product.getId(), "DOCUMENT_TEXT"));
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private BigDecimal extractQuantityFromLine(String line, String sku) {
        Matcher labeled = LABELED_QUANTITY_PATTERN.matcher(line);
        if (labeled.find()) {
            return parseDecimal(labeled.group(1));
        }

        String upperLine = line.toUpperCase(Locale.ROOT);
        int skuIndex = upperLine.indexOf(sku.toUpperCase(Locale.ROOT));
        String quantityRegion = skuIndex >= 0 && skuIndex + sku.length() < line.length()
                ? line.substring(skuIndex + sku.length())
                : line;
        Matcher generic = GENERIC_NUMBER_PATTERN.matcher(quantityRegion);
        while (generic.find()) {
            BigDecimal parsed = parseDecimal(generic.group(1));
            if (parsed != null && parsed.compareTo(BigDecimal.ZERO) > 0) {
                return parsed;
            }
        }
        return null;
    }

    private BigDecimal parseDecimal(String value) {
        String normalized = safeText(value).replace(",", ".").trim();
        if (normalized.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean matchesOrderExactly(PurchaseOrder order, List<ReceivingApplyRequest.ReceivingApplyItem> items) {
        if (order.getLines() == null || items == null) {
            return false;
        }
        Map<Long, BigDecimal> expected = new LinkedHashMap<>();
        for (PurchaseOrderLine line : order.getLines()) {
            if (line.getProduct() == null || line.getProduct().getId() == null || line.getQuantity() == null) {
                continue;
            }
            expected.merge(line.getProduct().getId(), line.getQuantity(), BigDecimal::add);
        }

        Map<Long, BigDecimal> actual = new LinkedHashMap<>();
        for (ReceivingApplyRequest.ReceivingApplyItem item : items) {
            if (item == null || item.getProductId() == null || item.getQuantity() == null) {
                continue;
            }
            if (item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            actual.merge(item.getProductId(), item.getQuantity(), BigDecimal::add);
        }

        if (expected.size() != actual.size()) {
            return false;
        }

        for (Map.Entry<Long, BigDecimal> entry : expected.entrySet()) {
            BigDecimal actualQuantity = actual.get(entry.getKey());
            if (actualQuantity == null || actualQuantity.compareTo(entry.getValue()) != 0) {
                return false;
            }
        }
        return true;
    }

    private String extractVendorName(String documentText, List<PurchaseOrder> openOrders) {
        String safeDocumentText = safeText(documentText);
        Matcher labeled = LABELED_VENDOR_PATTERN.matcher(safeDocumentText);
        if (labeled.find()) {
            return labeled.group(1).trim();
        }

        String normalizedText = normalizeLookup(safeDocumentText);
        return openOrders.stream()
                .map(PurchaseOrder::getVendorName)
                .filter(name -> !normalizeLookup(name).isBlank() && normalizedText.contains(normalizeLookup(name)))
                .findFirst()
                .orElse(null);
    }

    private String extractDate(String documentText) {
        Matcher matcher = DATE_PATTERN.matcher(safeText(documentText));
        if (!matcher.find()) {
            return null;
        }
        String raw = matcher.group(1);
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"))) {
            try {
                return LocalDate.parse(raw, formatter).toString();
            } catch (Exception ignored) {
                // Try the next known format.
            }
        }
        return raw;
    }

    private List<String> extractReferenceCandidates(String documentText) {
        String safeDocumentText = safeText(documentText);
        LinkedHashSet<String> references = new LinkedHashSet<>();

        Matcher labeled = LABELED_REFERENCE_PATTERN.matcher(safeDocumentText);
        while (labeled.find()) {
            addCandidate(references, labeled.group(1));
        }

        Matcher generic = GENERIC_REFERENCE_PATTERN.matcher(safeDocumentText.toUpperCase(Locale.ROOT));
        while (generic.find()) {
            addCandidate(references, generic.group());
        }

        return new ArrayList<>(references);
    }

    private String fallbackReferenceFromFile(String fileName) {
        String safeFileName = safeText(fileName).trim();
        if (safeFileName.isBlank()) {
            return null;
        }
        int extensionIndex = safeFileName.lastIndexOf('.');
        String baseName = extensionIndex > 0 ? safeFileName.substring(0, extensionIndex) : safeFileName;
        return baseName.replace('_', '-').trim();
    }

    private String abbreviate(String value, int maxLength) {
        String safeValue = safeText(value).trim();
        if (safeValue.length() <= maxLength) {
            return safeValue;
        }
        return safeValue.substring(0, Math.max(0, maxLength - 1)).trim() + "…";
    }

    private String normalizeLookup(String value) {
        return safeText(value)
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private static final class ReplenishmentNeed {
        private final int currentOnHand;
        private final int projectedMinimum;
        private final int safetyStock;
        private final int projectedShortfall;
        private final int recommendedQuantity;
        private final boolean stockOutRisk;
        private final boolean overstockRisk;
        private final double dailyDemand;
        private final double serviceLevel;
        private final int planningHorizonDays;
        private final PredictiveInventoryResponse forecast;
        private final PredictiveReplenishmentResponse analytics;

        private ReplenishmentNeed(int currentOnHand,
                                   int projectedMinimum,
                                   int safetyStock,
                                   int projectedShortfall,
                                   int recommendedQuantity,
                                   boolean stockOutRisk,
                                   boolean overstockRisk,
                                   double dailyDemand,
                                   double serviceLevel,
                                   int planningHorizonDays,
                                   PredictiveInventoryResponse forecast,
                                   PredictiveReplenishmentResponse analytics) {
            this.currentOnHand = currentOnHand;
            this.projectedMinimum = projectedMinimum;
            this.safetyStock = safetyStock;
            this.projectedShortfall = projectedShortfall;
            this.recommendedQuantity = recommendedQuantity;
            this.stockOutRisk = stockOutRisk;
            this.overstockRisk = overstockRisk;
            this.dailyDemand = dailyDemand;
            this.serviceLevel = serviceLevel;
            this.planningHorizonDays = planningHorizonDays;
            this.forecast = forecast;
            this.analytics = analytics;
        }

        private boolean requiresReplenishment() {
            if (recommendedQuantity > 0 && (stockOutRisk || projectedShortfall > 0)) {
                return true;
            }
            return analytics != null && analytics.getRecommendedOrderQuantity() > 0
                    && analytics.getDaysUntilStockout() <= Math.max(planningHorizonDays / 2, 3);
        }

        private int currentOnHand() {
            return currentOnHand;
        }

        private int projectedMinimum() {
            return projectedMinimum;
        }

        private int safetyStock() {
            return safetyStock;
        }

        private int projectedShortfall() {
            return projectedShortfall;
        }

        private int recommendedQuantity() {
            return recommendedQuantity;
        }

        private boolean stockOutRisk() {
            return stockOutRisk;
        }

        private boolean overstockRisk() {
            return overstockRisk;
        }

        private double dailyDemand() {
            return dailyDemand;
        }

        private double serviceLevel() {
            return serviceLevel;
        }

        private int planningHorizonDays() {
            return planningHorizonDays;
        }

        @SuppressWarnings("unused")
        private PredictiveInventoryResponse forecast() {
            return forecast;
        }

        private PredictiveReplenishmentResponse analytics() {
            return analytics;
        }
    }

    private ReplenishmentComputation computeReplenishmentPlans(AutoReplenishRequest request) {
        List<Product> candidates = resolveProductsForReplenishment(request);
        if (candidates.isEmpty()) {
            return new ReplenishmentComputation(List.of(), Map.of(), 0);
        }

        int planningHorizon = Optional.ofNullable(request.getPlanningHorizonDays()).orElse(28);
        int safetyDays = Optional.ofNullable(request.getSafetyDays()).orElse(7);
        double serviceLevel = Optional.ofNullable(request.getServiceLevelTarget()).orElse(0.95d);

        Map<String, PurchasePlan> plansBySupplier = new LinkedHashMap<>();
        int replenishedSkus = 0;

        for (Product product : candidates) {
            if (!product.isActive()) {
                continue;
            }
            ReplenishmentNeed need = evaluateReplenishmentNeed(product, planningHorizon, safetyDays, serviceLevel);
            if (!need.requiresReplenishment()) {
                continue;
            }

            SmartSourcingRequest sourcingRequest = buildSmartSourcingRequest(product, need, request.getSupplierPreferences());
            SmartSourcingResponse sourcingResponse = warehouseIntelligenceService.selectSupplier(sourcingRequest);
            SmartSourcingResponse.SupplierScore supplier = sourcingResponse.getRecommended();
            if (supplier == null) {
                continue;
            }

            int finalQuantity = adjustQuantityForSupplier(need, supplier);
            if (finalQuantity <= 0) {
                continue;
            }

            AutoReplenishItemDTO item = buildAutoReplenishItem(product, need, finalQuantity, serviceLevel);
            List<AutoReplenishSupplierDTO> rankedSuppliers = convertSuppliers(sourcingResponse.getRankedSuppliers());
            PurchasePlan plan = plansBySupplier.computeIfAbsent(supplier.getSupplierId(), key -> new PurchasePlan(supplier, rankedSuppliers));
            plan.items().add(item);

            PurchaseOrderLine line = new PurchaseOrderLine();
            line.setProduct(product);
            line.setQuantity(BigDecimal.valueOf(finalQuantity));
            line.setUnitCost(determineUnitCost(product));
            plan.lines().add(line);
            plan.incrementBudget(line.getUnitCost().multiply(line.getQuantity()));
            replenishedSkus++;
        }

        return new ReplenishmentComputation(candidates, plansBySupplier, replenishedSkus);
    }

    private static final class PurchasePlan {
        private final SmartSourcingResponse.SupplierScore supplier;
        private final List<PurchaseOrderLine> lines = new ArrayList<>();
        private final List<AutoReplenishItemDTO> items = new ArrayList<>();
        private final List<AutoReplenishSupplierDTO> rankedSuppliers;
        private BigDecimal budget = BigDecimal.ZERO;

        private PurchasePlan(SmartSourcingResponse.SupplierScore supplier, List<AutoReplenishSupplierDTO> rankedSuppliers) {
            this.supplier = supplier;
            this.rankedSuppliers = new ArrayList<>(rankedSuppliers);
        }

        private SmartSourcingResponse.SupplierScore supplier() {
            return supplier;
        }

        private List<PurchaseOrderLine> lines() {
            return lines;
        }

        private List<AutoReplenishItemDTO> items() {
            return items;
        }

        private List<AutoReplenishSupplierDTO> rankedSuppliers() {
            return rankedSuppliers;
        }

        private void incrementBudget(BigDecimal amount) {
            if (amount != null) {
                budget = budget.add(amount);
            }
        }

        @SuppressWarnings("unused")
        private BigDecimal budget() {
            return budget;
        }
    }

    private record ReplenishmentComputation(List<Product> candidates,
                                            Map<String, PurchasePlan> plansBySupplier,
                                            int replenishedSkus) {
    }

    private Map<String, String> buildPrimaryZoneIndex(Map<String, WarehouseLocation> locationIndex) {
        Map<String, Map<String, Integer>> zoneByProduct = new HashMap<>();
        for (InventoryItem item : warehouseIntelligenceService.listInventory()) {
            if (item.getProductId() == null) {
                continue;
            }
            WarehouseLocation location = locationIndex.get(item.getLocationId());
            String zone = location != null && location.getZone() != null
                    ? location.getZone()
                    : "UNASSIGNED";
            zoneByProduct
                    .computeIfAbsent(item.getProductId(), key -> new HashMap<>())
                    .merge(zone, Math.max(0, item.getQuantity()), Integer::sum);
        }
        Map<String, String> productZones = new HashMap<>();
        zoneByProduct.forEach((product, zones) -> productZones.put(product,
                zones.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse("UNASSIGNED")));
        return productZones;
    }

    private String determinePrimaryZone(SalesOrder order, Map<String, String> productZones) {
        Map<String, Integer> zoneScore = new HashMap<>();
        for (SalesOrderLine line : order.getLines()) {
            if (line.getProduct() == null || line.getProduct().getSku() == null) {
                continue;
            }
            int units = toUnits(line.getQuantity());
            if (units <= 0) {
                continue;
            }
            String sku = line.getProduct().getSku();
            String zone = productZones.getOrDefault(sku, "UNASSIGNED");
            zoneScore.merge(zone, units, Integer::sum);
        }
        return zoneScore.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("UNASSIGNED");
    }

    private WaveAssemblyResult assembleWave(String zone, List<SalesOrder> orders, int sequence) {
        Map<String, Integer> skuDemand = new HashMap<>();
        Map<String, Set<String>> skuOrders = new HashMap<>();
        List<WavePickOrderSummaryDTO> orderSummaries = new ArrayList<>();
        int totalUnits = 0;

        for (SalesOrder order : orders) {
            int lineCount = 0;
            int orderUnits = 0;
            for (SalesOrderLine line : order.getLines()) {
                if (line.getProduct() == null || line.getProduct().getSku() == null) {
                    continue;
                }
                int units = toUnits(line.getQuantity());
                if (units <= 0) {
                    continue;
                }
                lineCount++;
                orderUnits += units;
                String sku = line.getProduct().getSku();
                skuDemand.merge(sku, units, Integer::sum);
                skuOrders.computeIfAbsent(sku, key -> new HashSet<>()).add(order.getOrderNumber());
            }
            if (lineCount > 0) {
                totalUnits += orderUnits;
                orderSummaries.add(new WavePickOrderSummaryDTO(order.getId(), order.getOrderNumber(),
                        order.getCustomerName(), lineCount, orderUnits, order.getStatus().name()));
            }
        }

        if (orderSummaries.isEmpty() || skuDemand.isEmpty()) {
            return null;
        }

        PickRouteRequest pickRequest = new PickRouteRequest();
        List<PickRouteRequest.PickItem> items = skuDemand.entrySet().stream()
                .map(entry -> {
                    PickRouteRequest.PickItem item = new PickRouteRequest.PickItem();
                    item.setProductId(entry.getKey());
                    item.setQuantity(entry.getValue());
                    return item;
                })
                .toList();
        pickRequest.setItems(items);

        PickRouteResponse route = warehouseIntelligenceService.planPickRoute(pickRequest);
        List<WavePickStopDTO> stops = route.getWaypoints().stream()
                .map(waypoint -> new WavePickStopDTO(waypoint.getLocationId(), waypoint.getProductId(),
                        waypoint.getQuantity(), waypoint.getEtaSeconds(),
                        skuOrders.containsKey(waypoint.getProductId())
                                ? skuOrders.get(waypoint.getProductId()).stream().sorted().toList()
                                : List.of()))
                .toList();

        double unitsPerHour = route.getEstimatedDurationSeconds() <= 0 ? 0
                : (totalUnits / (route.getEstimatedDurationSeconds() / 3600.0));

        WavePickWaveDTO wave = new WavePickWaveDTO(
                String.format(Locale.ROOT, "%s-W%03d", zone, sequence),
                zone,
                orderSummaries,
                stops,
                route.getTotalDistance(),
                route.getEstimatedDurationSeconds(),
                totalUnits,
                skuDemand.size(),
                Math.round(unitsPerHour * 100.0) / 100.0);

        return new WaveAssemblyResult(wave, totalUnits, skuDemand.keySet());
    }

    private int toUnits(BigDecimal quantity) {
        if (quantity == null) {
            return 0;
        }
        try {
            return quantity.setScale(0, RoundingMode.HALF_UP).intValueExact();
        } catch (ArithmeticException ex) {
            return quantity.intValue();
        }
    }

    private record WaveAssemblyResult(WavePickWaveDTO wave, int units, Set<String> skus) {
    }
}
