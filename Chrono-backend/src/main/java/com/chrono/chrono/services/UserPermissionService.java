package com.chrono.chrono.services;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.utils.RegistrationFeatures;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class UserPermissionService {

    public static final String ACCESS_NONE = "NONE";
    public static final String ACCESS_VIEW = "VIEW";
    public static final String ACCESS_MANAGE = "MANAGE";

    public static final String PAGE_DASHBOARD = "dashboard";
    public static final String PAGE_PERSONAL_DATA = "personalData";
    public static final String PAGE_PAYSLIPS = "payslips";
    public static final String PAGE_DEMO_TOUR = "demoTour";
    public static final String PAGE_PRINT_REPORT = "printReport";
    public static final String PAGE_SUPPLY_CHAIN = "supplyChain";
    public static final String PAGE_ADMIN_DASHBOARD = "adminDashboard";
    public static final String PAGE_ADMIN_USERS = "adminUsers";
    public static final String PAGE_ADMIN_CHANGE_PASSWORD = "adminChangePassword";
    public static final String PAGE_ADMIN_CUSTOMERS = "adminCustomers";
    public static final String PAGE_ADMIN_PROJECTS = "adminProjects";
    public static final String PAGE_ADMIN_TASKS = "adminTasks";
    public static final String PAGE_ADMIN_ANALYTICS = "adminAnalytics";
    public static final String PAGE_ADMIN_ACCOUNTING = "adminAccounting";
    public static final String PAGE_CHRONO_TWO = "chronoTwo";
    public static final String PAGE_CRM = "crm";
    public static final String PAGE_BANKING = "banking";
    public static final String PAGE_ADMIN_PROJECT_REPORT = "adminProjectReport";
    public static final String PAGE_COMPANY_MANAGEMENT = "companyManagement";
    public static final String PAGE_ADMIN_PAYSLIPS = "adminPayslips";
    public static final String PAGE_ADMIN_SCHEDULE = "adminSchedule";
    public static final String PAGE_ADMIN_PRINT_SCHEDULE = "adminPrintSchedule";
    public static final String PAGE_ADMIN_KNOWLEDGE = "adminKnowledge";
    public static final String PAGE_COMPANY_SETTINGS = "companySettings";
    public static final String PAGE_ADMIN_SHIFT_RULES = "adminShiftRules";
    public static final String PAGE_ADMIN_IMPORT_TIMES = "adminImportTimes";

    private static final List<String> PAGE_ORDER = List.of(
            PAGE_DASHBOARD,
            PAGE_PERSONAL_DATA,
            PAGE_PAYSLIPS,
            PAGE_DEMO_TOUR,
            PAGE_PRINT_REPORT,
            PAGE_SUPPLY_CHAIN,
            PAGE_ADMIN_DASHBOARD,
            PAGE_ADMIN_USERS,
            PAGE_ADMIN_CHANGE_PASSWORD,
            PAGE_ADMIN_CUSTOMERS,
            PAGE_ADMIN_PROJECTS,
            PAGE_ADMIN_TASKS,
            PAGE_ADMIN_ANALYTICS,
            PAGE_ADMIN_ACCOUNTING,
            PAGE_CHRONO_TWO,
            PAGE_CRM,
            PAGE_BANKING,
            PAGE_ADMIN_PROJECT_REPORT,
            PAGE_COMPANY_MANAGEMENT,
            PAGE_ADMIN_PAYSLIPS,
            PAGE_ADMIN_SCHEDULE,
            PAGE_ADMIN_PRINT_SCHEDULE,
            PAGE_ADMIN_KNOWLEDGE,
            PAGE_COMPANY_SETTINGS,
            PAGE_ADMIN_SHIFT_RULES,
            PAGE_ADMIN_IMPORT_TIMES
    );

    private static final Set<String> ADMIN_ONLY_PAGES = Set.of(
            PAGE_ADMIN_DASHBOARD,
            PAGE_ADMIN_USERS,
            PAGE_ADMIN_CUSTOMERS,
            PAGE_ADMIN_TASKS,
            PAGE_ADMIN_ANALYTICS,
            PAGE_ADMIN_PAYSLIPS,
            PAGE_ADMIN_SCHEDULE,
            PAGE_ADMIN_PRINT_SCHEDULE,
            PAGE_ADMIN_KNOWLEDGE,
            PAGE_COMPANY_SETTINGS,
            PAGE_ADMIN_SHIFT_RULES,
            PAGE_ADMIN_IMPORT_TIMES
    );

    private static final Set<String> SUPERADMIN_ONLY_PAGES = Set.of(
            PAGE_COMPANY_MANAGEMENT
    );

    private static final Map<String, String> FEATURE_PAGE_MAPPING = Map.ofEntries(
            Map.entry(PAGE_SUPPLY_CHAIN, "supplyChain"),
            Map.entry(PAGE_ADMIN_CUSTOMERS, "projects"),
            Map.entry(PAGE_ADMIN_PROJECTS, "projects"),
            Map.entry(PAGE_ADMIN_TASKS, "projects"),
            Map.entry(PAGE_ADMIN_PROJECT_REPORT, "projects"),
            Map.entry(PAGE_ADMIN_ANALYTICS, "analytics"),
            Map.entry(PAGE_ADMIN_ACCOUNTING, "accounting"),
            Map.entry(PAGE_CHRONO_TWO, "chrono2"),
            Map.entry(PAGE_CRM, "crm"),
            Map.entry(PAGE_BANKING, "banking"),
            Map.entry(PAGE_ADMIN_PAYSLIPS, "payroll"),
            Map.entry(PAGE_ADMIN_SCHEDULE, "roster"),
            Map.entry(PAGE_ADMIN_PRINT_SCHEDULE, "roster"),
            Map.entry(PAGE_ADMIN_SHIFT_RULES, "roster")
    );

    public Map<String, String> resolvePagePermissions(User user) {
        if (user == null) {
            return emptyPermissions();
        }

        if (isSuperAdmin(user)) {
            return allManagePermissions();
        }

        if (needsDefaultPagePermissionBackfill(user)) {
            return buildDefaultPagePermissions(user);
        }

        LinkedHashMap<String, String> resolved = defaultPermissionsFor(user);
        resolved.putAll(sanitizeRawPermissions(user.getPagePermissions()));
        applyRoleRestrictions(user, resolved);
        applyFeatureRestrictions(user, resolved);
        return resolved;
    }

    public Map<String, String> buildDefaultPagePermissions(User user) {
        if (user == null) {
            return emptyPermissions();
        }

        if (isSuperAdmin(user)) {
            return allManagePermissions();
        }

        LinkedHashMap<String, String> defaults = defaultPermissionsFor(user);
        applyRoleRestrictions(user, defaults);
        applyFeatureRestrictions(user, defaults);
        return defaults;
    }

    public boolean needsDefaultPagePermissionBackfill(User user) {
        if (user == null) {
            return false;
        }

        LinkedHashMap<String, String> sanitized = sanitizeRawPermissions(user.getPagePermissions());
        if (sanitized.isEmpty()) {
            return true;
        }

        return sanitized.keySet().containsAll(PAGE_ORDER)
                && sanitized.values().stream()
                .allMatch(accessLevel -> ACCESS_NONE.equals(normalizeAccessLevel(accessLevel)));
    }

    public Map<String, String> resolvePermissionsForPersistence(User user, Map<String, String> requestedPermissions) {
        if (user == null) {
            return emptyPermissions();
        }

        if (isSuperAdmin(user)) {
            return allManagePermissions();
        }

        LinkedHashMap<String, String> resolved = defaultPermissionsFor(user);
        resolved.putAll(sanitizeRawPermissions(requestedPermissions));
        applyRoleRestrictions(user, resolved);
        applyFeatureRestrictions(user, resolved);
        return resolved;
    }

    public boolean hasPageAccess(User user, String pageKey, String requiredLevel) {
        if (user == null || pageKey == null || pageKey.isBlank()) {
            return false;
        }

        if (isSuperAdmin(user)) {
            return true;
        }

        String grantedLevel = resolvePagePermissions(user).getOrDefault(pageKey, ACCESS_NONE);
        return accessRank(grantedLevel) >= accessRank(requiredLevel);
    }

    public void assertPageAccess(User user, String pageKey, String requiredLevel, String message) {
        if (!hasPageAccess(user, pageKey, requiredLevel)) {
            throw new AccessDeniedException(message != null && !message.isBlank()
                    ? message
                    : "Missing permission for page: " + pageKey);
        }
    }

    public boolean hasAnyPageAccess(User user, String requiredLevel, String... pageKeys) {
        if (pageKeys == null || pageKeys.length == 0) {
            return false;
        }

        for (String pageKey : pageKeys) {
            if (hasPageAccess(user, pageKey, requiredLevel)) {
                return true;
            }
        }
        return false;
    }

    public void assertAnyPageAccess(User user, String requiredLevel, String message, String... pageKeys) {
        if (!hasAnyPageAccess(user, requiredLevel, pageKeys)) {
            throw new AccessDeniedException(message != null && !message.isBlank()
                    ? message
                    : "Missing permission for any allowed page.");
        }
    }

    private LinkedHashMap<String, String> defaultPermissionsFor(User user) {
        LinkedHashMap<String, String> defaults = emptyPermissions();

        defaults.put(PAGE_DASHBOARD, ACCESS_MANAGE);
        defaults.put(PAGE_PERSONAL_DATA, ACCESS_MANAGE);
        defaults.put(PAGE_PAYSLIPS, ACCESS_VIEW);
        defaults.put(PAGE_DEMO_TOUR, ACCESS_VIEW);
        defaults.put(PAGE_PRINT_REPORT, ACCESS_VIEW);

        if (isAdmin(user)) {
            defaults.put(PAGE_ADMIN_DASHBOARD, ACCESS_MANAGE);
            defaults.put(PAGE_ADMIN_USERS, ACCESS_MANAGE);
            defaults.put(PAGE_ADMIN_CHANGE_PASSWORD, ACCESS_MANAGE);
            defaults.put(PAGE_ADMIN_KNOWLEDGE, ACCESS_MANAGE);
            defaults.put(PAGE_COMPANY_SETTINGS, ACCESS_MANAGE);
            defaults.put(PAGE_ADMIN_IMPORT_TIMES, ACCESS_MANAGE);

            if (hasCompanyFeature(user, "projects")) {
                defaults.put(PAGE_ADMIN_CUSTOMERS, ACCESS_MANAGE);
                defaults.put(PAGE_ADMIN_PROJECTS, ACCESS_MANAGE);
                defaults.put(PAGE_ADMIN_TASKS, ACCESS_MANAGE);
                defaults.put(PAGE_ADMIN_PROJECT_REPORT, ACCESS_MANAGE);
            }
            if (hasCompanyFeature(user, "analytics")) {
                defaults.put(PAGE_ADMIN_ANALYTICS, ACCESS_MANAGE);
            }
            if (hasCompanyFeature(user, "accounting")) {
                defaults.put(PAGE_ADMIN_ACCOUNTING, ACCESS_MANAGE);
            }
            if (hasCompanyFeature(user, "chrono2")) {
                defaults.put(PAGE_CHRONO_TWO, ACCESS_MANAGE);
            }
            if (hasCompanyFeature(user, "supplyChain")) {
                defaults.put(PAGE_SUPPLY_CHAIN, ACCESS_MANAGE);
            }
            if (hasCompanyFeature(user, "crm")) {
                defaults.put(PAGE_CRM, ACCESS_MANAGE);
            }
            if (hasCompanyFeature(user, "banking")) {
                defaults.put(PAGE_BANKING, ACCESS_MANAGE);
            }
            if (hasCompanyFeature(user, "payroll")) {
                defaults.put(PAGE_ADMIN_PAYSLIPS, ACCESS_MANAGE);
            }
            if (hasCompanyFeature(user, "roster")) {
                defaults.put(PAGE_ADMIN_SCHEDULE, ACCESS_MANAGE);
                defaults.put(PAGE_ADMIN_PRINT_SCHEDULE, ACCESS_MANAGE);
                defaults.put(PAGE_ADMIN_SHIFT_RULES, ACCESS_MANAGE);
            }
        }

        if (isPayrollAdmin(user) && hasCompanyFeature(user, "payroll")) {
            defaults.put(PAGE_ADMIN_PAYSLIPS, ACCESS_MANAGE);
        }

        return defaults;
    }

    private LinkedHashMap<String, String> sanitizeRawPermissions(Map<String, String> rawPermissions) {
        LinkedHashMap<String, String> sanitized = new LinkedHashMap<>();
        if (rawPermissions == null || rawPermissions.isEmpty()) {
            return sanitized;
        }

        for (Map.Entry<String, String> entry : rawPermissions.entrySet()) {
            String pageKey = entry.getKey();
            if (!PAGE_ORDER.contains(pageKey)) {
                continue;
            }
            sanitized.put(pageKey, normalizeAccessLevel(entry.getValue()));
        }
        return sanitized;
    }

    private void applyRoleRestrictions(User user, Map<String, String> permissions) {
        boolean admin = isAdmin(user);
        boolean payrollAdmin = isPayrollAdmin(user);
        boolean superAdmin = isSuperAdmin(user);

        for (String pageKey : PAGE_ORDER) {
            if (SUPERADMIN_ONLY_PAGES.contains(pageKey) && !superAdmin) {
                permissions.put(pageKey, ACCESS_NONE);
                continue;
            }
            if (ADMIN_ONLY_PAGES.contains(pageKey) && !admin) {
                if (PAGE_ADMIN_PAYSLIPS.equals(pageKey) && payrollAdmin) {
                    continue;
                }
                permissions.put(pageKey, ACCESS_NONE);
            }
        }
    }

    private void applyFeatureRestrictions(User user, Map<String, String> permissions) {
        if (isSuperAdmin(user)) {
            return;
        }

        for (Map.Entry<String, String> entry : FEATURE_PAGE_MAPPING.entrySet()) {
            if (!hasCompanyFeature(user, entry.getValue())) {
                permissions.put(entry.getKey(), ACCESS_NONE);
            }
        }
    }

    private boolean hasCompanyFeature(User user, String featureKey) {
        if (featureKey == null || featureKey.isBlank()) {
            return true;
        }
        if (user == null) {
            return false;
        }
        if (isSuperAdmin(user)) {
            return true;
        }

        Company company = user.getCompany();
        if (company == null) {
            return false;
        }

        Set<String> features = new LinkedHashSet<>(RegistrationFeatures.ALWAYS_AVAILABLE_FEATURES);
        features.addAll(RegistrationFeatures.sanitizeOptionalFeatures(company.getEnabledFeatures()));
        return features.contains(featureKey);
    }

    private LinkedHashMap<String, String> emptyPermissions() {
        LinkedHashMap<String, String> permissions = new LinkedHashMap<>();
        for (String pageKey : PAGE_ORDER) {
            permissions.put(pageKey, ACCESS_NONE);
        }
        return permissions;
    }

    private LinkedHashMap<String, String> allManagePermissions() {
        LinkedHashMap<String, String> permissions = new LinkedHashMap<>();
        for (String pageKey : PAGE_ORDER) {
            permissions.put(pageKey, ACCESS_MANAGE);
        }
        return permissions;
    }

    private String normalizeAccessLevel(String accessLevel) {
        if (accessLevel == null || accessLevel.isBlank()) {
            return ACCESS_NONE;
        }

        String normalized = accessLevel.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case ACCESS_VIEW -> ACCESS_VIEW;
            case ACCESS_MANAGE -> ACCESS_MANAGE;
            default -> ACCESS_NONE;
        };
    }

    private int accessRank(String accessLevel) {
        String normalized = normalizeAccessLevel(accessLevel);
        return switch (normalized) {
            case ACCESS_VIEW -> 1;
            case ACCESS_MANAGE -> 2;
            default -> 0;
        };
    }

    private boolean isAdmin(User user) {
        if (user == null) {
            return false;
        }
        return user.getRoles().stream()
                .map(Role::getRoleName)
                .anyMatch(roleName -> "ROLE_ADMIN".equals(roleName) || "ROLE_SUPERADMIN".equals(roleName));
    }

    private boolean isSuperAdmin(User user) {
        if (user == null) {
            return false;
        }
        return user.getRoles().stream()
                .map(Role::getRoleName)
                .anyMatch("ROLE_SUPERADMIN"::equals);
    }

    private boolean isPayrollAdmin(User user) {
        if (user == null) {
            return false;
        }
        return user.getRoles().stream()
                .map(Role::getRoleName)
                .anyMatch("ROLE_PAYROLL_ADMIN"::equals);
    }
}
