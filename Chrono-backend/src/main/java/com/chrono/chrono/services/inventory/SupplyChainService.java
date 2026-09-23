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
import com.chrono.chrono.dto.inventory.CreateWarehouseBinRequest;
import com.chrono.chrono.dto.inventory.StockAdjustmentRequest;
import com.chrono.chrono.dto.inventory.WarehouseBinDTO;
import com.chrono.chrono.dto.inventory.WavePickOrderSummaryDTO;
import com.chrono.chrono.dto.inventory.WavePickResponse;
import com.chrono.chrono.dto.inventory.WavePickStopDTO;
import com.chrono.chrono.dto.inventory.WavePickWaveDTO;
import com.chrono.chrono.entities.Company;
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
    private final WarehouseBinRepository warehouseBinRepository;
    private final StockReservationRepository stockReservationRepository;
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
                              WarehouseBinRepository warehouseBinRepository,
                              StockReservationRepository stockReservationRepository,
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
        this.warehouseBinRepository = warehouseBinRepository;
        this.stockReservationRepository = stockReservationRepository;
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
        ensureUniqueProductSku(product);
        Product saved = productRepository.save(product);
        warehouseIntelligenceService.registerProduct(saved);
        return saved;
    }

    @Transactional
    public Product saveProduct(Product product, Company company) {
        if (company != null) {
            product.setCompany(company);
        }
        return saveProduct(product);
    }

    @Transactional
    public Warehouse saveWarehouse(Warehouse warehouse) {
        ensureUniqueWarehouseCode(warehouse);
        Warehouse saved = warehouseRepository.save(warehouse);
        ensureDefaultBin(saved);
        warehouseIntelligenceService.registerWarehouse(saved);
        return saved;
    }

    @Transactional
    public Warehouse saveWarehouse(Warehouse warehouse, Company company) {
        if (company != null) {
            warehouse.setCompany(company);
        }
        return saveWarehouse(warehouse);
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
        return adjustStock(product, warehouse, null, quantity, type, reference, null, lotNumber,
                serialNumber, expirationDate, InventoryStatus.AVAILABLE, null, null);
    }

    @Transactional
    public StockMovement adjustStock(Product product,
                                     Warehouse warehouse,
                                     WarehouseBin warehouseBin,
                                     BigDecimal quantity,
                                     StockMovementType type,
                                     String reference,
                                     String notes,
                                     String lotNumber,
                                     String serialNumber,
                                     LocalDate expirationDate,
                                     InventoryStatus inventoryStatus,
                                     String createdBy,
                                     String idempotencyKey) {
        if (product == null || warehouse == null || quantity == null || type == null) {
            throw new IllegalArgumentException("Product, warehouse, quantity and movement type are required");
        }
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Quantity change cannot be zero");
        }
        ensureSameCompany(product.getCompany(), warehouse.getCompany(),
                "Product and warehouse must belong to the same company");

        String normalizedIdempotencyKey = trimToNull(idempotencyKey);
        WarehouseBin effectiveBin = warehouseBin == null ? ensureDefaultBin(warehouse) : warehouseBin;
        if (!effectiveBin.isActive() || effectiveBin.getWarehouse() == null
                || !warehouse.getId().equals(effectiveBin.getWarehouse().getId())) {
            throw new IllegalArgumentException("Warehouse bin must be active and belong to the selected warehouse");
        }
        WarehouseBin lockedBin = warehouseBinRepository.findForUpdate(effectiveBin.getId()).orElseThrow();
        if (normalizedIdempotencyKey != null) {
            Optional<StockMovement> existing = product.getCompany() != null && product.getCompany().getId() != null
                    ? stockMovementRepository.findByIdempotencyKeyAndProduct_Company_Id(normalizedIdempotencyKey, product.getCompany().getId())
                    : stockMovementRepository.findByIdempotencyKey(normalizedIdempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        InventoryStatus effectiveStatus = inventoryStatus == null ? InventoryStatus.AVAILABLE : inventoryStatus;
        String normalizedLot = trimToNull(lotNumber);
        String normalizedSerial = trimToNull(serialNumber);
        String inventoryKey = inventoryKey(lockedBin, normalizedLot, normalizedSerial, expirationDate, effectiveStatus);
        StockLevel level = stockLevelRepository.findForUpdate(product, warehouse, inventoryKey)
                .orElseGet(() -> {
                    StockLevel created = new StockLevel();
                    created.setProduct(product);
                    created.setWarehouse(warehouse);
                    created.setWarehouseBin(lockedBin);
                    created.setInventoryKey(inventoryKey);
                    created.setLotNumber(normalizedLot);
                    created.setSerialNumber(normalizedSerial);
                    created.setExpirationDate(expirationDate);
                    created.setInventoryStatus(effectiveStatus);
                    created.setQuantity(BigDecimal.ZERO);
                    created.setReservedQuantity(BigDecimal.ZERO);
                    return stockLevelRepository.saveAndFlush(created);
                });
        BigDecimal current = Optional.ofNullable(level.getQuantity()).orElse(BigDecimal.ZERO);
        BigDecimal newQty = current.add(quantity);
        BigDecimal reserved = Optional.ofNullable(level.getReservedQuantity()).orElse(BigDecimal.ZERO);
        if (newQty.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Insufficient stock: the posting would create a negative balance");
        }
        if (newQty.compareTo(reserved) < 0) {
            throw new IllegalStateException("Stock is reserved and cannot be issued by this posting");
        }
        level.setQuantity(newQty);
        stockLevelRepository.save(level);

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setWarehouse(warehouse);
        movement.setWarehouseBin(lockedBin);
        movement.setType(type);
        movement.setQuantityChange(quantity);
        movement.setReference(trimToNull(reference));
        movement.setNotes(trimToNull(notes));
        movement.setLotNumber(normalizedLot);
        movement.setSerialNumber(normalizedSerial);
        movement.setExpirationDate(expirationDate);
        movement.setInventoryStatus(effectiveStatus);
        movement.setCreatedBy(trimToNull(createdBy));
        movement.setIdempotencyKey(normalizedIdempotencyKey);
        StockMovement savedMovement = stockMovementRepository.save(movement);
        warehouseIntelligenceService.applyStockMovement(product, warehouse, quantity, level.getQuantity());
        return savedMovement;
    }

    @Transactional
    public StockMovement adjustStock(Long companyId, StockAdjustmentRequest request, String actor) {
        if (trimToNull(request.getSerialNumber()) != null
                && request.getQuantityChange().abs().compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("Serialized stock must be posted one unit at a time");
        }
        Product product = requireProduct(companyId, request.getProductId());
        Warehouse warehouse = requireWarehouse(companyId, request.getWarehouseId());
        WarehouseBin bin = request.getWarehouseBinId() == null ? null : requireWarehouseBin(companyId, request.getWarehouseBinId());
        return adjustStock(product, warehouse, bin, request.getQuantityChange(), request.getType(),
                request.getReference(), request.getNotes(), request.getLotNumber(), request.getSerialNumber(),
                request.getExpirationDate(), request.getInventoryStatus(), actor, request.getIdempotencyKey());
    }

    @Transactional(readOnly = true)
    public List<WarehouseBinDTO> listWarehouseBins(Long companyId, Long warehouseId) {
        Warehouse warehouse = requireWarehouse(companyId, warehouseId);
        return warehouseBinRepository.findAllByWarehouse_IdOrderByPickSequenceAscCodeAsc(warehouse.getId()).stream()
                .map(WarehouseBinDTO::from).toList();
    }

    @Transactional
    public WarehouseBinDTO createWarehouseBin(Long companyId, Long warehouseId, CreateWarehouseBinRequest request) {
        Warehouse warehouse = requireWarehouse(companyId, warehouseId);
        String code = request.getCode().trim().toUpperCase(Locale.ROOT);
        if (warehouseBinRepository.findByWarehouse_IdAndCodeIgnoreCase(warehouseId, code).isPresent()) {
            throw new IllegalArgumentException("Warehouse bin code already exists in this warehouse");
        }
        WarehouseBin bin = new WarehouseBin();
        bin.setWarehouse(warehouse);
        bin.setCode(code);
        bin.setName(request.getName().trim());
        bin.setZone(trimToNull(request.getZone()));
        bin.setAisle(trimToNull(request.getAisle()));
        bin.setRack(trimToNull(request.getRack()));
        bin.setShelf(trimToNull(request.getShelf()));
        bin.setBarcode(trimToNull(request.getBarcode()));
        bin.setPickSequence(Optional.ofNullable(request.getPickSequence()).orElse(0));
        bin.setCapacityQuantity(request.getCapacityQuantity());
        return WarehouseBinDTO.from(warehouseBinRepository.save(bin));
    }

    @Transactional(readOnly = true)
    public Page<StockMovement> listStockMovements(Pageable pageable) {
        return stockMovementRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<StockMovement> listStockMovements(Long companyId, Pageable pageable) {
        return stockMovementRepository.findAllByProduct_Company_Id(companyId, pageable);
    }

    @Transactional(readOnly = true)
    public ReceivingPreviewResponse previewReceiving(ReceivingPreviewRequest request) {
        return previewReceivingInternal(null, request);
    }

    @Transactional(readOnly = true)
    public ReceivingPreviewResponse previewReceiving(Long companyId, ReceivingPreviewRequest request) {
        return previewReceivingInternal(companyId, request);
    }

    private ReceivingPreviewResponse previewReceivingInternal(Long companyId, ReceivingPreviewRequest request) {
        String documentText = safeText(request.getDocumentText());
        List<PurchaseOrder> openOrders = loadPurchaseOrders(companyId).stream()
                .filter(this::isOpenPurchaseOrder)
                .toList();
        LinkedHashSet<String> detectedCodes = collectDetectedCodes(request);
        PurchaseOrder matchedOrder = findMatchingPurchaseOrder(companyId, detectedCodes, documentText, openOrders);

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
                : detectPreviewItems(companyId, documentText, detectedCodes);

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
        return applyReceivingInternal(null, request, warehouse);
    }

    @Transactional
    public ReceivingApplyResponse applyReceiving(Long companyId, ReceivingApplyRequest request, Warehouse warehouse) {
        return applyReceivingInternal(companyId, request, warehouse);
    }

    private ReceivingApplyResponse applyReceivingInternal(Long companyId, ReceivingApplyRequest request, Warehouse warehouse) {
        if (request.getWarehouseId() == null) {
            throw new IllegalArgumentException("Warehouse is required");
        }
        ensureCompanyMatches(companyId, warehouse != null ? warehouse.getCompany() : null, "Warehouse");

        PurchaseOrder order = request.getPurchaseOrderId() == null
                ? null
                : requirePurchaseOrder(companyId, request.getPurchaseOrderId());
        if (order != null) {
            ensureSameCompany(order.getCompany(), warehouse != null ? warehouse.getCompany() : null,
                    "Purchase order and warehouse must belong to the same company");
        }

        String reference = safeText(request.getReference());
        if (reference.isBlank() && order != null) {
            reference = order.getOrderNumber();
        }
        if (reference.isBlank()) {
            reference = "SCAN-RECEIPT";
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
            if (trimToNull(item.getSerialNumber()) != null && item.getQuantity().compareTo(BigDecimal.ONE) != 0) {
                throw new IllegalArgumentException("Serialized goods receipts must be posted one unit at a time");
            }
            Product product = requireProduct(companyId, item.getProductId());
            WarehouseBin bin = item.getWarehouseBinId() == null ? null : requireWarehouseBin(companyId, item.getWarehouseBinId());
            if (order != null) {
                PurchaseOrderLine orderLine = order.getLines().stream()
                        .filter(line -> line.getProduct() != null && product.getId().equals(line.getProduct().getId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Received product is not part of the purchase order"));
                BigDecimal openQuantity = orderLine.getOpenQuantity();
                if (item.getQuantity().compareTo(openQuantity) > 0) {
                    throw new IllegalArgumentException("Received quantity exceeds the open purchase order quantity for " + product.getSku());
                }
                orderLine.setReceivedQuantity(Optional.ofNullable(orderLine.getReceivedQuantity()).orElse(BigDecimal.ZERO)
                        .add(item.getQuantity()));
            }
            adjustStock(product, warehouse, bin, item.getQuantity(), StockMovementType.RECEIPT,
                    reference, "Goods receipt", item.getLotNumber(), item.getSerialNumber(), item.getExpirationDate(),
                    item.getInventoryStatus(), null, null);
            totalQuantity = totalQuantity.add(item.getQuantity());
            bookedItemCount++;
        }

        if (bookedItemCount == 0) {
            throw new IllegalArgumentException("No positive quantities were provided for receiving");
        }

        boolean completed = false;
        if (order != null) {
            completed = order.getLines().stream().allMatch(line -> line.getOpenQuantity().compareTo(BigDecimal.ZERO) == 0);
            order.setStatus(completed ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED);
            purchaseOrderRepository.save(order);
            if (completed) {
                recordPurchaseOrderInvoice(order);
            }
        }

        return new ReceivingApplyResponse(
                order == null ? "DIRECT_RECEIPT" : (completed ? "PURCHASE_ORDER" : "PURCHASE_ORDER_PARTIAL"),
                order == null ? "Goods receipt posted from scan/document analysis."
                        : completed ? "Purchase order fully received and stock posted."
                        : "Goods receipt posted. Purchase order remains open for the remaining quantity.",
                reference,
                order != null ? order.getOrderNumber() : null,
                completed,
                bookedItemCount,
                totalQuantity
        );
    }

    @Transactional
    public PurchaseOrder createPurchaseOrder(PurchaseOrder purchaseOrder) {
        return createPurchaseOrderInternal(purchaseOrder, null);
    }

    @Transactional
    public PurchaseOrder createPurchaseOrder(PurchaseOrder purchaseOrder, Company company) {
        return createPurchaseOrderInternal(purchaseOrder, company);
    }

    private PurchaseOrder createPurchaseOrderInternal(PurchaseOrder purchaseOrder, Company company) {
        Company effectiveCompany = resolveScopedCompany(company, purchaseOrder.getCompany(), extractPurchaseOrderProducts(purchaseOrder));
        purchaseOrder.setCompany(effectiveCompany);
        ensureUniquePurchaseOrderNumber(purchaseOrder);
        BigDecimal total = BigDecimal.ZERO;
        if (purchaseOrder.getLines() != null) {
            for (PurchaseOrderLine line : purchaseOrder.getLines()) {
                if (line.getProduct() == null) {
                    throw new IllegalArgumentException("Purchase order lines require a product");
                }
                ensureCompanyOwned(line.getProduct().getCompany(), effectiveCompany, "Purchase order line product");
                line.setPurchaseOrder(purchaseOrder);
                line.setReceivedQuantity(Optional.ofNullable(line.getReceivedQuantity()).orElse(BigDecimal.ZERO));
                BigDecimal lineTotal = line.getUnitCost().multiply(line.getQuantity());
                total = total.add(lineTotal);
            }
        }
        purchaseOrder.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));
        return purchaseOrderRepository.save(purchaseOrder);
    }

    @Transactional
    public PurchaseOrder receivePurchaseOrder(Long purchaseOrderId, Warehouse warehouse) {
        return receivePurchaseOrderInternal(null, purchaseOrderId, warehouse);
    }

    @Transactional
    public PurchaseOrder receivePurchaseOrder(Long companyId, Long purchaseOrderId, Warehouse warehouse) {
        return receivePurchaseOrderInternal(companyId, purchaseOrderId, warehouse);
    }

    private PurchaseOrder receivePurchaseOrderInternal(Long companyId, Long purchaseOrderId, Warehouse warehouse) {
        PurchaseOrder order = requirePurchaseOrder(companyId, purchaseOrderId);
        ensureCompanyMatches(companyId, order.getCompany(), "Purchase order");
        ensureCompanyMatches(companyId, warehouse != null ? warehouse.getCompany() : null, "Warehouse");
        ensureSameCompany(order.getCompany(), warehouse != null ? warehouse.getCompany() : null,
                "Purchase order and warehouse must belong to the same company");
        if (order.getStatus() == PurchaseOrderStatus.RECEIVED) {
            return order;
        }
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled purchase orders cannot be received");
        }
        order.setExpectedDate(LocalDate.now());
        if (order.getLines() != null) {
            for (PurchaseOrderLine line : order.getLines()) {
                BigDecimal openQuantity = line.getOpenQuantity();
                if (openQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                adjustStock(line.getProduct(), warehouse, openQuantity, StockMovementType.RECEIPT,
                        order.getOrderNumber(), null, null, null);
                line.setReceivedQuantity(Optional.ofNullable(line.getReceivedQuantity()).orElse(BigDecimal.ZERO).add(openQuantity));
            }
        }
        order.setStatus(PurchaseOrderStatus.RECEIVED);
        purchaseOrderRepository.save(order);
        recordPurchaseOrderInvoice(order);
        return order;
    }

    private void recordPurchaseOrderInvoice(PurchaseOrder order) {
        VendorInvoice invoice = new VendorInvoice();
        invoice.setVendorName(order.getVendorName());
        invoice.setInvoiceNumber(order.getOrderNumber());
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setAmount(order.getTotalAmount());
        invoice.setStatus(InvoiceStatus.OPEN);
        accountsPayableService.recordVendorInvoice(invoice);
    }

    @Transactional
    public SalesOrder createSalesOrder(SalesOrder salesOrder) {
        return createSalesOrderInternal(salesOrder, null);
    }

    @Transactional
    public SalesOrder createSalesOrder(SalesOrder salesOrder, Company company) {
        return createSalesOrderInternal(salesOrder, company);
    }

    private SalesOrder createSalesOrderInternal(SalesOrder salesOrder, Company company) {
        Company effectiveCompany = resolveScopedCompany(company, salesOrder.getCompany(), extractSalesOrderProducts(salesOrder));
        salesOrder.setCompany(effectiveCompany);
        ensureUniqueSalesOrderNumber(salesOrder);
        BigDecimal total = BigDecimal.ZERO;
        if (salesOrder.getLines() != null) {
            for (SalesOrderLine line : salesOrder.getLines()) {
                if (line.getProduct() == null) {
                    throw new IllegalArgumentException("Sales order lines require a product");
                }
                ensureCompanyOwned(line.getProduct().getCompany(), effectiveCompany, "Sales order line product");
                line.setSalesOrder(salesOrder);
                line.setReservedQuantity(Optional.ofNullable(line.getReservedQuantity()).orElse(BigDecimal.ZERO));
                line.setFulfilledQuantity(Optional.ofNullable(line.getFulfilledQuantity()).orElse(BigDecimal.ZERO));
                BigDecimal lineTotal = line.getUnitPrice().multiply(line.getQuantity());
                total = total.add(lineTotal);
            }
        }
        salesOrder.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));
        return salesOrderRepository.save(salesOrder);
    }

    @Transactional
    public SalesOrder fulfillSalesOrder(Long salesOrderId, Warehouse warehouse) {
        return fulfillSalesOrderInternal(null, salesOrderId, warehouse, null);
    }

    @Transactional
    public SalesOrder fulfillSalesOrder(Long companyId, Long salesOrderId, Warehouse warehouse) {
        return fulfillSalesOrderInternal(companyId, salesOrderId, warehouse, null);
    }

    @Transactional
    public SalesOrder reserveSalesOrder(Long companyId, Long salesOrderId, Warehouse warehouse, String actor) {
        SalesOrder order = requireSalesOrder(companyId, salesOrderId);
        ensureSameCompany(order.getCompany(), warehouse != null ? warehouse.getCompany() : null,
                "Sales order and warehouse must belong to the same company");
        if (order.getStatus() == SalesOrderStatus.CANCELLED || order.getStatus() == SalesOrderStatus.FULFILLED) {
            throw new IllegalStateException("This sales order can no longer be reserved");
        }
        for (SalesOrderLine line : order.getLines()) {
            BigDecimal fulfilled = Optional.ofNullable(line.getFulfilledQuantity()).orElse(BigDecimal.ZERO);
            BigDecimal alreadyReserved = Optional.ofNullable(line.getReservedQuantity()).orElse(BigDecimal.ZERO);
            BigDecimal required = line.getQuantity().subtract(fulfilled).subtract(alreadyReserved);
            if (required.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal remaining = required;
            for (StockLevel level : stockLevelRepository.findAvailableForReservation(line.getProduct(), warehouse)) {
                BigDecimal allocatable = level.getAvailableQuantity().min(remaining);
                if (allocatable.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                level.setReservedQuantity(Optional.ofNullable(level.getReservedQuantity()).orElse(BigDecimal.ZERO).add(allocatable));
                stockLevelRepository.save(level);
                StockReservation reservation = new StockReservation();
                reservation.setCompany(order.getCompany());
                reservation.setSalesOrderLine(line);
                reservation.setStockLevel(level);
                reservation.setQuantity(allocatable);
                reservation.setCreatedBy(trimToNull(actor));
                stockReservationRepository.save(reservation);
                remaining = remaining.subtract(allocatable);
                if (remaining.compareTo(BigDecimal.ZERO) == 0) {
                    break;
                }
            }
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                throw new IllegalStateException("Insufficient available stock for " + line.getProduct().getSku()
                        + ": missing " + remaining.stripTrailingZeros().toPlainString());
            }
            line.setReservedQuantity(alreadyReserved.add(required));
        }
        order.setStatus(SalesOrderStatus.RESERVED);
        return salesOrderRepository.save(order);
    }

    @Transactional
    public SalesOrder fulfillSalesOrder(Long companyId, Long salesOrderId, Warehouse warehouse, String actor) {
        return fulfillSalesOrderInternal(companyId, salesOrderId, warehouse, actor);
    }

    private SalesOrder fulfillSalesOrderInternal(Long companyId, Long salesOrderId, Warehouse warehouse, String actor) {
        SalesOrder order = requireSalesOrder(companyId, salesOrderId);
        ensureCompanyMatches(companyId, order.getCompany(), "Sales order");
        ensureCompanyMatches(companyId, warehouse != null ? warehouse.getCompany() : null, "Warehouse");
        ensureSameCompany(order.getCompany(), warehouse != null ? warehouse.getCompany() : null,
                "Sales order and warehouse must belong to the same company");
        if (order.getStatus() == SalesOrderStatus.FULFILLED) {
            return order;
        }
        if (order.getStatus() == SalesOrderStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled sales orders cannot be fulfilled");
        }
        reserveSalesOrder(companyId, salesOrderId, warehouse, actor);
        order = requireSalesOrder(companyId, salesOrderId);
        if (order.getLines() != null) {
            for (SalesOrderLine line : order.getLines()) {
                List<StockReservation> reservations = stockReservationRepository
                        .findAllBySalesOrderLine_IdAndStatus(line.getId(), StockReservationStatus.ACTIVE);
                BigDecimal fulfilledNow = BigDecimal.ZERO;
                for (StockReservation reservation : reservations) {
                    StockLevel level = reservation.getStockLevel();
                    BigDecimal reserved = Optional.ofNullable(level.getReservedQuantity()).orElse(BigDecimal.ZERO);
                    if (reserved.compareTo(reservation.getQuantity()) < 0) {
                        throw new IllegalStateException("Reservation balance is inconsistent; fulfillment stopped");
                    }
                    level.setReservedQuantity(reserved.subtract(reservation.getQuantity()));
                    stockLevelRepository.save(level);
                    adjustStock(line.getProduct(), warehouse, level.getWarehouseBin(), reservation.getQuantity().negate(),
                            StockMovementType.ISSUE, order.getOrderNumber(), "Sales order fulfillment",
                            level.getLotNumber(), level.getSerialNumber(), level.getExpirationDate(),
                            level.getInventoryStatus(), actor, "SO-" + order.getId() + "-RES-" + reservation.getId());
                    reservation.setStatus(StockReservationStatus.CONSUMED);
                    stockReservationRepository.save(reservation);
                    fulfilledNow = fulfilledNow.add(reservation.getQuantity());
                }
                line.setReservedQuantity(BigDecimal.ZERO);
                line.setFulfilledQuantity(Optional.ofNullable(line.getFulfilledQuantity()).orElse(BigDecimal.ZERO).add(fulfilledNow));
            }
        }
        boolean fullyFulfilled = order.getLines().stream()
                .allMatch(line -> line.getOpenQuantity().compareTo(BigDecimal.ZERO) == 0);
        order.setStatus(fullyFulfilled ? SalesOrderStatus.FULFILLED : SalesOrderStatus.PARTIALLY_FULFILLED);
        order.setDueDate(LocalDate.now());
        return salesOrderRepository.save(order);
    }

    @Transactional
    public ProductionOrder saveProductionOrder(ProductionOrder productionOrder) {
        return saveProductionOrderInternal(productionOrder, null);
    }

    @Transactional
    public ProductionOrder saveProductionOrder(ProductionOrder productionOrder, Company company) {
        return saveProductionOrderInternal(productionOrder, company);
    }

    private ProductionOrder saveProductionOrderInternal(ProductionOrder productionOrder, Company company) {
        Company effectiveCompany = resolveScopedCompany(
                company,
                productionOrder.getCompany(),
                productionOrder.getProduct() != null ? productionOrder.getProduct().getCompany() : null
        );
        if (productionOrder.getProduct() == null) {
            throw new IllegalArgumentException("Production order requires a product");
        }
        ensureCompanyOwned(productionOrder.getProduct().getCompany(), effectiveCompany, "Production order product");
        productionOrder.setCompany(effectiveCompany);
        ensureUniqueProductionOrderNumber(productionOrder);
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
        return updateProductionOrderStatusInternal(null, productionOrderId, status, startDate, completionDate);
    }

    @Transactional
    public ProductionOrder updateProductionOrderStatus(Long companyId,
                                                       Long productionOrderId,
                                                       ProductionOrderStatus status,
                                                       LocalDate startDate,
                                                       LocalDate completionDate) {
        return updateProductionOrderStatusInternal(companyId, productionOrderId, status, startDate, completionDate);
    }

    private ProductionOrder updateProductionOrderStatusInternal(Long companyId,
                                                                Long productionOrderId,
                                                                ProductionOrderStatus status,
                                                                LocalDate startDate,
                                                                LocalDate completionDate) {
        ProductionOrder order = requireProductionOrder(companyId, productionOrderId);
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
        return logServiceRequestInternal(request, null);
    }

    @Transactional
    public ServiceRequest logServiceRequest(ServiceRequest request, Company company) {
        return logServiceRequestInternal(request, company);
    }

    private ServiceRequest logServiceRequestInternal(ServiceRequest request, Company company) {
        if (company != null) {
            request.setCompany(company);
        }
        if (request.getOpenedDate() == null) {
            request.setOpenedDate(LocalDate.now());
        }
        return serviceRequestRepository.save(request);
    }

    @Transactional
    public ServiceRequest updateServiceRequestStatus(Long serviceRequestId,
                                                     ServiceRequestStatus status,
                                                     LocalDate closedDate) {
        return updateServiceRequestStatusInternal(null, serviceRequestId, status, closedDate);
    }

    @Transactional
    public ServiceRequest updateServiceRequestStatus(Long companyId,
                                                     Long serviceRequestId,
                                                     ServiceRequestStatus status,
                                                     LocalDate closedDate) {
        return updateServiceRequestStatusInternal(companyId, serviceRequestId, status, closedDate);
    }

    private ServiceRequest updateServiceRequestStatusInternal(Long companyId,
                                                              Long serviceRequestId,
                                                              ServiceRequestStatus status,
                                                              LocalDate closedDate) {
        ServiceRequest request = requireServiceRequest(companyId, serviceRequestId);
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
    public Page<StockLevel> listStockLevels(Long companyId, Pageable pageable) {
        return stockLevelRepository.findAllByProduct_Company_Id(companyId, pageable);
    }


    @Transactional(readOnly = true)
    public Page<PurchaseOrder> listPurchaseOrders(Pageable pageable) {
        return purchaseOrderRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrder> listPurchaseOrders(Long companyId, Pageable pageable) {
        return purchaseOrderRepository.findAllByCompany_Id(companyId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<SalesOrder> listSalesOrders(Pageable pageable) {
        return salesOrderRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<SalesOrder> listSalesOrders(Long companyId, Pageable pageable) {
        return salesOrderRepository.findAllByCompany_Id(companyId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ProductionOrder> listProductionOrders(Pageable pageable) {
        return productionOrderRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<ProductionOrder> listProductionOrders(Long companyId, Pageable pageable) {
        return productionOrderRepository.findAllByCompany_Id(companyId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ServiceRequest> listServiceRequests(Pageable pageable) {
        return serviceRequestRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<ServiceRequest> listServiceRequests(Long companyId, Pageable pageable) {
        return serviceRequestRepository.findAllByCompany_Id(companyId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<CycleCount> listCycleCounts(Pageable pageable) {
        return cycleCountRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<CycleCount> listCycleCounts(Long companyId, Pageable pageable) {
        return cycleCountRepository.findAllByCompany_Id(companyId, pageable);
    }

    @Transactional
    public CycleCount createCycleCount(Product product, Warehouse warehouse, String requestedBy) {
        return createCycleCountInternal(null, product, warehouse, requestedBy);
    }

    @Transactional
    public CycleCount createCycleCount(Long companyId, Product product, Warehouse warehouse, String requestedBy) {
        return createCycleCountInternal(companyId, product, warehouse, requestedBy);
    }

    private CycleCount createCycleCountInternal(Long companyId, Product product, Warehouse warehouse, String requestedBy) {
        ensureCompanyMatches(companyId, product != null ? product.getCompany() : null, "Product");
        ensureCompanyMatches(companyId, warehouse != null ? warehouse.getCompany() : null, "Warehouse");
        ensureSameCompany(product != null ? product.getCompany() : null,
                warehouse != null ? warehouse.getCompany() : null,
                "Product and warehouse must belong to the same company");
        Optional<CycleCount> existing = cycleCountRepository.findFirstByProductAndWarehouseAndStatusInOrderByCreatedAtDesc(
                product,
                warehouse,
                List.of(CycleCountStatus.PLANNED, CycleCountStatus.APPROVAL_REQUIRED));
        if (existing.isPresent()) {
            return existing.get();
        }

        BigDecimal expectedQuantity = stockLevelRepository.findByProduct(product).stream()
                .filter(level -> level.getWarehouse() != null && warehouse.getId().equals(level.getWarehouse().getId()))
                .map(StockLevel::getQuantity)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CycleCount cycleCount = new CycleCount();
        cycleCount.setPlanNumber(nextCycleCountPlanNumber(companyId));
        cycleCount.setCompany(resolveScopedCompany(null,
                product != null ? product.getCompany() : null,
                warehouse != null ? warehouse.getCompany() : null));
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
        return submitCycleCountInternal(null, cycleCountId, countedQuantity, countedBy);
    }

    @Transactional
    public CycleCount submitCycleCount(Long companyId, Long cycleCountId, BigDecimal countedQuantity, String countedBy) {
        return submitCycleCountInternal(companyId, cycleCountId, countedQuantity, countedBy);
    }

    private CycleCount submitCycleCountInternal(Long companyId, Long cycleCountId, BigDecimal countedQuantity, String countedBy) {
        if (countedQuantity == null) {
            throw new IllegalArgumentException("Counted quantity is required");
        }
        if (countedQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Counted quantity cannot be negative");
        }

        CycleCount cycleCount = requireCycleCount(companyId, cycleCountId);
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
        return approveCycleCountInternal(null, cycleCountId, approvedBy);
    }

    @Transactional
    public CycleCount approveCycleCount(Long companyId, Long cycleCountId, String approvedBy) {
        return approveCycleCountInternal(companyId, cycleCountId, approvedBy);
    }

    private CycleCount approveCycleCountInternal(Long companyId, Long cycleCountId, String approvedBy) {
        CycleCount cycleCount = requireCycleCount(companyId, cycleCountId);
        if (cycleCount.getStatus() != CycleCountStatus.APPROVAL_REQUIRED) {
            return cycleCount;
        }
        BigDecimal expectedQuantity = Optional.ofNullable(cycleCount.getExpectedQuantity()).orElse(BigDecimal.ZERO);
        BigDecimal countedQuantity = Optional.ofNullable(cycleCount.getCountedQuantity()).orElse(expectedQuantity);
        BigDecimal delta = countedQuantity.subtract(expectedQuantity);

        if (delta.compareTo(BigDecimal.ZERO) != 0) {
            adjustWarehouseAggregate(cycleCount.getProduct(), cycleCount.getWarehouse(), delta,
                    "Cycle count " + cycleCount.getPlanNumber(), approvedBy);
        }

        cycleCount.setApprovedBy(approvedBy);
        cycleCount.setApprovedAt(LocalDateTime.now());
        cycleCount.setStatus(CycleCountStatus.COMPLETED);
        cycleCount.setExpectedQuantity(countedQuantity);
        return cycleCountRepository.save(cycleCount);
    }

    private void adjustWarehouseAggregate(Product product, Warehouse warehouse, BigDecimal delta,
                                          String reference, String actor) {
        if (delta.compareTo(BigDecimal.ZERO) > 0) {
            adjustStock(product, warehouse, null, delta, StockMovementType.ADJUSTMENT, reference,
                    "Approved cycle count variance", null, null, null, InventoryStatus.AVAILABLE, actor, null);
            return;
        }
        BigDecimal remaining = delta.abs();
        for (StockLevel level : stockLevelRepository.findAllIssuableForUpdate(product, warehouse)) {
            BigDecimal issue = level.getAvailableQuantity().min(remaining);
            if (issue.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            adjustStock(product, warehouse, level.getWarehouseBin(), issue.negate(), StockMovementType.ADJUSTMENT,
                    reference, "Approved cycle count variance", level.getLotNumber(), level.getSerialNumber(),
                    level.getExpirationDate(), level.getInventoryStatus(), actor, null);
            remaining = remaining.subtract(issue);
            if (remaining.compareTo(BigDecimal.ZERO) == 0) {
                return;
            }
        }
        throw new IllegalStateException("Cycle count variance exceeds unreserved warehouse stock");
    }

    @Transactional
    public AutoReplenishResponse autoReplenish(AutoReplenishRequest request) {
        return autoReplenishInternal(null, request);
    }

    @Transactional
    public AutoReplenishResponse autoReplenish(Long companyId, AutoReplenishRequest request) {
        return autoReplenishInternal(companyId, request);
    }

    private AutoReplenishResponse autoReplenishInternal(Long companyId, AutoReplenishRequest request) {
        ReplenishmentComputation computation = computeReplenishmentPlans(companyId, request);
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

            PurchaseOrder saved = createPurchaseOrder(order, resolveCompany(companyId, computation.candidates()));
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
        return previewReplenishmentInternal(null, request);
    }

    public ReplenishmentPreviewResponse previewReplenishment(Long companyId, AutoReplenishRequest request) {
        return previewReplenishmentInternal(companyId, request);
    }

    private ReplenishmentPreviewResponse previewReplenishmentInternal(Long companyId, AutoReplenishRequest request) {
        ReplenishmentComputation computation = computeReplenishmentPlans(companyId, request);
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
        return planWavePickingInternal(null, request);
    }

    @Transactional(readOnly = true)
    public WavePickResponse planWavePicking(Long companyId, PlanWavePickRequest request) {
        return planWavePickingInternal(companyId, request);
    }

    private WavePickResponse planWavePickingInternal(Long companyId, PlanWavePickRequest request) {
        int maxPerWave = Optional.ofNullable(request.getMaxOrdersPerWave()).orElse(6);
        maxPerWave = Math.min(Math.max(maxPerWave, 2), 25);
        boolean includeDrafts = Optional.ofNullable(request.getIncludeDrafts()).orElse(Boolean.FALSE);

        List<SalesOrder> candidateOrders;
        List<Long> ids = request.getSalesOrderIds();
        if (ids == null || ids.isEmpty()) {
            candidateOrders = companyId == null
                    ? new ArrayList<>(salesOrderRepository.findByStatus(SalesOrderStatus.CONFIRMED))
                    : new ArrayList<>(salesOrderRepository.findByStatusAndCompany_Id(SalesOrderStatus.CONFIRMED, companyId));
        } else {
            candidateOrders = companyId == null
                    ? salesOrderRepository.findAllById(ids)
                    : salesOrderRepository.findAllByIdInAndCompany_Id(ids, companyId);
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

    private List<Product> resolveProductsForReplenishment(Long companyId, AutoReplenishRequest request) {
        List<Long> productIds = request.getProductIds();
        Collection<Product> pool;
        if (productIds == null || productIds.isEmpty()) {
            pool = companyId == null ? productRepository.findAll() : productRepository.findAllByCompany_Id(companyId);
        } else if (companyId == null) {
            pool = productRepository.findAllById(productIds);
        } else {
            pool = productIds.stream()
                    .map(productId -> productRepository.findByIdAndCompany_Id(productId, companyId).orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
        return pool.stream()
                .sorted(Comparator.comparing(Product::getId, Comparator.nullsLast(Long::compareTo)))
                .collect(Collectors.toList());
    }

    private List<Product> resolveProductsForReplenishment(AutoReplenishRequest request) {
        return resolveProductsForReplenishment(null, request);
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
            forecast = warehouseIntelligenceService.forecastInventory(warehouseProductKey(product));
        } catch (RuntimeException ex) {
            forecast = null;
        }

        PredictiveReplenishmentResponse replenishmentAnalytics;
        try {
            replenishmentAnalytics = warehouseIntelligenceService.analyseReplenishment(warehouseProductKey(product), serviceLevel);
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
        sourcingRequest.setProductId(warehouseProductKey(product));
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

    private String nextCycleCountPlanNumber(Long companyId) {
        if (companyId == null) {
            return nextCycleCountPlanNumber();
        }
        long sequence = cycleCountRepository.countByCompany_Id(companyId) + 1;
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

    private PurchaseOrder findMatchingPurchaseOrder(Long companyId,
                                                    Set<String> detectedCodes,
                                                    String documentText,
                                                    List<PurchaseOrder> openOrders) {
        for (String code : detectedCodes) {
            Optional<PurchaseOrder> exact = companyId == null
                    ? purchaseOrderRepository.findByOrderNumberIgnoreCase(code)
                    : purchaseOrderRepository.findByOrderNumberIgnoreCaseAndCompany_Id(code, companyId);
            exact = exact
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

    private List<ReceivingPreviewResponse.ReceivingPreviewItem> detectPreviewItems(Long companyId,
                                                                                   String documentText,
                                                                                   Set<String> detectedCodes) {
        List<Product> products = (companyId == null
                ? productRepository.findAll()
                : productRepository.findAllByCompany_Id(companyId)).stream()
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
            Optional<Product> matchedProduct = companyId == null
                    ? productRepository.findBySkuIgnoreCase(code)
                    : productRepository.findBySkuIgnoreCaseAndCompany_Id(code, companyId);
            matchedProduct.ifPresent(product -> {
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

    private String trimToNull(String value) {
        String normalized = value == null ? null : value.trim();
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }

    private WarehouseBin ensureDefaultBin(Warehouse warehouse) {
        if (warehouse == null || warehouse.getId() == null) {
            throw new IllegalArgumentException("A persisted warehouse is required");
        }
        return warehouseBinRepository.findByWarehouse_IdAndCodeIgnoreCase(warehouse.getId(), WarehouseBin.DEFAULT_CODE)
                .orElseGet(() -> {
                    WarehouseBin bin = new WarehouseBin();
                    bin.setWarehouse(warehouse);
                    bin.setCode(WarehouseBin.DEFAULT_CODE);
                    bin.setName("Standardlagerplatz");
                    bin.setZone("STANDARD");
                    bin.setPickSequence(Integer.MAX_VALUE);
                    return warehouseBinRepository.saveAndFlush(bin);
                });
    }

    private String inventoryKey(WarehouseBin bin, String lotNumber, String serialNumber,
                                LocalDate expirationDate, InventoryStatus status) {
        return String.join("|",
                String.valueOf(bin.getId()),
                Optional.ofNullable(lotNumber).orElse("-"),
                Optional.ofNullable(serialNumber).orElse("-"),
                expirationDate == null ? "-" : expirationDate.toString(),
                status.name());
    }

    private Product requireProduct(Long companyId, Long productId) {
        return companyId == null
                ? productRepository.findById(productId).orElseThrow()
                : productRepository.findByIdAndCompany_Id(productId, companyId).orElseThrow();
    }

    private Warehouse requireWarehouse(Long companyId, Long warehouseId) {
        return companyId == null
                ? warehouseRepository.findById(warehouseId).orElseThrow()
                : warehouseRepository.findByIdAndCompany_Id(warehouseId, companyId).orElseThrow();
    }

    private WarehouseBin requireWarehouseBin(Long companyId, Long warehouseBinId) {
        return companyId == null
                ? warehouseBinRepository.findById(warehouseBinId).orElseThrow()
                : warehouseBinRepository.findByIdAndWarehouse_Company_Id(warehouseBinId, companyId).orElseThrow();
    }

    private PurchaseOrder requirePurchaseOrder(Long companyId, Long purchaseOrderId) {
        return companyId == null
                ? purchaseOrderRepository.findById(purchaseOrderId).orElseThrow()
                : purchaseOrderRepository.findByIdAndCompany_Id(purchaseOrderId, companyId).orElseThrow();
    }

    private SalesOrder requireSalesOrder(Long companyId, Long salesOrderId) {
        return companyId == null
                ? salesOrderRepository.findById(salesOrderId).orElseThrow()
                : salesOrderRepository.findByIdAndCompany_Id(salesOrderId, companyId).orElseThrow();
    }

    private ProductionOrder requireProductionOrder(Long companyId, Long productionOrderId) {
        return companyId == null
                ? productionOrderRepository.findById(productionOrderId).orElseThrow()
                : productionOrderRepository.findByIdAndCompany_Id(productionOrderId, companyId).orElseThrow();
    }

    private ServiceRequest requireServiceRequest(Long companyId, Long serviceRequestId) {
        return companyId == null
                ? serviceRequestRepository.findById(serviceRequestId).orElseThrow()
                : serviceRequestRepository.findByIdAndCompany_Id(serviceRequestId, companyId).orElseThrow();
    }

    private CycleCount requireCycleCount(Long companyId, Long cycleCountId) {
        return companyId == null
                ? cycleCountRepository.findById(cycleCountId).orElseThrow()
                : cycleCountRepository.findByIdAndCompany_Id(cycleCountId, companyId).orElseThrow();
    }

    private List<PurchaseOrder> loadPurchaseOrders(Long companyId) {
        return companyId == null
                ? purchaseOrderRepository.findAll()
                : purchaseOrderRepository.findAllByCompany_Id(companyId);
    }

    private List<Product> extractPurchaseOrderProducts(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null || purchaseOrder.getLines() == null) {
            return List.of();
        }
        return purchaseOrder.getLines().stream()
                .map(PurchaseOrderLine::getProduct)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private List<Product> extractSalesOrderProducts(SalesOrder salesOrder) {
        if (salesOrder == null || salesOrder.getLines() == null) {
            return List.of();
        }
        return salesOrder.getLines().stream()
                .map(SalesOrderLine::getProduct)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private Company resolveCompany(Long companyId, List<Product> candidates) {
        if (companyId == null) {
            return candidates.stream()
                    .map(Product::getCompany)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        return candidates.stream()
                .map(Product::getCompany)
                .filter(company -> company != null && companyId.equals(company.getId()))
                .findFirst()
                .orElse(null);
    }

    private Company resolveScopedCompany(Company preferredCompany, Company... candidates) {
        Company resolved = preferredCompany;
        for (Company candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            if (resolved == null) {
                resolved = candidate;
                continue;
            }
            ensureSameCompany(resolved, candidate, "Supply Chain payload mixes companies");
        }
        return resolved;
    }

    private Company resolveScopedCompany(Company preferredCompany,
                                         Company existingCompany,
                                         List<Product> candidateProducts) {
        Company resolved = resolveScopedCompany(preferredCompany, existingCompany);
        for (Product product : candidateProducts) {
            if (product != null) {
                resolved = resolveScopedCompany(resolved, product.getCompany());
            }
        }
        return resolved;
    }

    private void ensureUniqueProductSku(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("Product is required");
        }
        String sku = safeText(product.getSku()).trim();
        if (sku.isBlank()) {
            throw new IllegalArgumentException("Product SKU is required");
        }
        product.setSku(sku);
        Long companyId = product.getCompany() != null ? product.getCompany().getId() : null;
        Optional<Product> existing = companyId == null
                ? productRepository.findBySkuIgnoreCase(sku)
                : productRepository.findBySkuIgnoreCaseAndCompany_Id(sku, companyId);
        if (existing.isPresent() && !existing.get().getId().equals(product.getId())) {
            throw new IllegalArgumentException("Product SKU already exists for this company");
        }
    }

    private void ensureUniqueWarehouseCode(Warehouse warehouse) {
        if (warehouse == null) {
            throw new IllegalArgumentException("Warehouse is required");
        }
        String code = safeText(warehouse.getCode()).trim();
        if (code.isBlank()) {
            throw new IllegalArgumentException("Warehouse code is required");
        }
        warehouse.setCode(code);
        Long companyId = warehouse.getCompany() != null ? warehouse.getCompany().getId() : null;
        Optional<Warehouse> existing = companyId == null
                ? warehouseRepository.findByCode(code)
                : warehouseRepository.findByCodeAndCompany_Id(code, companyId);
        if (existing.isPresent() && !existing.get().getId().equals(warehouse.getId())) {
            throw new IllegalArgumentException("Warehouse code already exists for this company");
        }
    }

    private void ensureUniquePurchaseOrderNumber(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            throw new IllegalArgumentException("Purchase order is required");
        }
        String orderNumber = safeText(purchaseOrder.getOrderNumber()).trim();
        if (orderNumber.isBlank()) {
            throw new IllegalArgumentException("Purchase order number is required");
        }
        purchaseOrder.setOrderNumber(orderNumber);
        Long companyId = purchaseOrder.getCompany() != null ? purchaseOrder.getCompany().getId() : null;
        Optional<PurchaseOrder> existing = companyId == null
                ? purchaseOrderRepository.findByOrderNumberIgnoreCase(orderNumber)
                : purchaseOrderRepository.findByOrderNumberIgnoreCaseAndCompany_Id(orderNumber, companyId);
        if (existing.isPresent() && !existing.get().getId().equals(purchaseOrder.getId())) {
            throw new IllegalArgumentException("Purchase order number already exists for this company");
        }
    }

    private void ensureUniqueSalesOrderNumber(SalesOrder salesOrder) {
        if (salesOrder == null) {
            throw new IllegalArgumentException("Sales order is required");
        }
        String orderNumber = safeText(salesOrder.getOrderNumber()).trim();
        if (orderNumber.isBlank()) {
            throw new IllegalArgumentException("Sales order number is required");
        }
        salesOrder.setOrderNumber(orderNumber);
        Long companyId = salesOrder.getCompany() != null ? salesOrder.getCompany().getId() : null;
        Optional<SalesOrder> existing = companyId == null
                ? salesOrderRepository.findByOrderNumberIgnoreCase(orderNumber)
                : salesOrderRepository.findByOrderNumberIgnoreCaseAndCompany_Id(orderNumber, companyId);
        if (existing.isPresent() && !existing.get().getId().equals(salesOrder.getId())) {
            throw new IllegalArgumentException("Sales order number already exists for this company");
        }
    }

    private void ensureUniqueProductionOrderNumber(ProductionOrder productionOrder) {
        if (productionOrder == null) {
            throw new IllegalArgumentException("Production order is required");
        }
        String orderNumber = safeText(productionOrder.getOrderNumber()).trim();
        if (orderNumber.isBlank()) {
            throw new IllegalArgumentException("Production order number is required");
        }
        productionOrder.setOrderNumber(orderNumber);
        Long companyId = productionOrder.getCompany() != null ? productionOrder.getCompany().getId() : null;
        Optional<ProductionOrder> existing = companyId == null
                ? productionOrderRepository.findByOrderNumberIgnoreCase(orderNumber)
                : productionOrderRepository.findByOrderNumberIgnoreCaseAndCompany_Id(orderNumber, companyId);
        if (existing.isPresent() && !existing.get().getId().equals(productionOrder.getId())) {
            throw new IllegalArgumentException("Production order number already exists for this company");
        }
    }

    private void ensureCompanyOwned(Company actualCompany, Company expectedCompany, String entityLabel) {
        if (expectedCompany == null) {
            return;
        }
        if (!sameCompany(actualCompany, expectedCompany)) {
            throw new IllegalArgumentException(entityLabel + " does not belong to the active company");
        }
    }

    private void ensureCompanyMatches(Long companyId, Company company, String entityLabel) {
        if (companyId == null || company == null || company.getId() == null) {
            return;
        }
        if (!companyId.equals(company.getId())) {
            throw new IllegalArgumentException(entityLabel + " does not belong to the active company");
        }
    }

    private void ensureSameCompany(Company leftCompany, Company rightCompany, String message) {
        if (leftCompany == null || rightCompany == null) {
            return;
        }
        if (!sameCompany(leftCompany, rightCompany)) {
            throw new IllegalArgumentException(message);
        }
    }

    private boolean sameCompany(Company leftCompany, Company rightCompany) {
        if (leftCompany == null || rightCompany == null) {
            return leftCompany == rightCompany;
        }
        Long leftId = leftCompany.getId();
        Long rightId = rightCompany.getId();
        if (leftId != null || rightId != null) {
            return leftId != null && leftId.equals(rightId);
        }
        return leftCompany == rightCompany;
    }

    private String warehouseProductKey(Product product) {
        if (product == null) {
            return "";
        }
        String base = product.getSku() != null && !product.getSku().isBlank()
                ? product.getSku().trim()
                : "PRD-" + product.getId();
        if (product.getCompany() != null && product.getCompany().getId() != null) {
            return product.getCompany().getId() + "::" + base;
        }
        return base;
    }

    private String stripWarehouseKey(String value) {
        if (value == null) {
            return null;
        }
        int separator = value.indexOf("::");
        return separator >= 0 ? value.substring(separator + 2) : value;
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

    private ReplenishmentComputation computeReplenishmentPlans(Long companyId, AutoReplenishRequest request) {
        List<Product> candidates = resolveProductsForReplenishment(companyId, request);
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

    private ReplenishmentComputation computeReplenishmentPlans(AutoReplenishRequest request) {
        return computeReplenishmentPlans(null, request);
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
            if (line.getProduct() == null) {
                continue;
            }
            int units = toUnits(line.getQuantity());
            if (units <= 0) {
                continue;
            }
            String productKey = warehouseProductKey(line.getProduct());
            String zone = productZones.getOrDefault(productKey, "UNASSIGNED");
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
                if (line.getProduct() == null) {
                    continue;
                }
                int units = toUnits(line.getQuantity());
                if (units <= 0) {
                    continue;
                }
                lineCount++;
                orderUnits += units;
                String productKey = warehouseProductKey(line.getProduct());
                skuDemand.merge(productKey, units, Integer::sum);
                skuOrders.computeIfAbsent(productKey, key -> new HashSet<>()).add(order.getOrderNumber());
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
                .map(waypoint -> new WavePickStopDTO(stripWarehouseKey(waypoint.getLocationId()), stripWarehouseKey(waypoint.getProductId()),
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
