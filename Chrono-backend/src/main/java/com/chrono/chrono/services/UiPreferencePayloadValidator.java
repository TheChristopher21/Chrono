package com.chrono.chrono.services;

import com.chrono.chrono.entities.UserUiPreferenceArea;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Component
public class UiPreferencePayloadValidator {

    static final int MAX_PAYLOAD_BYTES = 60 * 1024;
    static final int MAX_DEPTH = 12;
    static final int MAX_NODES = 2_000;
    static final int MAX_OBJECT_FIELDS = 100;
    static final int MAX_ARRAY_ITEMS = 200;
    static final int MAX_TEXT_BYTES = 4_096;
    static final int MAX_TABS = 30;
    static final int MAX_TAB_PARAMS = 20;
    static final int MAX_LAYOUT_SCOPES = 12;
    static final int MAX_WIDGETS_PER_LAYOUT = 60;

    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,80}");
    private static final Set<String> APP_TAB_ROOT_FIELDS = Set.of("tabs", "activeTabId");
    private static final Set<String> APP_TAB_FIELDS = Set.of("id", "viewKey", "params", "pinned");
    private static final Set<String> DASHBOARD_ROOT_FIELDS = Set.of("type", "schemaVersion", "layouts");
    private static final Set<String> DASHBOARD_LAYOUT_FIELDS = Set.of("widgets");
    private static final Set<String> DASHBOARD_WIDGET_FIELDS = Set.of("id", "visible", "order", "size");
    private static final Set<String> DASHBOARD_WIDGET_SIZES = Set.of("S", "M", "L", "full");
    private static final Set<String> ADMIN_DASHBOARD_SCOPES = Set.of(
            "overview", "time", "requests", "calendar", "modules"
    );
    private static final String DASHBOARD_PAYLOAD_TYPE = "chrono-dashboard-layouts";

    private final ObjectMapper objectMapper;

    public UiPreferencePayloadValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String validateAndSerialize(UserUiPreferenceArea area, String contextKey, JsonNode payload) {
        validate(area, contextKey, payload);
        try {
            String serialized = objectMapper.writeValueAsString(payload);
            requirePayloadSize(serialized);
            return serialized;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Preference payload could not be serialized.", exception);
        }
    }

    public JsonNode defaultPayload(UserUiPreferenceArea area) {
        ObjectNode payload = objectMapper.createObjectNode();
        if (area == UserUiPreferenceArea.APP_TABS) {
            payload.set("tabs", objectMapper.createArrayNode());
        }
        return payload;
    }

    public JsonNode deserializeAndValidate(UserUiPreferenceArea area, String contextKey, String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalStateException("Stored preference payload is empty.");
        }
        try {
            requirePayloadSize(payload);
            JsonNode value = objectMapper.readTree(payload);
            validate(area, contextKey, value);
            return value;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("Stored preference payload is invalid.", exception);
        }
    }

    public void requireAuthorizedTabs(JsonNode payload, Predicate<String> isAllowedView) {
        for (JsonNode tab : payload.path("tabs")) {
            String viewKey = tab.path("viewKey").asText();
            if (!isAllowedView.test(viewKey)) {
                throw new AccessDeniedException("Tab view is not available: " + viewKey);
            }
        }
    }

    public JsonNode filterUnauthorizedTabs(JsonNode payload, Predicate<String> isAllowedView) {
        return filterTabs(payload, tab -> isAllowedView.test(tab.path("viewKey").asText()));
    }

    public JsonNode filterTabs(JsonNode payload, Predicate<JsonNode> isAllowedTab) {
        ObjectNode copy = ((ObjectNode) payload).deepCopy();
        ArrayNode filtered = objectMapper.createArrayNode();
        Set<String> retainedIds = new LinkedHashSet<>();
        for (JsonNode tab : copy.path("tabs")) {
            if (isAllowedTab.test(tab)) {
                filtered.add(tab.deepCopy());
                retainedIds.add(tab.path("id").asText());
            }
        }
        copy.set("tabs", filtered);

        JsonNode activeTabId = copy.get("activeTabId");
        if (activeTabId != null && !retainedIds.contains(activeTabId.asText())) {
            if (filtered.isEmpty()) {
                copy.remove("activeTabId");
            } else {
                copy.put("activeTabId", filtered.get(0).path("id").asText());
            }
        }
        return copy;
    }

    public Set<Long> pmsPropertyIdsFromTabs(JsonNode payload) {
        Set<Long> propertyIds = new LinkedHashSet<>();
        for (JsonNode tab : payload.path("tabs")) {
            if (!UserPermissionService.PAGE_PMS.equals(tab.path("viewKey").asText())) {
                continue;
            }
            JsonNode propertyId = tab.path("params").get("propertyId");
            if (propertyId == null || propertyId.isNull()) {
                continue;
            }
            if (!propertyId.isIntegralNumber() || !propertyId.canConvertToLong() || propertyId.longValue() <= 0) {
                throw new IllegalArgumentException("PMS tab propertyId must be a positive integer.");
            }
            propertyIds.add(propertyId.longValue());
        }
        return propertyIds;
    }

    private void validate(UserUiPreferenceArea area, String contextKey, JsonNode payload) {
        if (area == null) {
            throw new IllegalArgumentException("Preference area is required.");
        }
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("Preference payload must be a JSON object.");
        }
        ValidationStats stats = new ValidationStats();
        validateNode(payload, 1, stats);
        if (area == UserUiPreferenceArea.APP_TABS) {
            validateAppTabs((ObjectNode) payload);
        } else {
            validateDashboardLayouts(area, contextKey, (ObjectNode) payload);
        }
    }

    private void validateNode(JsonNode node, int depth, ValidationStats stats) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("Preference payload is nested too deeply.");
        }
        stats.nodes++;
        if (stats.nodes > MAX_NODES) {
            throw new IllegalArgumentException("Preference payload contains too many values.");
        }

        if (node.isObject()) {
            if (node.size() > MAX_OBJECT_FIELDS) {
                throw new IllegalArgumentException("Preference object contains too many fields.");
            }
            Iterator<java.util.Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                java.util.Map.Entry<String, JsonNode> field = fields.next();
                if (field.getKey() == null || field.getKey().isBlank() || field.getKey().length() > 100) {
                    throw new IllegalArgumentException("Preference field name is invalid.");
                }
                validateNode(field.getValue(), depth + 1, stats);
            }
            return;
        }

        if (node.isArray()) {
            if (node.size() > MAX_ARRAY_ITEMS) {
                throw new IllegalArgumentException("Preference array contains too many items.");
            }
            for (JsonNode child : node) {
                validateNode(child, depth + 1, stats);
            }
            return;
        }

        if (node.isTextual()
                && node.textValue().getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("Preference text value is too long.");
        }
        if (node.isBinary() || node.isPojo()) {
            throw new IllegalArgumentException("Preference payload contains an unsupported value.");
        }
    }

    private void validateAppTabs(ObjectNode payload) {
        payload.fieldNames().forEachRemaining(field -> {
            if (!APP_TAB_ROOT_FIELDS.contains(field)) {
                throw new IllegalArgumentException("Unknown app-tab field: " + field);
            }
        });

        JsonNode tabs = payload.get("tabs");
        if (tabs == null || !tabs.isArray()) {
            throw new IllegalArgumentException("APP_TABS payload requires a tabs array.");
        }
        if (tabs.size() > MAX_TABS) {
            throw new IllegalArgumentException("Too many open tabs.");
        }

        Set<String> ids = new HashSet<>();
        for (JsonNode tab : tabs) {
            if (!tab.isObject()) {
                throw new IllegalArgumentException("Each tab must be a JSON object.");
            }
            tab.fieldNames().forEachRemaining(field -> {
                if (!APP_TAB_FIELDS.contains(field)) {
                    throw new IllegalArgumentException("Unknown tab field: " + field);
                }
            });

            String id = requiredSafeKey(tab, "id");
            requiredSafeKey(tab, "viewKey");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Tab ids must be unique.");
            }

            JsonNode pinned = tab.get("pinned");
            if (pinned != null && !pinned.isBoolean()) {
                throw new IllegalArgumentException("Tab pinned must be boolean.");
            }
            JsonNode params = tab.get("params");
            if (params != null) {
                validateTabParams(params);
            }
        }

        JsonNode activeTabId = payload.get("activeTabId");
        if (activeTabId != null) {
            if (!activeTabId.isTextual() || !SAFE_KEY.matcher(activeTabId.textValue()).matches()) {
                throw new IllegalArgumentException("activeTabId is invalid.");
            }
            if (!ids.contains(activeTabId.textValue())) {
                throw new IllegalArgumentException("activeTabId must reference an open tab.");
            }
        }
    }

    private void validateDashboardLayouts(
            UserUiPreferenceArea area,
            String contextKey,
            ObjectNode payload
    ) {
        requireOnlyFields(payload, DASHBOARD_ROOT_FIELDS, "dashboard root");

        JsonNode type = payload.get("type");
        if (type == null || !type.isTextual() || !DASHBOARD_PAYLOAD_TYPE.equals(type.textValue())) {
            throw new IllegalArgumentException("Dashboard payload type is invalid.");
        }

        JsonNode schemaVersion = payload.get("schemaVersion");
        if (schemaVersion == null
                || !schemaVersion.isIntegralNumber()
                || !schemaVersion.canConvertToInt()
                || schemaVersion.intValue() != UserUiPreferenceService.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Dashboard payload schemaVersion is invalid.");
        }

        JsonNode layouts = payload.get("layouts");
        if (layouts == null || !layouts.isObject() || layouts.size() > MAX_LAYOUT_SCOPES) {
            throw new IllegalArgumentException("Dashboard layouts must be a small JSON object.");
        }

        Iterator<java.util.Map.Entry<String, JsonNode>> scopes = layouts.fields();
        Set<String> allowedScopes = allowedDashboardScopes(area, contextKey);
        while (scopes.hasNext()) {
            java.util.Map.Entry<String, JsonNode> scope = scopes.next();
            if (!SAFE_KEY.matcher(scope.getKey()).matches() || !allowedScopes.contains(scope.getKey())) {
                throw new IllegalArgumentException("Dashboard layout scope is not supported for this area.");
            }
            validateDashboardLayout(scope.getValue());
        }
    }

    private Set<String> allowedDashboardScopes(UserUiPreferenceArea area, String contextKey) {
        return switch (area) {
            case TIME_USER_DASHBOARD -> Set.of("default");
            case TIME_ADMIN_DASHBOARD -> ADMIN_DASHBOARD_SCOPES;
            case PMS_DASHBOARD -> {
                if (contextKey == null || !contextKey.matches("property:[1-9][0-9]*")) {
                    throw new IllegalArgumentException("PMS dashboard context is invalid.");
                }
                yield Set.of(contextKey);
            }
            case APP_TABS -> throw new IllegalArgumentException("APP_TABS does not use dashboard scopes.");
        };
    }

    private void validateDashboardLayout(JsonNode layout) {
        if (layout == null || !layout.isObject()) {
            throw new IllegalArgumentException("Dashboard layout must be a JSON object.");
        }
        requireOnlyFields(layout, DASHBOARD_LAYOUT_FIELDS, "dashboard layout");

        JsonNode widgets = layout.get("widgets");
        if (widgets == null || !widgets.isArray() || widgets.size() > MAX_WIDGETS_PER_LAYOUT) {
            throw new IllegalArgumentException("Dashboard widgets must be a bounded array.");
        }

        Set<String> widgetIds = new HashSet<>();
        Set<Integer> widgetOrders = new HashSet<>();
        for (JsonNode widget : widgets) {
            if (!widget.isObject()) {
                throw new IllegalArgumentException("Dashboard widget must be a JSON object.");
            }
            requireOnlyFields(widget, DASHBOARD_WIDGET_FIELDS, "dashboard widget");

            String id = requiredSafeKey(widget, "id");
            if (!widgetIds.add(id)) {
                throw new IllegalArgumentException("Dashboard widget ids must be unique per layout.");
            }

            JsonNode visible = widget.get("visible");
            if (visible == null || !visible.isBoolean()) {
                throw new IllegalArgumentException("Dashboard widget visible must be boolean.");
            }

            JsonNode order = widget.get("order");
            if (order == null
                    || !order.isIntegralNumber()
                    || !order.canConvertToInt()
                    || order.intValue() < 0
                    || order.intValue() >= MAX_WIDGETS_PER_LAYOUT
                    || !widgetOrders.add(order.intValue())) {
                throw new IllegalArgumentException("Dashboard widget order must be unique and in range.");
            }

            JsonNode size = widget.get("size");
            if (size == null || !size.isTextual() || !DASHBOARD_WIDGET_SIZES.contains(size.textValue())) {
                throw new IllegalArgumentException("Dashboard widget size is invalid.");
            }
        }
    }

    private void requireOnlyFields(JsonNode object, Set<String> allowedFields, String objectName) {
        object.fieldNames().forEachRemaining(field -> {
            if (!allowedFields.contains(field)) {
                throw new IllegalArgumentException("Unknown " + objectName + " field: " + field);
            }
        });
    }

    private void validateTabParams(JsonNode params) {
        if (!params.isObject() || params.size() > MAX_TAB_PARAMS) {
            throw new IllegalArgumentException("Tab params must be a small JSON object.");
        }
        params.fields().forEachRemaining(entry -> {
            if (!SAFE_KEY.matcher(entry.getKey()).matches()) {
                throw new IllegalArgumentException("Tab parameter name is invalid.");
            }
            JsonNode value = entry.getValue();
            if (!(value.isNull() || value.isBoolean() || value.isNumber() || value.isTextual())) {
                throw new IllegalArgumentException("Tab parameter values must be scalar.");
            }
            if (value.isTextual() && value.textValue().getBytes(StandardCharsets.UTF_8).length > 512) {
                throw new IllegalArgumentException("Tab parameter value is too long.");
            }
        });
    }

    private String requiredSafeKey(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || !SAFE_KEY.matcher(value.textValue()).matches()) {
            throw new IllegalArgumentException("Tab " + field + " is invalid.");
        }
        return value.textValue();
    }

    private void requirePayloadSize(String payload) {
        if (payload.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Preference payload exceeds the size limit.");
        }
    }

    private static final class ValidationStats {
        private int nodes;
    }
}
