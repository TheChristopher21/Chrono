package com.chrono.chrono.services;

import com.chrono.chrono.entities.UserUiPreferenceArea;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiPreferencePayloadValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UiPreferencePayloadValidator validator = new UiPreferencePayloadValidator(objectMapper);

    @ParameterizedTest
    @EnumSource(value = UserUiPreferenceArea.class, names = {
            "TIME_USER_DASHBOARD", "TIME_ADMIN_DASHBOARD", "PMS_DASHBOARD"
    })
    void acceptsExactDashboardLayoutContract(UserUiPreferenceArea area) {
        String context = switch (area) {
            case TIME_USER_DASHBOARD -> "USER_STANDARD";
            case TIME_ADMIN_DASHBOARD -> "ADMIN";
            case PMS_DASHBOARD -> "property:42";
            case APP_TABS -> throw new IllegalArgumentException();
        };
        String scope = switch (area) {
            case TIME_USER_DASHBOARD -> "default";
            case TIME_ADMIN_DASHBOARD -> "overview";
            case PMS_DASHBOARD -> "property:42";
            case APP_TABS -> throw new IllegalArgumentException();
        };
        ObjectNode payload = dashboardPayload(scope);

        String serialized = assertDoesNotThrow(() -> validator.validateAndSerialize(area, context, payload));

        assertEquals(payload, assertDoesNotThrow(() -> objectMapper.readTree(serialized)));
    }

    @Test
    void dashboardGetDefaultRemainsEmptyWithoutPretendingItWasPersisted() {
        assertTrue(validator.defaultPayload(UserUiPreferenceArea.TIME_USER_DASHBOARD).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndSerialize(
                UserUiPreferenceArea.TIME_USER_DASHBOARD,
                "USER_STANDARD",
                objectMapper.createObjectNode()
        ));
    }

    @Test
    void reportsOversizedStoredPayloadAsServerStateInsteadOfClientInput() {
        String oversizedStoredPayload = "{\"value\":\"" + "x".repeat(70_000) + "\"}";

        assertThrows(IllegalStateException.class, () -> validator.deserializeAndValidate(
                UserUiPreferenceArea.TIME_USER_DASHBOARD,
                "USER_STANDARD",
                oversizedStoredPayload
        ));
    }

    @Test
    void rejectsUnknownDashboardFieldsAndUnsafeScopes() {
        ObjectNode unknownRoot = dashboardPayload("overview").put("companyId", 10);
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndSerialize(
                UserUiPreferenceArea.TIME_ADMIN_DASHBOARD,
                "ADMIN",
                unknownRoot
        ));

        ObjectNode unsafeScope = dashboardPayload("contains spaces");
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndSerialize(
                UserUiPreferenceArea.TIME_USER_DASHBOARD,
                "USER_STANDARD",
                unsafeScope
        ));

        ObjectNode unknownWidgetField = dashboardPayload("overview");
        ((ObjectNode) unknownWidgetField.path("layouts").path("overview").path("widgets").path(0))
                .put("url", "https://attacker.invalid");
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndSerialize(
                UserUiPreferenceArea.TIME_ADMIN_DASHBOARD,
                "ADMIN",
                unknownWidgetField
        ));
    }

    @Test
    void rejectsSafeButUncontractedDashboardScopes() {
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndSerialize(
                UserUiPreferenceArea.TIME_USER_DASHBOARD,
                "USER_STANDARD",
                dashboardPayload("second-dashboard")
        ));
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndSerialize(
                UserUiPreferenceArea.TIME_ADMIN_DASHBOARD,
                "ADMIN",
                dashboardPayload("future-admin-area")
        ));
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndSerialize(
                UserUiPreferenceArea.PMS_DASHBOARD,
                "property:42",
                dashboardPayload("property:43")
        ));
    }

    @Test
    void rejectsInvalidWidgetTypesSizesAndOrdering() {
        ObjectNode invalidVisible = dashboardPayload("default");
        ((ObjectNode) invalidVisible.path("layouts").path("default").path("widgets").path(0))
                .put("visible", "yes");
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndSerialize(
                UserUiPreferenceArea.TIME_USER_DASHBOARD,
                "USER_STANDARD",
                invalidVisible
        ));

        ObjectNode invalidSize = dashboardPayload("default");
        ((ObjectNode) invalidSize.path("layouts").path("default").path("widgets").path(0))
                .put("size", "XL");
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndSerialize(
                UserUiPreferenceArea.TIME_USER_DASHBOARD,
                "USER_STANDARD",
                invalidSize
        ));

        ObjectNode duplicateOrder = dashboardPayload("default");
        ArrayNode widgets = (ArrayNode) duplicateOrder.path("layouts")
                .path("default").path("widgets");
        ObjectNode duplicate = widgets.addObject();
        duplicate.put("id", "second-widget");
        duplicate.put("visible", false);
        duplicate.put("order", 0);
        duplicate.put("size", "full");
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndSerialize(
                UserUiPreferenceArea.TIME_USER_DASHBOARD,
                "USER_STANDARD",
                duplicateOrder
        ));
    }

    private ObjectNode dashboardPayload(String scope) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "chrono-dashboard-layouts");
        payload.put("schemaVersion", 1);
        ObjectNode widget = payload.putObject("layouts")
                .putObject(scope)
                .putArray("widgets")
                .addObject();
        widget.put("id", "weekly-summary");
        widget.put("visible", true);
        widget.put("order", 0);
        widget.put("size", "M");
        return payload;
    }
}
