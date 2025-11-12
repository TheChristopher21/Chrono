package com.chrono.chrono.warehouse.service;

import com.chrono.chrono.entities.inventory.Product;
import com.chrono.chrono.entities.inventory.StockLevel;
import com.chrono.chrono.entities.inventory.Warehouse;
import com.chrono.chrono.repositories.inventory.ProductRepository;
import com.chrono.chrono.repositories.inventory.StockLevelRepository;
import com.chrono.chrono.repositories.inventory.WarehouseRepository;
import com.chrono.chrono.warehouse.dto.*;
import com.chrono.chrono.warehouse.model.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class WarehouseIntelligenceService {

    private static final double WALKING_SPEED_MS = 1.5;
    private static final Map<String, List<String>> CATEGORY_KEYWORDS = Map.of(
            "Wearables", List.of("glove", "wear", "hand", "sleeve", "vest"),
            "Robotics", List.of("bot", "robot", "arm", "automaton"),
            "IoT", List.of("sensor", "tag", "beacon", "rfid"),
            "Packaging", List.of("label", "box", "pack", "carton"),
            "Consumables", List.of("battery", "pad", "clean"),
            "Software", List.of("license", "suite", "subscription"));
    private static final List<BoxOption> BOX_CATALOG = List.of(
            new BoxOption("BOX-S", "Parcel Small 30x20x15", 30, 20, 15, 5),
            new BoxOption("BOX-M", "Parcel Medium 40x30x25", 40, 30, 25, 12),
            new BoxOption("BOX-L", "Parcel Large 60x40x40", 60, 40, 40, 25),
            new BoxOption("CRATE-XL", "Crate XL 80x60x60", 80, 60, 60, 50));

    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockLevelRepository stockLevelRepository;

    private final Map<String, WarehouseProduct> products = new ConcurrentHashMap<>();
    private final Map<String, WarehouseLocation> locations = new ConcurrentHashMap<>();
    private final List<InventoryItem> inventory = new ArrayList<>();
    private final Map<String, List<SensorReading>> sensorReadings = new ConcurrentHashMap<>();
    private final Map<String, SupplierProfile> suppliers = new HashMap<>();
    private final List<MovementLogEntry> movementLedger = new ArrayList<>();
    private final List<ReturnCase> returnCases = new ArrayList<>();

    private final Map<String, CategoryStatistics> categoryStatistics = new ConcurrentHashMap<>();

    public WarehouseIntelligenceService() {
        this(null, null, null);
    }

    public WarehouseIntelligenceService(ProductRepository productRepository,
                                        WarehouseRepository warehouseRepository,
                                        StockLevelRepository stockLevelRepository) {
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.stockLevelRepository = stockLevelRepository;
        seedDemoData();
    }

    @PostConstruct
    public void hydrateFromDatabase() {
        refreshFromDatabase();
    }

    private void seedDemoData() {
        WarehouseProduct chronoBot = new WarehouseProduct("SKU-AR-01", "Chrono Vision Bot");
        chronoBot.setCategory("Robotics");
        chronoBot.setWeightKg(18.5);
        chronoBot.setVolumeCubicM(0.35);
        chronoBot.setCostPrice(new BigDecimal("1299.00"));
        chronoBot.setSalesPrice(new BigDecimal("1799.00"));
        chronoBot.setDemandSegment("A");
        chronoBot.addAttribute("AR-ready");
        chronoBot.addAttribute("AI-optimised");
        products.put(chronoBot.getId(), chronoBot);
        updateCategoryStatistics(chronoBot.getCategory(), chronoBot.getWeightKg(), chronoBot.getVolumeCubicM());

        WarehouseProduct smartGlove = new WarehouseProduct("SKU-AR-GLV", "Smart Picking Glove");
        smartGlove.setCategory("Wearables");
        smartGlove.setWeightKg(0.4);
        smartGlove.setVolumeCubicM(0.02);
        smartGlove.setCostPrice(new BigDecimal("149.00"));
        smartGlove.setSalesPrice(new BigDecimal("259.00"));
        smartGlove.setDemandSegment("B");
        products.put(smartGlove.getId(), smartGlove);
        updateCategoryStatistics(smartGlove.getCategory(), smartGlove.getWeightKg(), smartGlove.getVolumeCubicM());

        locations.put("A-01-01", new WarehouseLocation("A-01-01", "A", 2, 4, 1, 80));
        locations.put("A-02-03", new WarehouseLocation("A-02-03", "A", 5, 8, 1, 50));
        locations.put("B-01-02", new WarehouseLocation("B-01-02", "B", 10, 3, 2, 70));
        locations.put("C-03-05", new WarehouseLocation("C-03-05", "C", 15, 12, 3, 100));

        InventoryItem item1 = new InventoryItem();
        item1.setProductId(chronoBot.getId());
        item1.setLocationId("A-01-01");
        item1.setQuantity(24);
        item1.setBatchNumber("LOT-2025-01");
        item1.setLifecycleStatus("available");
        item1.setLastMovement(Instant.now().minusSeconds(3600));
        inventory.add(item1);
        adjustLocationOccupancy(item1.getLocationId(), item1.getQuantity());

        InventoryItem item2 = new InventoryItem();
        item2.setProductId(smartGlove.getId());
        item2.setLocationId("B-01-02");
        item2.setQuantity(140);
        item2.setLifecycleStatus("available");
        item2.setLastMovement(Instant.now().minusSeconds(7200));
        inventory.add(item2);
        adjustLocationOccupancy(item2.getLocationId(), item2.getQuantity());

        suppliers.put("SUP-001", new SupplierProfile("SUP-001", "Quantum Logistics", 0.95, 0.88, 0.72, 3));
        suppliers.put("SUP-002", new SupplierProfile("SUP-002", "EcoFreight", 0.89, 0.92, 0.94, 5));
        suppliers.put("SUP-003", new SupplierProfile("SUP-003", "Swiss Precision Supply", 0.91, 0.97, 0.81, 2));

        returnCases.add(new ReturnCase(UUID.randomUUID().toString(), smartGlove.getId(),
                "Customer return - faulty sensor", "inspection", Instant.now().minusSeconds(86400)));
    }

    public void refreshFromDatabase() {
        if (productRepository == null || warehouseRepository == null || stockLevelRepository == null) {
            return;
        }
        productRepository.findAll().forEach(this::mapProductEntity);
        warehouseRepository.findAll().forEach(this::mapWarehouseEntity);

        List<StockLevel> levels = stockLevelRepository.findAll();
        Set<String> touchedLocations = levels.stream()
                .map(StockLevel::getWarehouse)
                .filter(Objects::nonNull)
                .map(this::mapWarehouseEntity)
                .map(WarehouseLocation::getId)
                .collect(Collectors.toSet());

        if (!touchedLocations.isEmpty()) {
            inventory.removeIf(item -> touchedLocations.contains(item.getLocationId()));
            touchedLocations.forEach(locationId -> {
                WarehouseLocation location = locations.get(locationId);
                if (location != null) {
                    location.setOccupied(0);
                }
            });
        }

        for (StockLevel level : levels) {
            Product product = level.getProduct();
            Warehouse warehouse = level.getWarehouse();
            if (product == null || warehouse == null) {
                continue;
            }
            WarehouseProduct mappedProduct = mapProductEntity(product);
            WarehouseLocation location = mapWarehouseEntity(warehouse);
            int absolute = toInt(level.getQuantity());
            updateInventoryEntry(mappedProduct, location, absolute, absolute);
        }

        rebuildCategoryStatistics();
        products.values().forEach(product -> product.setDemandSegment(classifyDemandSegment(product)));
    }

    public void registerProduct(Product product) {
        if (product == null) {
            return;
        }
        WarehouseProduct mapped = mapProductEntity(product);
        rebuildCategoryStatistics();
        mapped.setDemandSegment(classifyDemandSegment(mapped));
    }

    public void registerWarehouse(Warehouse warehouse) {
        if (warehouse == null) {
            return;
        }
        WarehouseLocation location = mapWarehouseEntity(warehouse);
        if (inventory.stream().noneMatch(item -> location.getId().equals(item.getLocationId()))) {
            location.setOccupied(0);
        }
    }

    public void applyStockMovement(Product product,
                                   Warehouse warehouse,
                                   BigDecimal delta,
                                   BigDecimal resultingQuantity) {
        if (product == null || warehouse == null) {
            return;
        }
        WarehouseProduct mappedProduct = mapProductEntity(product);
        WarehouseLocation location = mapWarehouseEntity(warehouse);
        int change = toInt(delta);
        int absolute = resultingQuantity == null ? -1 : toInt(resultingQuantity);
        updateInventoryEntry(mappedProduct, location, change, absolute);
        if (change != 0) {
            recordLedgerSnapshot(mappedProduct, location.getId(), change);
        }
        mappedProduct.setDemandSegment(classifyDemandSegment(mappedProduct));
    }

    private WarehouseProduct mapProductEntity(Product entity) {
        if (entity == null) {
            return null;
        }
        String productId = resolveProductId(entity);
        WarehouseProduct product = products.computeIfAbsent(productId,
                key -> new WarehouseProduct(key, entity.getName()));
        product.setName(entity.getName());
        if (entity.getUnitCost() != null) {
            product.setCostPrice(entity.getUnitCost());
        }
        if (entity.getUnitPrice() != null) {
            product.setSalesPrice(entity.getUnitPrice());
        }
        String category = resolveCategory(entity, product);
        product.setCategory(category);
        double weight = resolveWeight(entity, product);
        product.setWeightKg(weight);
        product.setVolumeCubicM(resolveVolume(entity, product, weight));
        product.setLastUpdated(Instant.now());
        applyAttributes(product, entity);
        return product;
    }

    private WarehouseLocation mapWarehouseEntity(Warehouse entity) {
        if (entity == null) {
            return null;
        }
        String locationId = resolveWarehouseId(entity);
        WarehouseLocation location = locations.computeIfAbsent(locationId, key -> {
            double[] coords = deriveCoordinates(key);
            return new WarehouseLocation(key, determineZone(key), coords[0], coords[1], coords[2],
                    estimateCapacity(entity));
        });
        location.setZone(determineZone(locationId));
        double[] coords = deriveCoordinates(locationId);
        location.setX(coords[0]);
        location.setY(coords[1]);
        location.setZ(coords[2]);
        location.setCapacity(estimateCapacity(entity));
        return location;
    }

    private void updateInventoryEntry(WarehouseProduct product,
                                      WarehouseLocation location,
                                      int delta,
                                      int absoluteQuantity) {
        InventoryItem entry = inventory.stream()
                .filter(item -> item.getProductId().equals(product.getId())
                        && item.getLocationId().equals(location.getId()))
                .findFirst()
                .orElse(null);
        if (entry == null) {
            entry = new InventoryItem();
            entry.setProductId(product.getId());
            entry.setLocationId(location.getId());
            entry.setLifecycleStatus("available");
            inventory.add(entry);
        }
        int previous = entry.getQuantity();
        int updated = absoluteQuantity >= 0 ? Math.max(0, absoluteQuantity)
                : Math.max(0, previous + delta);
        entry.setQuantity(updated);
        entry.setLastMovement(Instant.now());
        adjustLocationOccupancy(location.getId(), updated - previous);
    }

    private void recordLedgerSnapshot(WarehouseProduct product, String locationId, int change) {
        MovementLogEntry entry = new MovementLogEntry();
        entry.setId(UUID.randomUUID().toString());
        entry.setProductId(product.getId());
        if (change > 0) {
            entry.setFromLocation(null);
            entry.setToLocation(locationId);
            entry.setQuantity(change);
        } else {
            entry.setFromLocation(locationId);
            entry.setToLocation(null);
            entry.setQuantity(Math.abs(change));
        }
        entry.setTimestamp(Instant.now());
        entry.setHash(hashPayload(product.getId() + entry.getFromLocation()
                + entry.getToLocation() + entry.getQuantity() + entry.getTimestamp()));
        movementLedger.add(entry);
    }

    private String resolveProductId(Product entity) {
        if (entity.getSku() != null && !entity.getSku().isBlank()) {
            return entity.getSku().trim();
        }
        return "PRD-" + entity.getId();
    }

    private String resolveWarehouseId(Warehouse entity) {
        if (entity.getCode() != null && !entity.getCode().isBlank()) {
            return entity.getCode().trim();
        }
        return "WH-" + entity.getId();
    }

    private String resolveCategory(Product entity, WarehouseProduct current) {
        String reference = (Optional.ofNullable(entity.getName()).orElse("") + " "
                + Optional.ofNullable(entity.getDescription()).orElse(""))
                .toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            if (entry.getValue().stream().anyMatch(reference::contains)) {
                return entry.getKey();
            }
        }
        return Optional.ofNullable(current.getCategory()).orElse("General");
    }

    private double resolveWeight(Product entity, WarehouseProduct current) {
        if (current.getWeightKg() > 0) {
            return current.getWeightKg();
        }
        BigDecimal reference = Optional.ofNullable(entity.getUnitCost())
                .orElse(entity.getUnitPrice());
        double base = reference == null ? 1.0 : Math.max(0.2, Math.min(reference.doubleValue() / 120.0, 35));
        String uom = entity.getUnitOfMeasure();
        if (uom != null) {
            String normalized = uom.toLowerCase(Locale.ROOT);
            if (normalized.contains("kg")) {
                return Math.max(0.5, base);
            }
            if (normalized.contains("g")) {
                return Math.max(0.2, base / 4);
            }
        }
        return base;
    }

    private double resolveVolume(Product entity, WarehouseProduct current, double weight) {
        if (current.getVolumeCubicM() > 0) {
            return current.getVolumeCubicM();
        }
        String description = Optional.ofNullable(entity.getDescription()).orElse("")
                .toLowerCase(Locale.ROOT);
        if (description.contains("pallet")) {
            return 0.9;
        }
        double base = weight / 250.0;
        return Math.max(0.01, Math.min(base, 1.5));
    }

    private void applyAttributes(WarehouseProduct product, Product entity) {
        if (entity.getUnitOfMeasure() != null && !entity.getUnitOfMeasure().isBlank()) {
            String attribute = "UoM: " + entity.getUnitOfMeasure();
            if (product.getAttributes().stream().noneMatch(attribute::equals)) {
                product.addAttribute(attribute);
            }
        }
        if (entity.getDescription() != null && !entity.getDescription().isBlank()) {
            String snippet = entity.getDescription().strip();
            if (snippet.length() > 64) {
                snippet = snippet.substring(0, 64) + "…";
            }
            String attribute = "Desc: " + snippet;
            if (product.getAttributes().stream().noneMatch(attribute::equals)) {
                product.addAttribute(attribute);
            }
        }
    }

    private String determineZone(String locationId) {
        if (locationId == null || locationId.isBlank()) {
            return "A";
        }
        char first = Character.toUpperCase(locationId.charAt(0));
        if (first >= 'A' && first <= 'Z') {
            return String.valueOf(first);
        }
        return "A";
    }

    private double[] deriveCoordinates(String locationId) {
        int hash = Math.abs(locationId.hashCode());
        double x = 2 + (hash % 150) / 10.0;
        double y = 2 + ((hash / 150) % 150) / 10.0;
        double z = ((hash / 22500) % 3) + 1;
        return new double[]{Math.round(x * 10.0) / 10.0,
                Math.round(y * 10.0) / 10.0,
                Math.round(z * 10.0) / 10.0};
    }

    private int estimateCapacity(Warehouse warehouse) {
        String base = Optional.ofNullable(warehouse.getLocation()).orElse(warehouse.getName());
        int hash = Math.abs(base == null ? 0 : base.hashCode());
        return 80 + (hash % 140);
    }

    private void rebuildCategoryStatistics() {
        categoryStatistics.clear();
        products.values().forEach(product ->
                updateCategoryStatistics(product.getCategory(), product.getWeightKg(), product.getVolumeCubicM()));
    }

    private int toInt(BigDecimal value) {
        if (value == null) {
            return 0;
        }
        return value.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    public List<ProductResponse> listProducts() {
        return products.values().stream()
                .sorted(Comparator.comparing(WarehouseProduct::getName))
                .map(product -> new ProductResponse(product.getId(), product.getName(), product.getCategory(),
                        product.getWeightKg(), product.getVolumeCubicM(), product.getCostPrice(),
                        product.getSalesPrice(), product.getDemandSegment(), product.getLastUpdated(),
                        List.copyOf(product.getAttributes())))
                .toList();
    }

    public ProductResponse upsertProduct(ProductRequest request) {
        String id = Optional.ofNullable(request.getId()).orElse("SKU-" + UUID.randomUUID());
        WarehouseProduct product = products.computeIfAbsent(id, key -> new WarehouseProduct(key, request.getName()));
        if (request.getName() != null) {
            product.setName(request.getName());
        }

        String category = Optional.ofNullable(request.getCategory()).orElseGet(() ->
                inferCategoryFromName(Optional.ofNullable(request.getName()).orElse(product.getName())));
        product.setCategory(category);

        double weight = Optional.ofNullable(request.getWeightKg()).orElseGet(() ->
                product.getWeightKg() == 0 ? estimateWeightFromCategory(category) : product.getWeightKg());
        product.setWeightKg(weight);

        double volume = Optional.ofNullable(request.getVolumeCubicM()).orElse(Math.max(0.01, weight * 0.02));
        product.setVolumeCubicM(volume);

        if (request.getCostPrice() != null) {
            product.setCostPrice(request.getCostPrice());
        } else if (product.getCostPrice() == null) {
            product.setCostPrice(BigDecimal.valueOf(weight * 10));
        }

        BigDecimal dynamicSalesPrice = calculateDynamicPrice(product);
        product.setSalesPrice(dynamicSalesPrice);
        product.setDemandSegment(classifyDemandSegment(product));
        product.setLastUpdated(Instant.now());

        if (request.getAttributes() != null) {
            request.getAttributes().forEach(product::addAttribute);
        }

        updateCategoryStatistics(category, weight, volume);

        return new ProductResponse(product.getId(), product.getName(), product.getCategory(), product.getWeightKg(),
                product.getVolumeCubicM(), product.getCostPrice(), product.getSalesPrice(),
                product.getDemandSegment(), product.getLastUpdated(), List.copyOf(product.getAttributes()));
    }

    private String inferCategoryFromName(String name) {
        if (name == null || name.isBlank()) {
            return "General";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        Map<String, Integer> scores = new HashMap<>();
        CATEGORY_KEYWORDS.forEach((category, keywords) -> {
            int score = 0;
            for (String keyword : keywords) {
                if (lower.contains(keyword)) {
                    score += 2;
                }
            }
            if (score > 0) {
                scores.put(category, score);
            }
        });
        if (!scores.isEmpty()) {
            return scores.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("General");
        }
        return products.values().stream()
                .filter(product -> product.getName() != null)
                .filter(product -> similarity(product.getName(), name) >= 0.6)
                .map(WarehouseProduct::getCategory)
                .findFirst()
                .orElse("General");
    }

    private double estimateWeightFromCategory(String category) {
        CategoryStatistics stats = categoryStatistics.get(category.toLowerCase(Locale.ROOT));
        if (stats != null && stats.getAverageWeight() > 0) {
            return stats.getAverageWeight();
        }
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "robotics" -> 14.5;
            case "wearables" -> 0.75;
            case "iot" -> 1.4;
            case "packaging" -> 0.35;
            case "consumables" -> 0.5;
            case "software" -> 0.1;
            default -> 2.8;
        };
    }

    private BigDecimal calculateDynamicPrice(WarehouseProduct product) {
        BigDecimal baseCost = Optional.ofNullable(product.getCostPrice()).orElse(BigDecimal.valueOf(10));
        double demandFactor = switch (Optional.ofNullable(product.getDemandSegment()).orElse("B")) {
            case "A" -> 1.35;
            case "C" -> 1.12;
            default -> 1.22;
        };
        double weightFactor = Math.max(0.9, Math.min(1.1, product.getWeightKg() / 10));
        double volatility = 0.98 + ThreadLocalRandom.current().nextDouble(0.05);
        return baseCost.multiply(BigDecimal.valueOf(demandFactor * weightFactor * volatility))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void updateCategoryStatistics(String category, double weight, double volume) {
        if (category == null) {
            return;
        }
        String key = category.toLowerCase(Locale.ROOT);
        categoryStatistics.compute(key, (ignored, stats) -> {
            CategoryStatistics updated = stats == null ? new CategoryStatistics() : stats;
            updated.observe(weight, volume);
            return updated;
        });
    }

    private double similarity(String left, String right) {
        String[] leftTokens = left.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        String[] rightTokens = right.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        Set<String> leftSet = new HashSet<>();
        Set<String> rightSet = new HashSet<>();
        for (String token : leftTokens) {
            if (!token.isBlank()) {
                leftSet.add(token);
            }
        }
        for (String token : rightTokens) {
            if (!token.isBlank()) {
                rightSet.add(token);
            }
        }
        if (leftSet.isEmpty() || rightSet.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(leftSet);
        intersection.retainAll(rightSet);
        Set<String> union = new HashSet<>(leftSet);
        union.addAll(rightSet);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    private BigDecimal detectSupplierTolerance(String purchaseOrderId) {
        if (purchaseOrderId == null) {
            return BigDecimal.valueOf(0.01);
        }
        return suppliers.entrySet().stream()
                .filter(entry -> purchaseOrderId.toLowerCase(Locale.ROOT)
                        .contains(entry.getKey().toLowerCase(Locale.ROOT)))
                .map(Map.Entry::getValue)
                .findFirst()
                .map(profile -> {
                    double reliabilityPenalty = Math.max(0, 1 - profile.getReliabilityScore());
                    double baseTolerance = 0.0075 + reliabilityPenalty * 0.03;
                    return BigDecimal.valueOf(baseTolerance);
                })
                .orElse(BigDecimal.valueOf(0.015));
    }

    private String classifyDemandSegment(WarehouseProduct product) {
        double velocity = inventory.stream()
                .filter(item -> item.getProductId().equals(product.getId()))
                .mapToDouble(item -> item.getQuantity() / Math.max(1, item.getLastMovement() == null ? 1 : 7))
                .average()
                .orElse(5);
        if (velocity > 30) {
            return "A";
        }
        if (velocity > 10) {
            return "B";
        }
        return "C";
    }

    public List<WarehouseLocation> listLocations() {
        return locations.values().stream()
                .sorted(Comparator.comparing(WarehouseLocation::getZone))
                .toList();
    }

    public List<InventoryItem> listInventory() {
        return List.copyOf(inventory);
    }

    public SmartSlottingResponse recommendSlot(SmartSlottingRequest request) {
        WarehouseLocation bestLocation = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        StringBuilder reason = new StringBuilder();
        for (WarehouseLocation location : locations.values()) {
            if (location.isBlocked() || location.getCapacity() <= location.getOccupied()) {
                continue;
            }
            double freeRatio = 1 - location.occupancyRate();
            double zoneScore = request.getZonePreference() != null &&
                    request.getZonePreference().equalsIgnoreCase(location.getZone()) ? 1.2 : 1.0;
            double weightScore = request.getWeightKg() > 10 && location.getZone().equalsIgnoreCase("C") ? 1.1 : 1.0;
            double velocityScore = request.getExpectedTurnoverDays() <= 7 &&
                    location.getZone().equalsIgnoreCase("A") ? 1.3 : 1.0;
            double travelTime = Math.max(estimateTravelTime(location), 1);
            double timeScore = 1 / travelTime;
            double score = freeRatio * 0.45 + zoneScore * 0.15 + weightScore * 0.1 + velocityScore * 0.1 + timeScore * 0.2;
            if (score > bestScore) {
                bestScore = score;
                bestLocation = location;
            }
        }
        if (bestLocation == null) {
            throw new IllegalStateException("No available locations for smart slotting suggestion");
        }
        reason.append("Free capacity: ")
                .append(String.format(Locale.ROOT, "%.0f%%", (1 - bestLocation.occupancyRate()) * 100))
                .append(", Zone: ").append(bestLocation.getZone());
        return new SmartSlottingResponse(bestLocation.getId(), Math.min(0.99, bestScore),
                estimateTravelTime(bestLocation), reason.toString());
    }

    private double estimateTravelTime(WarehouseLocation location) {
        double pathLength = aStarDistance(location);
        return Math.round((pathLength / WALKING_SPEED_MS) * 10.0) / 10.0;
    }

    public PredictiveInventoryResponse forecastInventory(String productId) {
        int current = inventory.stream()
                .filter(item -> item.getProductId().equals(productId))
                .mapToInt(InventoryItem::getQuantity)
                .sum();
        Map<LocalDate, Integer> forecast = generateForecast(productId, current);
        boolean stockOutRisk = forecast.values().stream().anyMatch(value -> value < calculateSafetyStock(productId));
        boolean overstockRisk = forecast.values().stream().anyMatch(value -> value > current * 1.6);
        return new PredictiveInventoryResponse(productId, forecast, stockOutRisk, overstockRisk);
    }

    public SensorReading recordSensorReading(SensorReadingRequest request) {
        SensorReading reading = new SensorReading(request.getLocationId(), request.getTemperature(),
                request.getHumidity(), request.getWeight(), Instant.now());
        sensorReadings.computeIfAbsent(request.getLocationId(), key -> new ArrayList<>()).add(reading);
        return reading;
    }

    public List<SensorReading> getSensorReadings(String locationId) {
        return sensorReadings.getOrDefault(locationId, List.of());
    }

    public MovementLogEntry recordMovement(String productId, String fromLocation, String toLocation, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive for a movement");
        }
        WarehouseProduct product = Optional.ofNullable(products.get(productId))
                .orElseThrow(() -> new IllegalArgumentException("Unknown product: " + productId));
        Instant now = Instant.now();
        String source = normalizeLocationId(fromLocation);
        String destination = normalizeLocationId(toLocation);
        adjustInventoryOnMovement(product, source, destination, quantity, now);
        MovementLogEntry entry = new MovementLogEntry(UUID.randomUUID().toString(), productId,
                source, destination, quantity, now, "");
        String payload = productId + source + destination + quantity + entry.getTimestamp();
        entry.setHash(hashPayload(payload));
        movementLedger.add(entry);
        return entry;
    }

    private String hashPayload(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(payload.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Hash algorithm missing", e);
        }
    }

    public List<MovementLogEntry> getMovementLedger() {
        return List.copyOf(movementLedger);
    }

    public SmartSourcingResponse selectSupplier(SmartSourcingRequest request) {
        List<SmartSourcingResponse.SupplierScore> scores = suppliers.values().stream()
                .map(profile -> new SmartSourcingResponse.SupplierScore(profile.getId(), profile.getName(),
                        scoreSupplier(profile, request.getPreferences()), profile.getLeadTimeDays()))
                .sorted(Comparator.comparing(SmartSourcingResponse.SupplierScore::getScore).reversed())
                .toList();
        SmartSourcingResponse.SupplierScore winner = scores.isEmpty() ? null : scores.get(0);
        return new SmartSourcingResponse(request.getProductId(), request.getQuantity(), winner, scores);
    }

    private double scoreSupplier(SupplierProfile profile, List<SmartSourcingRequest.SupplierPreference> preferences) {
        if (preferences == null || preferences.isEmpty()) {
            return profile.getPriceScore() * 0.4 + profile.getReliabilityScore() * 0.4
                    + profile.getSustainabilityScore() * 0.2;
        }
        return preferences.stream()
                .filter(pref -> pref.getSupplierId().equals(profile.getId()))
                .findFirst()
                .map(pref -> profile.getPriceScore() * pref.getWeightPrice()
                        + profile.getReliabilityScore() * pref.getWeightReliability()
                        + profile.getSustainabilityScore() * pref.getWeightSustainability())
                .orElse(profile.getPriceScore());
    }

    public AccountingMatchResponse reconcileAccounting(AccountingMatchRequest request) {
        BigDecimal ordered = Optional.ofNullable(request.getOrderedAmount()).orElse(BigDecimal.ZERO);
        BigDecimal received = Optional.ofNullable(request.getReceivedAmount()).orElse(BigDecimal.ZERO);
        BigDecimal invoiced = Optional.ofNullable(request.getInvoicedAmount()).orElse(BigDecimal.ZERO);
        BigDecimal maxReference = received.max(invoiced);
        BigDecimal deviation = ordered.subtract(maxReference).abs();

        BigDecimal supplierTolerance = detectSupplierTolerance(request.getPurchaseOrderId());
        BigDecimal quantityTolerance = ordered.multiply(supplierTolerance.max(BigDecimal.valueOf(0.01)));
        BigDecimal priceMismatch = received.subtract(invoiced).abs();

        boolean freightDetected = invoiced.compareTo(received) > 0
                && invoiced.subtract(received).compareTo(ordered.multiply(BigDecimal.valueOf(0.05))) <= 0;

        boolean quantityOk = deviation.compareTo(quantityTolerance) <= 0;
        boolean priceOk = priceMismatch.compareTo(ordered.multiply(BigDecimal.valueOf(0.02))) <= 0;
        boolean autoApprove = (quantityOk && priceOk) || (freightDetected && quantityOk);

        StringBuilder message = new StringBuilder();
        if (autoApprove) {
            message.append("Automatische Freigabe erteilt");
            if (freightDetected) {
                message.append(" – Differenz als Frachtkosten verbucht");
            }
        } else {
            message.append("Manuelle Prüfung notwendig: ");
            if (!quantityOk) {
                message.append("Mengendifferenz ");
            }
            if (!priceOk) {
                if (!quantityOk) {
                    message.append("& ");
                }
                message.append("Preisabweichung");
            }
        }

        return new AccountingMatchResponse(request.getPurchaseOrderId(), autoApprove,
                deviation.setScale(2, RoundingMode.HALF_UP), message.toString());
    }

    public MobileInboundResponse guideInbound(MobileInboundRequest request) {
        SmartSlottingResponse slotting = recommendSlot(new SmartSlottingRequest() {{
            setProductId(request.getProductId());
            setWeightKg(inferWeight(request.getProductId()));
            setVolumeCubicM(estimateVolume(request.getProductId()));
            setZonePreference("A");
            setExpectedTurnoverDays(5);
        }});
        List<MobileInboundResponse.NavigationStep> steps = List.of(
                new MobileInboundResponse.NavigationStep(request.getDockLocationId(), "Main-Aisle",
                        distanceBetween(request.getDockLocationId(), "Main-Aisle")),
                new MobileInboundResponse.NavigationStep("Main-Aisle", slotting.getLocationId(),
                        distanceBetween("Main-Aisle", slotting.getLocationId()))
        );
        return new MobileInboundResponse(slotting.getLocationId(), steps);
    }

    private double inferWeight(String productId) {
        return Optional.ofNullable(products.get(productId)).map(WarehouseProduct::getWeightKg).orElse(5.0);
    }

    private double estimateVolume(String productId) {
        return Optional.ofNullable(products.get(productId)).map(WarehouseProduct::getVolumeCubicM).orElse(0.2);
    }

    private double distanceBetween(String from, String to) {
        double[] origin = coordinatesForLocation(from);
        double[] destination = coordinatesForLocation(to);
        double dx = origin[0] - destination[0];
        double dy = origin[1] - destination[1];
        double dz = origin[2] - destination[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private double[] coordinatesForLocation(String id) {
        String normalized = normalizeLocationId(id);
        if (normalized == null || normalized.isBlank()) {
            return new double[]{0, 0, 0};
        }
        WarehouseLocation location = locations.get(normalized);
        if (location != null) {
            return new double[]{location.getX(), location.getY(), location.getZ()};
        }
        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "dock-1" -> new double[]{-2, 0, 0};
            case "main-aisle" -> new double[]{0, 5, 0};
            case "packing" -> new double[]{-5, 3, 0};
            default -> new double[]{0, 0, 0};
        };
    }

    private double euclideanDistance(double[] from, double[] to) {
        double dx = from[0] - to[0];
        double dy = from[1] - to[1];
        double dz = from[2] - to[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public PickRouteResponse planPickRoute(PickRouteRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return new PickRouteResponse(List.of(), 0, 0);
        }

        Map<String, Integer> requiredQuantities = new HashMap<>();
        for (PickRouteRequest.PickItem item : request.getItems()) {
            if (item.getProductId() == null) {
                continue;
            }
            requiredQuantities.merge(item.getProductId(), Math.max(0, item.getQuantity()), Integer::sum);
        }

        List<RouteNode> nodes = new ArrayList<>();
        requiredQuantities.forEach((productId, quantity) -> {
            int remaining = quantity;
            List<InventoryItem> candidates = inventory.stream()
                    .filter(inv -> inv.getProductId().equals(productId))
                    .sorted(Comparator.comparingInt(InventoryItem::getQuantity).reversed())
                    .toList();
            for (InventoryItem candidate : candidates) {
                if (remaining <= 0) {
                    break;
                }
                WarehouseLocation location = locations.get(candidate.getLocationId());
                if (location == null) {
                    continue;
                }
                int pickQuantity = Math.min(remaining, candidate.getQuantity());
                nodes.add(new RouteNode(productId, location, pickQuantity));
                remaining -= pickQuantity;
            }
            if (remaining > 0) {
                throw new IllegalStateException("Nicht genügend Bestand für Produkt " + productId);
            }
        });

        List<RouteNode> ordered = optimiseRoute(nodes);
        List<PickRouteResponse.RouteWaypoint> waypoints = new ArrayList<>();
        double[] current = {0, 0, 0};
        double totalDistance = 0;
        double cumulativeSeconds = 0;
        for (RouteNode node : ordered) {
            double[] destination = {node.location().getX(), node.location().getY(), node.location().getZ()};
            double legDistance = euclideanDistance(current, destination);
            totalDistance += legDistance;
            cumulativeSeconds += legDistance / WALKING_SPEED_MS;
            cumulativeSeconds += node.quantity() * 4.0;
            waypoints.add(new PickRouteResponse.RouteWaypoint(node.location().getId(), node.productId(),
                    destination[0], destination[1], destination[2], Math.round(cumulativeSeconds * 10.0) / 10.0,
                    node.quantity()));
            current = destination;
        }
        return new PickRouteResponse(waypoints, Math.round(totalDistance * 10.0) / 10.0,
                Math.round(cumulativeSeconds * 10.0) / 10.0);
    }

    public BoxRecommendationResponse recommend3dBox(BoxRecommendationRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Es werden mindestens eine Position für die Kartonempfehlung benötigt");
        }
        double targetUtilisation = Optional.ofNullable(request.getTargetUtilisation()).orElse(0.82d);
        targetUtilisation = Math.max(0.5d, Math.min(0.95d, targetUtilisation));

        double totalVolume = 0;
        double totalWeight = 0;
        double longestEdge = 0;

        for (BoxRecommendationRequest.Item item : request.getItems()) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                continue;
            }
            WarehouseProduct product = products.get(item.getProductId());
            double unitVolume = resolveVolume(item, product);
            double unitWeight = resolveWeight(item, product);
            double itemLongestEdge = resolveLongestEdge(item, unitVolume);

            totalVolume += unitVolume * item.getQuantity();
            totalWeight += unitWeight * item.getQuantity();
            longestEdge = Math.max(longestEdge, itemLongestEdge);
        }

        if (totalVolume <= 0) {
            totalVolume = 0.01;
        }

        List<BoxRecommendationResponse.Alternative> alternatives = new ArrayList<>();
        BoxOption bestOption = null;
        int bestBoxes = 0;
        double bestUtilisation = 0;
        double bestScore = Double.MAX_VALUE;

        for (BoxOption option : BOX_CATALOG) {
            if (longestEdge > option.largestEdgeCm()) {
                continue;
            }
            double boxVolume = option.volumeCubicM();
            int boxesNeeded = Math.max(1, (int) Math.ceil(totalVolume / (boxVolume * targetUtilisation)));
            while (boxesNeeded < 1000 && (totalWeight / boxesNeeded) > option.maxWeightKg()) {
                boxesNeeded++;
            }
            double utilisation = totalVolume / (boxesNeeded * boxVolume);
            alternatives.add(new BoxRecommendationResponse.Alternative(option.id(), option.name(), boxesNeeded,
                    Math.min(utilisation, 1.0), boxVolume, option.maxWeightKg()));
            double score = boxesNeeded * boxVolume;
            if (score < bestScore) {
                bestScore = score;
                bestOption = option;
                bestBoxes = boxesNeeded;
                bestUtilisation = utilisation;
            }
        }

        if (bestOption == null) {
            throw new IllegalStateException("Kein Versandkarton aus dem Katalog erfüllt die Anforderungen");
        }

        alternatives.sort(Comparator.comparingInt(BoxRecommendationResponse.Alternative::getBoxesRequired)
                .thenComparing(BoxRecommendationResponse.Alternative::getUtilisation).reversed());

        return new BoxRecommendationResponse(bestOption.id(), bestOption.name(), bestBoxes,
                Math.min(bestUtilisation, 1.0), totalVolume, totalWeight, alternatives);
    }

    public ReturnWorkflowResponse registerReturn(ReturnWorkflowRequest request) {
        ReturnCase newCase = new ReturnCase(UUID.randomUUID().toString(), request.getProductId(),
                request.getReason(), "inspection", Instant.now());
        returnCases.add(newCase);
        return mapReturnCase(newCase);
    }

    public List<ReturnWorkflowResponse> listReturnCases() {
        return returnCases.stream().map(this::mapReturnCase).toList();
    }

    private ReturnWorkflowResponse mapReturnCase(ReturnCase returnCase) {
        return new ReturnWorkflowResponse(returnCase.getId(), returnCase.getProductId(),
                returnCase.getReason(), returnCase.getStatus(), returnCase.getCreatedAt());
    }

    public CarrierLabelResponse createCarrierLabel(CarrierLabelRequest request) {
        String tracking = request.getCarrier().substring(0, Math.min(3, request.getCarrier().length())).toUpperCase(Locale.ROOT)
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String label = "Carrier: " + request.getCarrier() + "\nShipment: " + request.getShipmentId() +
                "\nWeight: " + request.getWeightKg() + " kg\nDestination: " + request.getDestination();
        return new CarrierLabelResponse(request.getCarrier(), request.getShipmentId(), tracking, label);
    }

    public NlpQueryResponse answerQuestion(NlpQueryRequest request) {
        String query = Optional.ofNullable(request.getQuery()).orElse("?");
        String lower = query.toLowerCase(Locale.ROOT);
        Map<String, Object> data = new HashMap<>();
        String metric = "inventory_value";

        if (containsAny(lower, List.of("überbestand", "overstock", "überhang"))) {
            metric = "overstock_value";
            double value = inventory.stream()
                    .mapToDouble(item -> {
                        WarehouseProduct product = products.get(item.getProductId());
                        if (product == null || product.getCostPrice() == null) {
                            return 0;
                        }
                        int capacity = Optional.ofNullable(locations.get(item.getLocationId()))
                                .map(WarehouseLocation::getCapacity).orElse(0);
                        int threshold = capacity == 0 ? 50 : (int) Math.round(capacity * 0.8);
                        int over = Math.max(0, item.getQuantity() - threshold);
                        return product.getCostPrice().doubleValue() * over;
                    }).sum();
            data.put("value", Math.round(value * 100.0) / 100.0);
            data.put("currency", "CHF");
        } else if (containsAny(lower, List.of("pick-rate", "kommission", "picks"))) {
            metric = "pick_rate";
            double movementPerHour = movementLedger.stream()
                    .filter(entry -> entry.getTimestamp().isAfter(Instant.now().minus(1, ChronoUnit.DAYS)))
                    .count() * 2.5;
            data.put("value", Math.round(movementPerHour));
            data.put("unit", "picks/hour");
        } else if (containsAny(lower, List.of("forecast", "prognose", "bedarf"))) {
            metric = "inventory_forecast";
            String productId = identifyProductFromQuery(lower)
                    .orElseGet(() -> inventory.stream().findFirst().map(InventoryItem::getProductId).orElse(""));
            PredictiveInventoryResponse forecast = forecastInventory(productId);
            data.put("productId", productId);
            data.put("weeks", forecast.getForecast());
        } else if (containsAny(lower, List.of("temperatur", "temperature"))) {
            metric = "temperature_alert";
            double maxTemp = sensorReadings.values().stream()
                    .flatMap(List::stream)
                    .mapToDouble(SensorReading::getTemperature)
                    .max()
                    .orElse(0);
            data.put("value", maxTemp);
            data.put("unit", "°C");
        } else {
            double value = inventory.stream()
                    .mapToDouble(item -> Optional.ofNullable(products.get(item.getProductId()))
                            .map(WarehouseProduct::getCostPrice)
                            .map(BigDecimal::doubleValue)
                            .orElse(0.0) * item.getQuantity())
                    .sum();
            data.put("value", Math.round(value * 100.0) / 100.0);
            data.put("currency", "CHF");
        }
        return new NlpQueryResponse(query, metric, data,
                "Die Anfrage wurde KI-gestützt interpretiert und mit aggregierten Lagerdaten beantwortet.");
    }

    private double resolveVolume(BoxRecommendationRequest.Item item, WarehouseProduct product) {
        if (item.getVolumeCubicM() != null) {
            return Math.max(0.0005, item.getVolumeCubicM());
        }
        if (item.getLengthCm() != null && item.getWidthCm() != null && item.getHeightCm() != null) {
            return Math.max(0.0005,
                    (item.getLengthCm() * item.getWidthCm() * item.getHeightCm()) / 1_000_000.0);
        }
        if (product != null && product.getVolumeCubicM() > 0) {
            return product.getVolumeCubicM();
        }
        return 0.012;
    }

    private double resolveWeight(BoxRecommendationRequest.Item item, WarehouseProduct product) {
        if (item.getWeightKg() != null) {
            return Math.max(0.1, item.getWeightKg());
        }
        if (product != null && product.getWeightKg() > 0) {
            return product.getWeightKg();
        }
        return 0.5;
    }

    private double resolveLongestEdge(BoxRecommendationRequest.Item item, double volumeCubicM) {
        if (item.getLengthCm() != null && item.getWidthCm() != null && item.getHeightCm() != null) {
            return Math.max(item.getLengthCm(), Math.max(item.getWidthCm(), item.getHeightCm()));
        }
        return Math.cbrt(Math.max(volumeCubicM, 0.0005)) * 100;
    }

    public KpiDashboardResponse buildKpiDashboard() {
        Map<String, Double> kpis = Map.of(
                "pick_rate", 318.0,
                "inventory_turnover", 8.6,
                "on_time_delivery", 97.2,
                "scrap_rate", 1.3
        );
        Map<String, Double> trends = Map.of(
                "pick_rate", 4.2,
                "inventory_turnover", 1.1,
                "on_time_delivery", 0.4,
                "scrap_rate", -0.2
        );
        return new KpiDashboardResponse(kpis, trends);
    }

    private record BoxOption(String id, String name, double lengthCm, double widthCm, double heightCm, double maxWeightKg) {
        private double volumeCubicM() {
            return (lengthCm * widthCm * heightCm) / 1_000_000.0;
        }

        private double smallestEdgeCm() {
            return Math.min(lengthCm, Math.min(widthCm, heightCm));
        }

        private double largestEdgeCm() {
            return Math.max(lengthCm, Math.max(widthCm, heightCm));
        }
    }

    private boolean containsAny(String query, List<String> tokens) {
        for (String token : tokens) {
            if (query.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private Optional<String> identifyProductFromQuery(String query) {
        return products.values().stream()
                .filter(product -> product.getName() != null)
                .filter(product -> query.contains(product.getName().toLowerCase(Locale.ROOT))
                        || query.contains(product.getId().toLowerCase(Locale.ROOT)))
                .map(WarehouseProduct::getId)
                .findFirst();
    }

    private Map<LocalDate, Integer> generateForecast(String productId, int current) {
        Map<LocalDate, Integer> forecast = new LinkedHashMap<>();
        try {
            PredictiveReplenishmentResponse analytics = analyseReplenishment(productId, 0.95d);
            Map<LocalDate, Integer> dailyForecast = analytics.getDailyDemandForecast();
            if (!dailyForecast.isEmpty()) {
                int projection = current;
                LocalDate today = LocalDate.now();
                int dayOffset = 0;
                for (int week = 1; week <= 6; week++) {
                    double weeklyDemand = 0;
                    for (int day = 0; day < 7; day++) {
                        dayOffset++;
                        LocalDate target = today.plusDays(dayOffset);
                        int demand = dailyForecast.getOrDefault(target,
                                (int) Math.round(analytics.getAverageDailyDemand()));
                        weeklyDemand += Math.max(0, demand);
                    }
                    projection = Math.max(0, projection - (int) Math.round(weeklyDemand));
                    forecast.put(today.plusWeeks(week), projection);
                }
                return forecast;
            }
        } catch (RuntimeException ignored) {
        }

        Map<LocalDate, Integer> history = new HashMap<>();
        for (MovementLogEntry entry : movementLedger) {
            if (!Objects.equals(entry.getProductId(), productId)) {
                continue;
            }
            LocalDate date = entry.getTimestamp().atZone(ZoneId.systemDefault()).toLocalDate();
            int delta = 0;
            if (entry.getFromLocation() != null && !entry.getFromLocation().isBlank()) {
                delta -= entry.getQuantity();
            }
            if (entry.getToLocation() != null && !entry.getToLocation().isBlank()) {
                delta += entry.getQuantity();
            }
            history.merge(date, delta, Integer::sum);
        }

        double averageDailyChange = history.entrySet().stream()
                .filter(entry -> entry.getKey().isAfter(LocalDate.now().minusDays(30)))
                .mapToDouble(Map.Entry::getValue)
                .average()
                .orElse(0);

        double weekdaySeasonality = history.entrySet().stream()
                .filter(entry -> entry.getKey().getMonth() == LocalDate.now().getMonth())
                .mapToDouble(Map.Entry::getValue)
                .average()
                .orElse(averageDailyChange);

        double expectedChange = (averageDailyChange * 0.7) + (weekdaySeasonality * 0.3);
        int projection = current;
        for (int week = 1; week <= 6; week++) {
            projection += Math.round(expectedChange * 7);
            forecast.put(LocalDate.now().plusWeeks(week), Math.max(0, projection));
        }
        return forecast;
    }

    private int calculateSafetyStock(String productId) {
        double avgDailyConsumption = movementLedger.stream()
                .filter(entry -> Objects.equals(entry.getProductId(), productId))
                .mapToInt(entry -> entry.getFromLocation() != null && !entry.getFromLocation().isBlank()
                        ? entry.getQuantity() : 0)
                .average()
                .orElse(5);
        double leadTime = suppliers.values().stream()
                .mapToDouble(SupplierProfile::getLeadTimeDays)
                .average()
                .orElse(5);
        return (int) Math.round(avgDailyConsumption * leadTime * 0.5);
    }

    public PredictiveReplenishmentResponse analyseReplenishment(String productId, double serviceLevelTarget) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("Product id required for replenishment analysis");
        }

        int onHand = inventory.stream()
                .filter(item -> productId.equals(item.getProductId()))
                .mapToInt(InventoryItem::getQuantity)
                .sum();

        NavigableMap<LocalDate, Integer> dailySeries = buildDemandSeries(productId, 90);
        if (dailySeries.isEmpty()) {
            dailySeries = buildEmptySeries(30);
        }

        List<Double> demandValues = new ArrayList<>();
        double alpha = 0.4;
        double beta = 0.2;
        double level = 0;
        double trend = 0;
        boolean initialised = false;

        for (Map.Entry<LocalDate, Integer> entry : dailySeries.entrySet()) {
            double demand = Math.max(0, entry.getValue());
            demandValues.add(demand);
            if (!initialised) {
                level = demand;
                trend = 0;
                initialised = true;
            } else {
                double previousLevel = level;
                level = alpha * demand + (1 - alpha) * (level + trend);
                trend = beta * (level - previousLevel) + (1 - beta) * trend;
            }
        }

        double averageDailyDemand = demandValues.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = demandValues.size() > 1
                ? demandValues.stream()
                .mapToDouble(value -> Math.pow(value - averageDailyDemand, 2))
                .sum() / (demandValues.size() - 1)
                : 0;
        double volatility = Math.sqrt(variance);

        Map<LocalDate, Integer> dailyForecast = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int offset = 1; offset <= 28; offset++) {
            double projected = Math.max(0, level + (offset * trend));
            dailyForecast.put(today.plusDays(offset), (int) Math.round(projected));
        }

        double leadTime = suppliers.isEmpty()
                ? 5
                : suppliers.values().stream().mapToDouble(SupplierProfile::getLeadTimeDays).average().orElse(5);
        double zScore = zScoreForServiceLevel(serviceLevelTarget);
        double leadTimeBuffer = Math.sqrt(Math.max(leadTime, 1));

        int reorderPoint = (int) Math.round(Math.max(0,
                averageDailyDemand * leadTime + zScore * volatility * leadTimeBuffer));

        int coverageDays = Math.max(14, (int) Math.ceil(leadTime * 1.5));
        double targetStock = averageDailyDemand * coverageDays
                + zScore * volatility * Math.sqrt(Math.max(coverageDays, 1));
        int recommendedOrder = (int) Math.max(0, Math.round(targetStock - onHand));

        double expectedConsumption = dailyForecast.values().stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(averageDailyDemand);
        if (expectedConsumption <= 0) {
            expectedConsumption = averageDailyDemand;
        }
        if (expectedConsumption <= 0) {
            expectedConsumption = 0.1;
        }
        int daysUntilStockout = onHand <= 0 ? 0 : (int) Math.floor(onHand / expectedConsumption);

        double confidence = demandValues.isEmpty()
                ? 0.4
                : Math.max(0.1, Math.min(0.99, 1 - (volatility / (averageDailyDemand + 1.0))));
        if (averageDailyDemand < 0.01) {
            confidence = Math.min(confidence, 0.6);
        }

        String trendDescriptor;
        if (Math.abs(trend) < 0.1) {
            trendDescriptor = "stabil";
        } else if (trend > 0) {
            trendDescriptor = "steigend";
        } else {
            trendDescriptor = "rückläufig";
        }

        String rationale = String.format(Locale.ROOT,
                "Ø Bedarf %.1f/Tag (%s) → Nachschubpunkt %d, Vorrat reicht %d Tage bei %.0f%% Servicelevel.",
                averageDailyDemand,
                trendDescriptor,
                reorderPoint,
                daysUntilStockout,
                Math.max(serviceLevelTarget, 0.5) * 100);
        if (recommendedOrder > 0) {
            rationale += String.format(Locale.ROOT, " Empfohlene Bestellung: %d Stück.", recommendedOrder);
        } else {
            rationale += " Kein zusätzlicher Bestand erforderlich.";
        }

        return new PredictiveReplenishmentResponse(productId, averageDailyDemand, trend, volatility,
                reorderPoint, recommendedOrder, daysUntilStockout, confidence, rationale, dailyForecast);
    }

    private NavigableMap<LocalDate, Integer> buildDemandSeries(String productId, int lookbackDays) {
        NavigableMap<LocalDate, Integer> series = new TreeMap<>();
        LocalDate today = LocalDate.now();
        for (int i = lookbackDays; i >= 0; i--) {
            series.put(today.minusDays(i), 0);
        }
        for (MovementLogEntry entry : movementLedger) {
            if (!Objects.equals(entry.getProductId(), productId)) {
                continue;
            }
            LocalDate date = entry.getTimestamp().atZone(ZoneId.systemDefault()).toLocalDate();
            if (date.isBefore(today.minusDays(lookbackDays))) {
                continue;
            }
            int outbound = entry.getFromLocation() != null && !entry.getFromLocation().isBlank()
                    ? entry.getQuantity() : 0;
            int inbound = entry.getToLocation() != null && !entry.getToLocation().isBlank()
                    ? entry.getQuantity() : 0;
            series.merge(date, outbound - inbound, Integer::sum);
        }
        return series;
    }

    private NavigableMap<LocalDate, Integer> buildEmptySeries(int days) {
        NavigableMap<LocalDate, Integer> fallback = new TreeMap<>();
        LocalDate today = LocalDate.now();
        for (int i = days; i >= 0; i--) {
            fallback.put(today.minusDays(i), 0);
        }
        return fallback;
    }

    private double zScoreForServiceLevel(double serviceLevelTarget) {
        double[][] reference = new double[][]{
                {0.50, 0.0},
                {0.60, 0.253},
                {0.70, 0.524},
                {0.80, 0.842},
                {0.85, 1.036},
                {0.90, 1.282},
                {0.95, 1.645},
                {0.975, 1.96},
                {0.98, 2.054},
                {0.99, 2.326},
                {0.995, 2.576},
                {0.998, 2.878},
                {0.999, 3.090}
        };
        double target = Math.max(0.5, Math.min(serviceLevelTarget, 0.999));
        for (int i = 0; i < reference.length - 1; i++) {
            double lowerLevel = reference[i][0];
            double upperLevel = reference[i + 1][0];
            if (target <= upperLevel) {
                double lowerZ = reference[i][1];
                double upperZ = reference[i + 1][1];
                double ratio = (target - lowerLevel) / (upperLevel - lowerLevel);
                return lowerZ + ratio * (upperZ - lowerZ);
            }
        }
        return reference[reference.length - 1][1];
    }

    private void adjustInventoryOnMovement(WarehouseProduct product, String fromLocation, String toLocation,
                                            int quantity, Instant timestamp) {
        if (fromLocation != null && !fromLocation.isBlank()) {
            InventoryItem sourceItem = inventory.stream()
                    .filter(item -> item.getProductId().equals(product.getId())
                            && fromLocation.equals(item.getLocationId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Keine Bestände an Standort " + fromLocation + " für Produkt " + product.getId()));
            if (sourceItem.getQuantity() < quantity) {
                throw new IllegalStateException("Unzureichender Bestand an Standort " + fromLocation);
            }
            sourceItem.setQuantity(sourceItem.getQuantity() - quantity);
            sourceItem.setLastMovement(timestamp);
            adjustLocationOccupancy(fromLocation, -quantity);
            if (sourceItem.getQuantity() == 0) {
                inventory.remove(sourceItem);
            }
        }

        if (toLocation != null && !toLocation.isBlank()) {
            WarehouseLocation destination = Optional.ofNullable(locations.get(toLocation))
                    .orElseThrow(() -> new IllegalArgumentException("Unbekannter Zielstandort " + toLocation));
            if (destination.getOccupied() + quantity > destination.getCapacity()) {
                throw new IllegalStateException("Zielstandort " + toLocation + " ist überbucht");
            }
            InventoryItem target = inventory.stream()
                    .filter(item -> item.getProductId().equals(product.getId())
                            && toLocation.equals(item.getLocationId()))
                    .findFirst()
                    .orElseGet(() -> {
                        InventoryItem created = new InventoryItem();
                        created.setProductId(product.getId());
                        created.setLocationId(toLocation);
                        created.setLifecycleStatus("available");
                        inventory.add(created);
                        return created;
                    });
            target.setQuantity(target.getQuantity() + quantity);
            target.setLastMovement(timestamp);
            adjustLocationOccupancy(toLocation, quantity);
        }
    }

    private void adjustLocationOccupancy(String locationId, int delta) {
        String normalized = normalizeLocationId(locationId);
        if (normalized == null || normalized.isBlank()) {
            return;
        }
        WarehouseLocation location = locations.get(normalized);
        if (location == null) {
            return;
        }
        int updated = Math.max(0, location.getOccupied() + delta);
        location.setOccupied(Math.min(updated, location.getCapacity()));
    }

    private String normalizeLocationId(String locationId) {
        return locationId == null ? null : locationId.trim();
    }

    private double aStarDistance(WarehouseLocation target) {
        GridPoint start = new GridPoint(0, 0, 0);
        GridPoint goal = new GridPoint((int) Math.round(target.getX()), (int) Math.round(target.getY()),
                (int) Math.round(target.getZ()));
        int maxX = (int) Math.ceil(locations.values().stream().mapToDouble(WarehouseLocation::getX).max().orElse(10));
        int maxY = (int) Math.ceil(locations.values().stream().mapToDouble(WarehouseLocation::getY).max().orElse(10));
        int maxZ = (int) Math.ceil(locations.values().stream().mapToDouble(WarehouseLocation::getZ).max().orElse(3));
        Set<GridPoint> blocked = new HashSet<>();
        locations.values().stream()
                .filter(WarehouseLocation::isBlocked)
                .map(loc -> new GridPoint((int) Math.round(loc.getX()), (int) Math.round(loc.getY()),
                        (int) Math.round(loc.getZ())))
                .forEach(blocked::add);

        PriorityQueue<PathNode> open = new PriorityQueue<>();
        Map<GridPoint, Double> gScore = new HashMap<>();
        open.add(new PathNode(start, 0, heuristic(start, goal)));
        gScore.put(start, 0.0);
        Set<GridPoint> closed = new HashSet<>();

        while (!open.isEmpty()) {
            PathNode current = open.poll();
            if (current.point().equals(goal)) {
                return current.g();
            }
            if (!closed.add(current.point())) {
                continue;
            }

            for (GridPoint neighbour : current.point().neighbours()) {
                if (neighbour.x() < -2 || neighbour.y() < -2 || neighbour.z() < 0
                        || neighbour.x() > maxX + 2 || neighbour.y() > maxY + 2 || neighbour.z() > maxZ + 5) {
                    continue;
                }
                if (blocked.contains(neighbour)) {
                    continue;
                }
                double tentative = current.g() + current.point().distanceTo(neighbour);
                if (tentative < gScore.getOrDefault(neighbour, Double.POSITIVE_INFINITY)) {
                    gScore.put(neighbour, tentative);
                    open.add(new PathNode(neighbour, tentative, tentative + heuristic(neighbour, goal)));
                }
            }
        }
        return euclideanDistance(new double[]{start.x(), start.y(), start.z()},
                new double[]{goal.x(), goal.y(), goal.z()});
    }

    private double heuristic(GridPoint from, GridPoint to) {
        return Math.sqrt(Math.pow(from.x() - to.x(), 2) + Math.pow(from.y() - to.y(), 2)
                + Math.pow(from.z() - to.z(), 2));
    }

    private List<RouteNode> optimiseRoute(List<RouteNode> nodes) {
        if (nodes.size() <= 1) {
            return nodes;
        }
        List<RouteNode> remaining = new ArrayList<>(nodes);
        List<RouteNode> ordered = new ArrayList<>();
        double[] current = {0, 0, 0};
        while (!remaining.isEmpty()) {
            RouteNode best = null;
            double bestDistance = Double.MAX_VALUE;
            for (RouteNode candidate : remaining) {
                double[] target = {candidate.location().getX(), candidate.location().getY(), candidate.location().getZ()};
                double distance = euclideanDistance(current, target);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
            ordered.add(best);
            current = new double[]{best.location().getX(), best.location().getY(), best.location().getZ()};
            remaining.remove(best);
        }
        return ordered;
    }

    private record RouteNode(String productId, WarehouseLocation location, int quantity) {
    }

    private record GridPoint(int x, int y, int z) {

        List<GridPoint> neighbours() {
            return List.of(
                    new GridPoint(x + 1, y, z),
                    new GridPoint(x - 1, y, z),
                    new GridPoint(x, y + 1, z),
                    new GridPoint(x, y - 1, z),
                    new GridPoint(x, y, z + 1),
                    new GridPoint(x, y, z - 1)
            );
        }

        double distanceTo(GridPoint other) {
            return Math.sqrt(Math.pow(x - other.x(), 2) + Math.pow(y - other.y(), 2)
                    + Math.pow(z - other.z(), 2));
        }
    }

    private record PathNode(GridPoint point, double g, double f) implements Comparable<PathNode> {
        @Override
        public int compareTo(PathNode other) {
            return Double.compare(this.f, other.f);
        }
    }

    private static class CategoryStatistics {
        private double totalWeight;
        private double totalVolume;
        private int observations;

        void observe(double weight, double volume) {
            if (weight > 0) {
                totalWeight += weight;
            }
            if (volume > 0) {
                totalVolume += volume;
            }
            observations++;
        }

        double getAverageWeight() {
            return observations == 0 ? 0 : totalWeight / observations;
        }

        double getAverageVolume() {
            return observations == 0 ? 0 : totalVolume / observations;
        }
    }
}
