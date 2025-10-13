package com.chrono.chrono.warehouse.controller;

import com.chrono.chrono.warehouse.dto.*;
import com.chrono.chrono.warehouse.model.InventoryItem;
import com.chrono.chrono.warehouse.model.MovementLogEntry;
import com.chrono.chrono.warehouse.model.SensorReading;
import com.chrono.chrono.warehouse.model.WarehouseLocation;
import com.chrono.chrono.warehouse.service.WarehouseIntelligenceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chrono2")
public class ChronoWarehouseController {

    private final WarehouseIntelligenceService intelligenceService;

    public ChronoWarehouseController(WarehouseIntelligenceService intelligenceService) {
        this.intelligenceService = intelligenceService;
    }

    @GetMapping("/products")
    public List<ProductResponse> listProducts() {
        return intelligenceService.listProducts();
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse upsertProduct(@RequestBody ProductRequest request) {
        return intelligenceService.upsertProduct(request);
    }

    @GetMapping("/locations")
    public List<WarehouseLocation> listLocations() {
        return intelligenceService.listLocations();
    }

    @GetMapping("/inventory")
    public List<InventoryItem> listInventory() {
        return intelligenceService.listInventory();
    }

    @PostMapping("/slotting")
    public SmartSlottingResponse recommendSlot(@RequestBody SmartSlottingRequest request) {
        return intelligenceService.recommendSlot(request);
    }

    @GetMapping("/inventory/{productId}/forecast")
    public PredictiveInventoryResponse forecast(@PathVariable String productId) {
        return intelligenceService.forecastInventory(productId);
    }

    @PostMapping("/iot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SensorReading addSensorReading(@RequestBody SensorReadingRequest request) {
        return intelligenceService.recordSensorReading(request);
    }

    @GetMapping("/iot/{locationId}")
    public List<SensorReading> listSensorReadings(@PathVariable String locationId) {
        return intelligenceService.getSensorReadings(locationId);
    }

    @PostMapping("/blockchain/movement")
    public MovementLogEntry logMovement(@RequestParam String productId,
                                        @RequestParam(required = false) String from,
                                        @RequestParam String to,
                                        @RequestParam int quantity) {
        return intelligenceService.recordMovement(productId, from, to, quantity);
    }

    @GetMapping("/blockchain/movement")
    public List<MovementLogEntry> getMovementLedger() {
        return intelligenceService.getMovementLedger();
    }

    @PostMapping("/procurement/sourcing")
    public SmartSourcingResponse recommendSupplier(@RequestBody SmartSourcingRequest request) {
        return intelligenceService.selectSupplier(request);
    }

    @PostMapping("/procurement/accounting")
    public AccountingMatchResponse reconcile(@RequestBody AccountingMatchRequest request) {
        return intelligenceService.reconcileAccounting(request);
    }

    @PostMapping("/procurement/mobile-inbound")
    public MobileInboundResponse guideInbound(@RequestBody MobileInboundRequest request) {
        return intelligenceService.guideInbound(request);
    }

    @PostMapping("/outbound/pick-route")
    public PickRouteResponse planPickRoute(@RequestBody PickRouteRequest request) {
        return intelligenceService.planPickRoute(request);
    }

    @PostMapping("/outbound/3d-box-recommendation")
    public BoxRecommendationResponse recommendBox(@RequestBody BoxRecommendationRequest request) {
        return intelligenceService.recommend3dBox(request);
    }

    @GetMapping("/outbound/returns")
    public List<ReturnWorkflowResponse> listReturns() {
        return intelligenceService.listReturnCases();
    }

    @PostMapping("/outbound/returns")
    public ReturnWorkflowResponse registerReturn(@RequestBody ReturnWorkflowRequest request) {
        return intelligenceService.registerReturn(request);
    }

    @PostMapping("/outbound/carrier-label")
    public CarrierLabelResponse createCarrierLabel(@RequestBody CarrierLabelRequest request) {
        return intelligenceService.createCarrierLabel(request);
    }

    @PostMapping("/analytics/nlp")
    public NlpQueryResponse answerNlp(@RequestBody NlpQueryRequest request) {
        return intelligenceService.answerQuestion(request);
    }

    @GetMapping("/analytics/kpis")
    public KpiDashboardResponse kpis() {
        return intelligenceService.buildKpiDashboard();
    }
}
