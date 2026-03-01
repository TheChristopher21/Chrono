package com.chrono.chrono.warehouse.service;

import com.chrono.chrono.warehouse.dto.BoxRecommendationRequest;
import com.chrono.chrono.warehouse.dto.BoxRecommendationResponse;
import com.chrono.chrono.warehouse.dto.PickRouteRequest;
import com.chrono.chrono.warehouse.dto.PickRouteResponse;
import com.chrono.chrono.warehouse.dto.ProductRequest;
import com.chrono.chrono.warehouse.dto.ProductResponse;
import com.chrono.chrono.warehouse.model.InventoryItem;
import com.chrono.chrono.warehouse.model.MovementLogEntry;
import com.chrono.chrono.warehouse.model.WarehouseLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WarehouseIntelligenceServiceTest {

    @Test
    void upsertProductInfersCategoryAndWeight() {
        WarehouseIntelligenceService service = new WarehouseIntelligenceService();
        ProductRequest request = new ProductRequest();
        request.setName("Quantum Sensor Tag");

        ProductResponse response = service.upsertProduct(request);

        assertEquals("Quantum Sensor Tag", response.getName());
        assertEquals("IoT", response.getCategory());
        assertEquals(1.4, response.getWeightKg(), 0.001);
        assertNotNull(response.getSalesPrice());
    }

    @Test
    void recordMovementUpdatesInventoryAndOccupancy() {
        WarehouseIntelligenceService service = new WarehouseIntelligenceService();
        InventoryItem source = service.listInventory().stream()
                .filter(item -> item.getProductId().equals("SKU-AR-01"))
                .findFirst()
                .orElseThrow();
        WarehouseLocation sourceLocationBefore = service.listLocations().stream()
                .filter(location -> location.getId().equals(source.getLocationId()))
                .findFirst()
                .orElseThrow();

        int sourceQuantityBefore = source.getQuantity();
        int sourceOccupancyBefore = sourceLocationBefore.getOccupied();

        MovementLogEntry entry = service.recordMovement(source.getProductId(), source.getLocationId(), "A-02-03", 5);

        assertEquals("SKU-AR-01", entry.getProductId());
        assertNotNull(entry.getHash());
        assertEquals(5, entry.getQuantity());

        InventoryItem updatedSource = service.listInventory().stream()
                .filter(item -> item.getProductId().equals(source.getProductId())
                        && item.getLocationId().equals(source.getLocationId()))
                .findFirst()
                .orElseThrow();
        InventoryItem destination = service.listInventory().stream()
                .filter(item -> item.getProductId().equals(source.getProductId())
                        && item.getLocationId().equals("A-02-03"))
                .findFirst()
                .orElseThrow();

        WarehouseLocation sourceLocationAfter = service.listLocations().stream()
                .filter(location -> location.getId().equals(source.getLocationId()))
                .findFirst()
                .orElseThrow();
        WarehouseLocation destinationLocation = service.listLocations().stream()
                .filter(location -> location.getId().equals("A-02-03"))
                .findFirst()
                .orElseThrow();

        assertEquals(sourceQuantityBefore - 5, updatedSource.getQuantity());
        assertEquals(5, destination.getQuantity());
        assertEquals(sourceOccupancyBefore - 5, sourceLocationAfter.getOccupied());
        assertEquals(5, destinationLocation.getOccupied());
    }

    @Test
    void planPickRouteOptimisesByDistance() {
        WarehouseIntelligenceService service = new WarehouseIntelligenceService();
        PickRouteRequest request = new PickRouteRequest();
        PickRouteRequest.PickItem gloves = new PickRouteRequest.PickItem();
        gloves.setProductId("SKU-AR-GLV");
        gloves.setQuantity(2);
        PickRouteRequest.PickItem bot = new PickRouteRequest.PickItem();
        bot.setProductId("SKU-AR-01");
        bot.setQuantity(1);
        request.setItems(List.of(gloves, bot));

        PickRouteResponse response = service.planPickRoute(request);

        assertEquals(2, response.getWaypoints().size());
        assertEquals("A-01-01", response.getWaypoints().get(0).getLocationId());
        assertEquals("B-01-02", response.getWaypoints().get(1).getLocationId());
        assertTrue(response.getTotalDistance() > 0);
        assertTrue(response.getEstimatedDurationSeconds() > 0);
    }

    @Test
    void recommend3dBoxFindsBestFitFromCatalog() {
        WarehouseIntelligenceService service = new WarehouseIntelligenceService();
        BoxRecommendationRequest request = new BoxRecommendationRequest();
        BoxRecommendationRequest.Item item = new BoxRecommendationRequest.Item();
        item.setProductId("SKU-AR-01");
        item.setQuantity(3);
        request.setItems(List.of(item));

        BoxRecommendationResponse response = service.recommend3dBox(request);

        assertNotNull(response.getRecommendedBoxId());
        assertTrue(response.getBoxesRequired() >= 1);
        assertFalse(response.getAlternatives().isEmpty());
        assertTrue(response.getTotalVolumeCubicM() > 0);
    }
}
