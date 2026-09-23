package com.chrono.chrono.services;

import com.chrono.chrono.dto.UiPreferenceResponse;
import com.chrono.chrono.dto.UiPreferenceUpdateRequest;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.UserUiPreference;
import com.chrono.chrono.entities.UserUiPreferenceArea;
import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.exceptions.UiPreferenceRevisionConflictException;
import com.chrono.chrono.repositories.UserUiPreferenceRepository;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.chrono.chrono.services.pms.PmsPropertyAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class UserUiPreferenceService {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final Map<UserUiPreferenceArea, Set<String>> ALLOWED_CONTEXTS = Map.of(
            UserUiPreferenceArea.APP_TABS, Set.of("workspace"),
            UserUiPreferenceArea.TIME_USER_DASHBOARD,
            Set.of("USER_STANDARD", "USER_HOURLY", "USER_PERCENTAGE"),
            UserUiPreferenceArea.TIME_ADMIN_DASHBOARD, Set.of("ADMIN")
    );

    private final UserUiPreferenceRepository preferenceRepository;
    private final HotelPropertyRepository propertyRepository;
    private final AccessControlService accessControlService;
    private final UserPermissionService userPermissionService;
    private final UiPreferencePayloadValidator payloadValidator;
    private PmsPropertyAccessService pmsPropertyAccess;

    @Autowired
    public void setPmsPropertyAccess(PmsPropertyAccessService pmsPropertyAccess) { this.pmsPropertyAccess = pmsPropertyAccess; }

    public UserUiPreferenceService(
            UserUiPreferenceRepository preferenceRepository,
            HotelPropertyRepository propertyRepository,
            AccessControlService accessControlService,
            UserPermissionService userPermissionService,
            UiPreferencePayloadValidator payloadValidator
    ) {
        this.preferenceRepository = preferenceRepository;
        this.propertyRepository = propertyRepository;
        this.accessControlService = accessControlService;
        this.userPermissionService = userPermissionService;
        this.payloadValidator = payloadValidator;
    }

    @Transactional(readOnly = true)
    public UiPreferenceResponse get(
            Principal principal,
            UserUiPreferenceArea area,
            String requestedContext,
            Long propertyId
    ) {
        PreferenceContext context = requireContext(principal, area, requestedContext, propertyId);
        return find(context).map(preference -> {
            JsonNode payload = payloadValidator.deserializeAndValidate(
                    area, context.contextKey(), preference.getPayload());
            if (area == UserUiPreferenceArea.APP_TABS) {
                payload = payloadValidator.filterTabs(payload, tab -> canRestoreTab(context.actor(), tab));
            }
            return toResponse(preference, context.contextKey(), payload);
        }).orElseGet(() -> new UiPreferenceResponse(
                area,
                context.contextKey(),
                CURRENT_SCHEMA_VERSION,
                0L,
                payloadValidator.defaultPayload(area),
                null
        ));
    }

    @Transactional
    public UiPreferenceResponse put(
            Principal principal,
            UserUiPreferenceArea area,
            String requestedContext,
            Long propertyId,
            UiPreferenceUpdateRequest request
    ) {
        PreferenceContext context = requireContext(principal, area, requestedContext, propertyId);
        requireValidRequest(request);
        String serializedPayload = payloadValidator.validateAndSerialize(
                area, context.contextKey(), request.payload());

        if (area == UserUiPreferenceArea.APP_TABS) {
            payloadValidator.requireAuthorizedTabs(
                    request.payload(),
                    viewKey -> isAllowedView(context.actor(), viewKey)
            );
            requirePmsTabPropertiesInTenant(context.actor(), request.payload());
        }

        Optional<UserUiPreference> existing = find(context);
        UserUiPreference preference;
        if (existing.isPresent()) {
            preference = existing.get();
            if (preference.getRevision() != request.revision()) {
                throw revisionConflict();
            }
        } else {
            if (request.revision() != 0L) {
                throw revisionConflict();
            }
            preference = new UserUiPreference();
            preference.setUser(context.actor());
            preference.setCompany(context.company());
            preference.setProperty(context.property());
            preference.setTenantKey(context.tenantKey());
            preference.setArea(area);
            preference.setContextKey(context.contextKey());
        }

        preference.setSchemaVersion(request.schemaVersion());
        preference.setPayload(serializedPayload);

        try {
            UserUiPreference saved = preferenceRepository.saveAndFlush(preference);
            JsonNode responsePayload = payloadValidator.deserializeAndValidate(
                    area, context.contextKey(), saved.getPayload());
            return toResponse(saved, context.contextKey(), responsePayload);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException | DataIntegrityViolationException exception) {
            throw revisionConflict(exception);
        }
    }

    @Transactional
    public void delete(
            Principal principal,
            UserUiPreferenceArea area,
            String requestedContext,
            Long propertyId,
            long expectedRevision
    ) {
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("Preference revision must not be negative.");
        }
        PreferenceContext context = requireContext(principal, area, requestedContext, propertyId);
        Optional<UserUiPreference> existing = find(context);
        if (existing.isEmpty()) {
            if (expectedRevision == 0L) {
                return;
            }
            throw revisionConflict();
        }

        UserUiPreference preference = existing.get();
        if (preference.getRevision() != expectedRevision) {
            throw revisionConflict();
        }
        try {
            preferenceRepository.delete(preference);
            preferenceRepository.flush();
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
            throw revisionConflict(exception);
        }
    }

    private PreferenceContext requireContext(
            Principal principal,
            UserUiPreferenceArea area,
            String requestedContext,
            Long propertyId
    ) {
        if (area == null) {
            throw new IllegalArgumentException("Preference area is required.");
        }
        User actor = accessControlService.requireAuthenticatedUser(principal);
        if (actor.isDeleted() || actor.getId() == null) {
            throw new AccessDeniedException("Authenticated user is not available.");
        }

        requireAreaAccess(actor, area);
        Company company = actor.getCompany();
        String tenantKey = company != null && company.getId() != null
                ? "company:" + company.getId()
                : "global";

        if (area == UserUiPreferenceArea.PMS_DASHBOARD) {
            if (requestedContext != null && !requestedContext.isBlank()) {
                throw new IllegalArgumentException("PMS dashboard context is derived from propertyId.");
            }
            if (company == null || company.getId() == null) {
                throw new AccessDeniedException("A company assignment is required for PMS preferences.");
            }
            if (propertyId == null || propertyId <= 0) {
                throw new IllegalArgumentException("A positive propertyId is required for PMS preferences.");
            }
            HotelProperty property = propertyRepository.findByIdAndCompany_Id(propertyId, company.getId())
                    .orElseThrow(() -> new AccessDeniedException("PMS property is not available."));
            if (pmsPropertyAccess != null) pmsPropertyAccess.require(pmsPropertyAccess.access(actor.getUsername()), propertyId, null, false);
            return new PreferenceContext(actor, company, property, tenantKey, "property:" + propertyId, area);
        }

        if (propertyId != null) {
            throw new IllegalArgumentException("propertyId is only valid for PMS dashboard preferences.");
        }
        return new PreferenceContext(
                actor,
                company,
                null,
                tenantKey,
                requireAllowedContext(area, requestedContext),
                area
        );
    }

    private void requireAreaAccess(User actor, UserUiPreferenceArea area) {
        String requiredPage = switch (area) {
            case APP_TABS -> null;
            case TIME_USER_DASHBOARD -> UserPermissionService.PAGE_DASHBOARD;
            case TIME_ADMIN_DASHBOARD -> UserPermissionService.PAGE_ADMIN_DASHBOARD;
            case PMS_DASHBOARD -> UserPermissionService.PAGE_PMS;
        };
        if (requiredPage != null) {
            userPermissionService.assertPageAccess(
                    actor,
                    requiredPage,
                    UserPermissionService.ACCESS_VIEW,
                    "The requested preference area is not available."
            );
        }
    }

    private Optional<UserUiPreference> find(PreferenceContext context) {
        return preferenceRepository.findByUser_IdAndTenantKeyAndAreaAndContextKey(
                context.actor().getId(),
                context.tenantKey(),
                context.area(),
                context.contextKey()
        ).map(preference -> {
            requireStoredScope(preference, context);
            return preference;
        });
    }

    private void requireStoredScope(UserUiPreference preference, PreferenceContext context) {
        if (preference.getUser() == null
                || !Objects.equals(preference.getUser().getId(), context.actor().getId())
                || !Objects.equals(preference.getTenantKey(), context.tenantKey())
                || !Objects.equals(companyId(preference.getCompany()), companyId(context.company()))
                || !Objects.equals(propertyId(preference.getProperty()), propertyId(context.property()))) {
            throw new AccessDeniedException("Stored preference does not belong to the current tenant context.");
        }
    }

    private boolean isAllowedView(User actor, String viewKey) {
        return userPermissionService.isKnownPageKey(viewKey)
                && userPermissionService.hasPageAccess(
                        actor,
                        viewKey,
                        UserPermissionService.ACCESS_VIEW
                );
    }

    private boolean canRestoreTab(User actor, JsonNode tab) {
        String viewKey = tab.path("viewKey").asText();
        if (!isAllowedView(actor, viewKey)) {
            return false;
        }
        if (!UserPermissionService.PAGE_PMS.equals(viewKey)) {
            return true;
        }
        var hotelAccess = pmsPropertyAccess == null ? null : pmsPropertyAccess.access(actor.getUsername());
        if (hotelAccess != null && !hotelAccess.any()) return false;
        JsonNode propertyIdNode = tab.path("params").get("propertyId");
        if (propertyIdNode == null || propertyIdNode.isNull()) {
            return true;
        }
        if (!propertyIdNode.isIntegralNumber()
                || !propertyIdNode.canConvertToLong()
                || propertyIdNode.longValue() <= 0
                || actor.getCompany() == null
                || actor.getCompany().getId() == null) {
            return false;
        }
        if (hotelAccess != null && !hotelAccess.any(propertyIdNode.longValue())) return false;
        return propertyRepository.findByIdAndCompany_Id(
                propertyIdNode.longValue(),
                actor.getCompany().getId()
        ).isPresent();
    }

    private void requirePmsTabPropertiesInTenant(User actor, JsonNode payload) {
        Set<Long> propertyIds = payloadValidator.pmsPropertyIdsFromTabs(payload);
        if (propertyIds.isEmpty()) {
            return;
        }
        if (actor.getCompany() == null || actor.getCompany().getId() == null) {
            throw new AccessDeniedException("A company assignment is required for PMS tabs.");
        }
        for (Long propertyId : propertyIds) {
            if (pmsPropertyAccess != null) pmsPropertyAccess.require(pmsPropertyAccess.access(actor.getUsername()), propertyId, null, false);
            propertyRepository.findByIdAndCompany_Id(propertyId, actor.getCompany().getId())
                    .orElseThrow(() -> new AccessDeniedException("PMS property is not available."));
        }
    }

    private void requireValidRequest(UiPreferenceUpdateRequest request) {
        if (request == null
                || request.schemaVersion() == null
                || request.revision() == null
                || request.payload() == null) {
            throw new IllegalArgumentException("Preference request is incomplete.");
        }
        if (request.schemaVersion() != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported preference schema version.");
        }
        if (request.revision() < 0) {
            throw new IllegalArgumentException("Preference revision must not be negative.");
        }
    }

    private String requireAllowedContext(UserUiPreferenceArea area, String requestedContext) {
        if (requestedContext == null || requestedContext.isBlank()) {
            throw new IllegalArgumentException("Preference context is required for this area.");
        }
        String normalized = requestedContext.trim();
        Set<String> allowedContexts = ALLOWED_CONTEXTS.get(area);
        if (allowedContexts == null || !allowedContexts.contains(normalized)) {
            throw new IllegalArgumentException("Preference context is not supported for this area.");
        }
        return normalized;
    }

    private UiPreferenceResponse toResponse(
            UserUiPreference preference,
            String contextKey,
            JsonNode payload
    ) {
        return new UiPreferenceResponse(
                preference.getArea(),
                contextKey,
                preference.getSchemaVersion(),
                preference.getRevision(),
                payload,
                preference.getUpdatedAt()
        );
    }

    private Long companyId(Company company) {
        return company != null ? company.getId() : null;
    }

    private Long propertyId(HotelProperty property) {
        return property != null ? property.getId() : null;
    }

    private UiPreferenceRevisionConflictException revisionConflict() {
        return new UiPreferenceRevisionConflictException("Preference revision conflict.");
    }

    private UiPreferenceRevisionConflictException revisionConflict(Exception cause) {
        return new UiPreferenceRevisionConflictException("Preference revision conflict.", cause);
    }

    private record PreferenceContext(
            User actor,
            Company company,
            HotelProperty property,
            String tenantKey,
            String contextKey,
            UserUiPreferenceArea area
    ) {
    }
}
