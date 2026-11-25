package com.chrono.chrono.services;

import com.chrono.chrono.entities.UserUiPreferenceArea;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiPreferencePayloadValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UiPreferencePayloadValidator validator = new UiPreferencePayloadValidator(objectMapper);

    @Test
    void roundTripsTwelveChronoAndTwelvePmsTabsIncludingExplicitCopies() {
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode tabs = payload.putArray("tabs");
        for (int index = 0; index < 12; index++) {
            tabs.addObject().put("id", "chrono-" + index).put("viewKey", "dashboard")
                    .put("pinned", index == 0).putObject("params");
            tabs.addObject().put("id", "pms-" + index).put("viewKey", "pms")
                    .put("pinned", false).putObject("params")
                    .put("propertyId", 42).put("workspace", "room-plan");
        }
        payload.put("activeTabId", "pms-11");

        String serialized = validator.validateAndSerialize(UserUiPreferenceArea.APP_TABS, "workspace", payload);

        assertEquals(payload, validator.deserializeAndValidate(
                UserUiPreferenceArea.APP_TABS, "workspace", serialized));
    }

    @Test
    void rejectsDuplicateTabIdentityWhileAllowingDuplicatePages() {
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode tabs = payload.putArray("tabs");
        tabs.addObject().put("id", "pms-copy").put("viewKey", "pms");
        tabs.addObject().put("id", "pms-copy").put("viewKey", "pms");

        assertThrows(IllegalArgumentException.class, () -> validator.validateAndSerialize(
                UserUiPreferenceArea.APP_TABS, "workspace", payload));
    }

    @Test
    void sharedWorkspaceStillEnforcesTheOverallTabLimit() {
        ObjectNode payload = objectMapper.createObjectNode();
        ArrayNode tabs = payload.putArray("tabs");
        for (int index = 0; index <= UiPreferencePayloadValidator.MAX_TABS; index++) {
            tabs.addObject().put("id", "copy-" + index).put("viewKey", "pms");
        }

        assertThrows(IllegalArgumentException.class, () -> validator.validateAndSerialize(
                UserUiPreferenceArea.APP_TABS, "workspace", payload));
    }

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
    void roundTripsClassicAndWorkspaceAdminLayoutsWithoutMixingTheirPreferences() {
        ObjectNode payload = dashboardPayload("overview");
        ObjectNode layouts = (ObjectNode) payload.path("layouts");
        for (String scope : new String[]{"time", "requests", "calendar", "modules",
                "workspace-time", "workspace-requests", "workspace-calendar", "workspace-modules"}) {
            ObjectNode layout = (ObjectNode) dashboardPayload(scope).path("layouts").path(scope);
            ((ObjectNode) layout.path("widgets").path(0))
                    .put("visible", !scope.startsWith("workspace-"))
                    .put("size", scope.startsWith("workspace-") ? "full" : "M");
            layouts.set(scope, layout);
        }

        String serialized = validator.validateAndSerialize(
                UserUiPreferenceArea.TIME_ADMIN_DASHBOARD, "ADMIN", payload);

        assertEquals(9, payload.path("layouts").size());
        assertEquals(payload, validator.deserializeAndValidate(
                UserUiPreferenceArea.TIME_ADMIN_DASHBOARD, "ADMIN", serialized));
    }

    @ParameterizedTest
    @ValueSource(strings = {"workspace-time", "workspace-requests", "workspace-calendar", "workspace-modules"})
    void workspaceAdminScopesDoNotExpandUserOrPmsDashboardScopes(String scope) {
        ObjectNode payload = dashboardPayload(scope);
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndSerialize(
                UserUiPreferenceArea.TIME_USER_DASHBOARD, "USER_STANDARD", payload));
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndSerialize(
                UserUiPreferenceArea.PMS_DASHBOARD, "property:42", payload));
    }

    @ParameterizedTest
    @ValueSource(strings = {"workspace", "workspace-overview", "workspace-settings", "workspace-time-extra"})
    void workspaceScopesRemainAnExactAllowlist(String scope) {
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndSerialize(
                UserUiPreferenceArea.TIME_ADMIN_DASHBOARD, "ADMIN", dashboardPayload(scope)));
    }

    @ParameterizedTest
    @CsvSource({"0,0,12,24", "11,199,1,1", "3,176,9,24"})
    void pmsGridRoundTripsBoundaryPositionsWithoutNormalization(int x, int y, int w, int h) {
        ObjectNode payload = dashboardPayload("property:42");
        pmsWidget(payload).put("x", x).put("y", y).put("w", w).put("h", h);

        String serialized = validator.validateAndSerialize(UserUiPreferenceArea.PMS_DASHBOARD, "property:42", payload);

        assertEquals(payload, validator.deserializeAndValidate(
                UserUiPreferenceArea.PMS_DASHBOARD, "property:42", serialized));
    }

    @Test
    void pmsGridCanCoexistWithLegacyWidgetsWithoutAddingCoordinatesToThem() {
        ObjectNode payload = dashboardPayload("property:42");
        ArrayNode widgets = (ArrayNode) payload.path("layouts").path("property:42").path("widgets");
        widgets.addObject().put("id", "grid-widget").put("visible", false).put("order", 1).put("size", "S")
                .put("x", 8).put("y", 19).put("w", 4).put("h", 3);

        String serialized = validator.validateAndSerialize(UserUiPreferenceArea.PMS_DASHBOARD, "property:42", payload);

        assertEquals(payload, validator.deserializeAndValidate(UserUiPreferenceArea.PMS_DASHBOARD, "property:42", serialized));
    }

    @ParameterizedTest
    @CsvSource({"-1,0,1,1", "12,0,1,1", "0,-1,1,1", "0,200,1,1",
            "0,0,0,1", "0,0,13,1", "0,0,1,0", "0,0,1,25", "11,0,2,1", "0,199,1,2"})
    void rejectsPmsGridBoundsAndOverflowBeyondRightOrBottomEdge(int x, int y, int w, int h) {
        ObjectNode payload = dashboardPayload("property:42");
        pmsWidget(payload).put("x", x).put("y", y).put("w", w).put("h", h);

        assertThrows(IllegalArgumentException.class, () -> validator.validateAndSerialize(
                UserUiPreferenceArea.PMS_DASHBOARD, "property:42", payload));
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "y", "w", "h"})
    void rejectsPartialNullFractionalStringAndOverflowingPmsGridFields(String field) throws Exception {
        for (String value : new String[]{"null", "0.5", "\"1\"", "true", "2147483648"}) {
            ObjectNode payload = dashboardPayload("property:42");
            pmsWidget(payload).put("x", 0).put("y", 0).put("w", 1).put("h", 1)
                    .set(field, objectMapper.readTree(value));
            assertThrows(IllegalArgumentException.class, () -> validator.validateAndSerialize(
                    UserUiPreferenceArea.PMS_DASHBOARD, "property:42", payload), field + "=" + value);
        }
        ObjectNode partial = dashboardPayload("property:42");
        pmsWidget(partial).put("x", 0).put("y", 0).put("w", 1).put("h", 1).remove(field);
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndSerialize(
                UserUiPreferenceArea.PMS_DASHBOARD, "property:42", partial));
    }

    @ParameterizedTest
    @EnumSource(value = UserUiPreferenceArea.class, names = {"TIME_USER_DASHBOARD", "TIME_ADMIN_DASHBOARD"})
    void pmsGridExtensionDoesNotLoosenChronoDashboardContract(UserUiPreferenceArea area) {
        String scope = area == UserUiPreferenceArea.TIME_USER_DASHBOARD ? "default" : "overview";
        String context = area == UserUiPreferenceArea.TIME_USER_DASHBOARD ? "USER_STANDARD" : "ADMIN";
        ObjectNode payload = dashboardPayload(scope);
        ((ObjectNode) payload.path("layouts").path(scope).path("widgets").path(0))
                .put("x", 0).put("y", 0).put("w", 1).put("h", 1);

        assertThrows(IllegalArgumentException.class, () -> validator.validateAndSerialize(area, context, payload));
    }

    @Test
    void pmsGridExtensionStillRejectsUnknownWidgetFieldsAndRequiresLegacyMetadata() {
        ObjectNode payload = dashboardPayload("property:42");
        pmsWidget(payload).put("x", 0).put("y", 0).put("w", 1).put("h", 1).put("minWidth", 1);
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndSerialize(
                UserUiPreferenceArea.PMS_DASHBOARD, "property:42", payload));

        pmsWidget(payload).remove("minWidth");
        for (String required : new String[]{"id", "visible", "order", "size"}) {
            ObjectNode missingMetadata = payload.deepCopy();
            pmsWidget(missingMetadata).remove(required);
            assertThrows(IllegalArgumentException.class, () -> validator.validateAndSerialize(
                    UserUiPreferenceArea.PMS_DASHBOARD, "property:42", missingMetadata));
        }
    }

    private ObjectNode pmsWidget(ObjectNode payload) {
        return (ObjectNode) payload.path("layouts").path("property:42").path("widgets").path(0);
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
