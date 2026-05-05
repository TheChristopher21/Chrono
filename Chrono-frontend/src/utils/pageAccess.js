export const ACCESS_NONE = "NONE";
export const ACCESS_VIEW = "VIEW";
export const ACCESS_MANAGE = "MANAGE";

const ACCESS_RANK = {
    [ACCESS_NONE]: 0,
    [ACCESS_VIEW]: 1,
    [ACCESS_MANAGE]: 2,
};

export const PAGE_CATALOG = [
    {
        key: "dashboard",
        label: "Mein Dashboard",
        description: "Wochenübersicht, Zeiterfassung und persönliche Auswertung.",
        path: "/dashboard",
        audiences: ["user"],
        dashboardContexts: ["user"],
        mobileContexts: ["user"],
        order: 10,
        supportsManage: true,
        group: "Benutzer",
        icon: "Zeit",
    },
    {
        key: "personalData",
        label: "Meine Daten",
        description: "Persönliche Daten, Kontaktangaben und Passwort.",
        path: "/personal-data",
        audiences: ["user", "admin"],
        dashboardContexts: ["user"],
        mobileContexts: ["user"],
        order: 20,
        supportsManage: true,
        group: "Benutzer",
        icon: "Profil",
    },
    {
        key: "payslips",
        label: "Abrechnungen",
        description: "Lohnabrechnungen und Dokumente ansehen.",
        path: "/payslips",
        audiences: ["user", "admin"],
        dashboardContexts: ["user"],
        mobileContexts: ["user"],
        order: 30,
        supportsManage: false,
        group: "Benutzer",
        icon: "Lohn",
    },
    {
        key: "demoTour",
        label: "Demo-Tour",
        description: "Kurze Einführung in Funktionen und Abläufe.",
        path: "/demo-tour",
        audiences: ["user"],
        dashboardContexts: ["user"],
        mobileContexts: [],
        order: 40,
        supportsManage: false,
        group: "Benutzer",
        icon: "Tour",
    },
    {
        key: "printReport",
        label: "Zeitbericht",
        description: "Berichte und Ausdrucke öffnen.",
        path: "/print-report",
        audiences: ["user"],
        dashboardContexts: ["user"],
        mobileContexts: [],
        order: 50,
        supportsManage: false,
        group: "Benutzer",
        icon: "Report",
    },
    {
        key: "supplyChain",
        label: "Supply Chain",
        description: "Bestände, Wareneingang und operative Lieferkette.",
        path: "/workspace/supply-chain",
        featureKey: "supplyChain",
        audiences: ["user", "admin"],
        dashboardContexts: ["user", "admin"],
        mobileContexts: ["user", "admin"],
        order: 60,
        supportsManage: true,
        group: "Module",
        icon: "SC",
    },
    {
        key: "adminDashboard",
        label: "Admin-Start",
        description: "Teamübersicht, Inbox und Kennzahlen.",
        path: "/admin/dashboard",
        audiences: ["admin"],
        dashboardContexts: ["admin"],
        mobileContexts: ["admin"],
        order: 100,
        supportsManage: true,
        group: "Admin",
        icon: "Admin",
    },
    {
        key: "adminUsers",
        label: "Benutzerverwaltung",
        description: "Benutzer, Rollen und Zugriffe verwalten.",
        path: "/admin/users",
        audiences: ["admin"],
        dashboardContexts: ["admin"],
        mobileContexts: ["admin"],
        order: 110,
        supportsManage: true,
        group: "Admin",
        icon: "Team",
    },
    {
        key: "adminChangePassword",
        label: "Admin-Passwort",
        description: "Eigenes Admin-Passwort ändern.",
        path: "/admin/change-password",
        userAssignable: true,
        audiences: ["admin"],
        dashboardContexts: ["user", "admin"],
        mobileContexts: [],
        order: 120,
        supportsManage: true,
        group: "Admin",
        icon: "PW",
    },
    {
        key: "adminCustomers",
        label: "Kunden",
        description: "Kundenverwaltung und Zuordnungen.",
        path: "/admin/customers",
        featureKey: "projects",
        audiences: ["admin"],
        dashboardContexts: ["admin"],
        mobileContexts: [],
        order: 130,
        supportsManage: true,
        group: "Projekte",
        icon: "Kunden",
    },
    {
        key: "adminProjects",
        label: "Projekte",
        description: "Projekte planen und pflegen.",
        path: "/admin/projects",
        featureKey: "projects",
        userAssignable: true,
        audiences: ["admin"],
        dashboardContexts: ["user", "admin"],
        mobileContexts: ["admin"],
        order: 140,
        supportsManage: true,
        group: "Projekte",
        icon: "Projekte",
    },
    {
        key: "adminTasks",
        label: "Aufgaben",
        description: "Aufgaben und operative Arbeitspakete.",
        path: "/admin/tasks",
        featureKey: "projects",
        audiences: ["admin"],
        dashboardContexts: ["admin"],
        mobileContexts: [],
        order: 150,
        supportsManage: true,
        group: "Projekte",
        icon: "Tasks",
    },
    {
        key: "adminProjectReport",
        label: "Projektbericht",
        description: "Projektberichte und Auswertungen.",
        path: "/admin/project-report",
        featureKey: "projects",
        userAssignable: true,
        audiences: ["admin"],
        dashboardContexts: ["user", "admin"],
        mobileContexts: [],
        order: 160,
        supportsManage: false,
        group: "Projekte",
        icon: "Bericht",
    },
    {
        key: "adminAnalytics",
        label: "Analytics",
        description: "Kennzahlen, Trends und Reports ansehen.",
        path: "/admin/analytics",
        featureKey: "analytics",
        audiences: ["admin"],
        dashboardContexts: ["admin"],
        mobileContexts: ["admin"],
        order: 170,
        supportsManage: false,
        group: "Admin",
        icon: "BI",
    },
    {
        key: "adminAccounting",
        label: "Finanzbuchhaltung",
        description: "Buchhaltung und finanzielle Prozesse.",
        path: "/admin/accounting",
        featureKey: "accounting",
        userAssignable: true,
        audiences: ["admin"],
        dashboardContexts: ["user", "admin"],
        mobileContexts: [],
        order: 180,
        supportsManage: true,
        group: "Module",
        icon: "FiBu",
    },
    {
        key: "chronoTwo",
        label: "Chrono 2.0",
        description: "Zusatzmodul Chrono 2.0.",
        path: "/admin/chrono-two",
        featureKey: "chrono2",
        userAssignable: true,
        audiences: ["admin"],
        dashboardContexts: ["user", "admin"],
        mobileContexts: [],
        order: 190,
        supportsManage: true,
        group: "Module",
        icon: "C2",
    },
    {
        key: "crm",
        label: "CRM & Marketing",
        description: "Leads, Pipeline und Marketingboard.",
        path: "/admin/crm",
        featureKey: "crm",
        userAssignable: true,
        audiences: ["admin"],
        dashboardContexts: ["user", "admin"],
        mobileContexts: [],
        order: 200,
        supportsManage: true,
        group: "Module",
        icon: "CRM",
    },
    {
        key: "banking",
        label: "Zahlungsverkehr",
        description: "Banking, Zahlungen und Freigaben.",
        path: "/admin/banking",
        featureKey: "banking",
        userAssignable: true,
        audiences: ["admin"],
        dashboardContexts: ["user", "admin"],
        mobileContexts: [],
        order: 210,
        supportsManage: true,
        group: "Module",
        icon: "Bank",
    },
    {
        key: "adminPayslips",
        label: "Payroll",
        description: "Abrechnungen erstellen und verwalten.",
        path: "/admin/payslips",
        featureKey: "payroll",
        audiences: ["admin", "payroll"],
        dashboardContexts: ["admin"],
        mobileContexts: [],
        order: 220,
        supportsManage: true,
        group: "Module",
        icon: "HR",
    },
    {
        key: "adminSchedule",
        label: "Dienstplan",
        description: "Schichten und Einsatzplanung steuern.",
        path: "/admin/schedule",
        featureKey: "roster",
        audiences: ["admin"],
        dashboardContexts: ["admin"],
        mobileContexts: [],
        order: 230,
        supportsManage: true,
        group: "Module",
        icon: "Plan",
    },
    {
        key: "adminPrintSchedule",
        label: "Dienstplan drucken",
        description: "Dienstpläne exportieren und drucken.",
        path: "/admin/print-schedule",
        featureKey: "roster",
        audiences: ["admin"],
        dashboardContexts: ["admin"],
        mobileContexts: [],
        order: 240,
        supportsManage: false,
        group: "Module",
        icon: "Print",
    },
    {
        key: "adminShiftRules",
        label: "Schichtregeln",
        description: "Planregeln und Vorgaben pflegen.",
        path: "/admin/shift-rules",
        featureKey: "roster",
        audiences: ["admin"],
        dashboardContexts: ["admin"],
        mobileContexts: [],
        order: 250,
        supportsManage: true,
        group: "Module",
        icon: "Regeln",
    },
    {
        key: "adminKnowledge",
        label: "Firmenwissen",
        description: "Interne Wissensbasis und KI-Inhalte.",
        path: "/admin/knowledge",
        audiences: ["admin"],
        dashboardContexts: ["admin"],
        mobileContexts: [],
        order: 260,
        supportsManage: true,
        group: "Admin",
        icon: "Wissen",
    },
    {
        key: "companySettings",
        label: "Firmeneinstellungen",
        description: "Globale Firmen- und Systemkonfiguration.",
        path: "/admin/company-settings",
        audiences: ["admin"],
        dashboardContexts: ["admin"],
        mobileContexts: [],
        order: 270,
        supportsManage: true,
        group: "Admin",
        icon: "Setup",
    },
    {
        key: "adminImportTimes",
        label: "Zeiten importieren",
        description: "Zeitdaten importieren und zuordnen.",
        path: "/admin/import-times",
        audiences: ["admin"],
        dashboardContexts: ["admin"],
        mobileContexts: [],
        order: 280,
        supportsManage: true,
        group: "Admin",
        icon: "Import",
    },
    {
        key: "companyManagement",
        label: "Firmenverwaltung",
        description: "Firmen und globale Chrono-Nutzung steuern.",
        path: "/admin/company",
        audiences: ["superadmin"],
        dashboardContexts: [],
        mobileContexts: [],
        order: 290,
        supportsManage: true,
        group: "Superadmin",
        icon: "Firma",
    },
];

const USER_DEFAULTS = {
    dashboard: ACCESS_MANAGE,
    personalData: ACCESS_MANAGE,
    payslips: ACCESS_VIEW,
    demoTour: ACCESS_VIEW,
    printReport: ACCESS_VIEW,
};

const ADMIN_DEFAULTS = {
    adminDashboard: ACCESS_MANAGE,
    adminUsers: ACCESS_MANAGE,
    adminChangePassword: ACCESS_MANAGE,
    adminKnowledge: ACCESS_MANAGE,
    companySettings: ACCESS_MANAGE,
    adminImportTimes: ACCESS_MANAGE,
};

const FEATURE_DEFAULTS = {
    supplyChain: ACCESS_MANAGE,
    adminCustomers: ACCESS_MANAGE,
    adminProjects: ACCESS_MANAGE,
    adminTasks: ACCESS_MANAGE,
    adminProjectReport: ACCESS_VIEW,
    adminAnalytics: ACCESS_VIEW,
    adminAccounting: ACCESS_MANAGE,
    chronoTwo: ACCESS_MANAGE,
    crm: ACCESS_MANAGE,
    banking: ACCESS_MANAGE,
    adminPayslips: ACCESS_MANAGE,
    adminSchedule: ACCESS_MANAGE,
    adminPrintSchedule: ACCESS_VIEW,
    adminShiftRules: ACCESS_MANAGE,
};

export const normalizeAccessLevel = (value) => {
    const normalized = String(value ?? ACCESS_NONE).trim().toUpperCase();
    if (normalized === ACCESS_MANAGE) return ACCESS_MANAGE;
    if (normalized === ACCESS_VIEW) return ACCESS_VIEW;
    return ACCESS_NONE;
};

export const isSuperAdminUser = (user) => Boolean(user?.roles?.includes("ROLE_SUPERADMIN"));

export const isPayrollAdminUser = (user) => Boolean(user?.roles?.includes("ROLE_PAYROLL_ADMIN"));

export const isAdminUser = (user) => Boolean(
    user?.roles?.includes("ROLE_ADMIN") || user?.roles?.includes("ROLE_SUPERADMIN")
);

export const getCompanyFeatureList = (userOrFeatureKeys) => {
    const featureKeys = userOrFeatureKeys?.companyFeatureKeys ?? userOrFeatureKeys;
    if (!featureKeys) return [];
    if (Array.isArray(featureKeys)) return featureKeys;
    return Object.values(featureKeys);
};

export const hasFeatureAccess = (user, featureKey) => {
    if (!featureKey) return true;
    if (isSuperAdminUser(user)) return true;
    return getCompanyFeatureList(user).includes(featureKey);
};

export const hasPageAccess = (user, pageKey, requiredLevel = ACCESS_VIEW) => {
    if (!pageKey) return true;
    if (isSuperAdminUser(user)) return true;
    const grantedLevel = normalizeAccessLevel(user?.pagePermissions?.[pageKey]);
    return (ACCESS_RANK[grantedLevel] ?? 0) >= (ACCESS_RANK[normalizeAccessLevel(requiredLevel)] ?? 0);
};

export const getPageDefinition = (pageKey) => PAGE_CATALOG.find((page) => page.key === pageKey) ?? null;

export const getRouteForPage = (pageKey) => getPageDefinition(pageKey)?.path ?? "/";

const translateValue = (t, key, fallback) => (
    typeof t === "function" ? t(key, fallback) : fallback
);

export const translatePageDefinition = (page, t) => {
    if (!page) return page;
    const groupFallback = page.group ?? "Seiten";

    return {
        ...page,
        label: translateValue(t, `pageCatalog.pages.${page.key}.label`, page.label),
        description: translateValue(t, `pageCatalog.pages.${page.key}.description`, page.description),
        group: translateValue(t, `pageCatalog.groups.${groupFallback}`, groupFallback),
    };
};

const isUserAssignablePage = (page) => {
    if (!page || page.audiences.includes("superadmin")) {
        return false;
    }
    if (typeof page.userAssignable === "boolean") {
        return page.userAssignable;
    }
    return page.audiences.includes("user");
};

const canRoleConfigurePage = (roleName, page) => {
    const normalizedRole = String(roleName ?? "ROLE_USER").toUpperCase();
    if (!page) return false;
    if (normalizedRole === "ROLE_SUPERADMIN") return true;
    if (page.audiences.includes("superadmin")) return false;
    if (normalizedRole === "ROLE_ADMIN") return true;
    if (normalizedRole === "ROLE_PAYROLL_ADMIN") {
        return page.key === "adminPayslips" || isUserAssignablePage(page);
    }
    return isUserAssignablePage(page);
};

export const getAccessChoicesForPage = (pageKey) => {
    const page = getPageDefinition(pageKey);
    if (!page) return [ACCESS_NONE, ACCESS_VIEW];
    return page.supportsManage
        ? [ACCESS_NONE, ACCESS_VIEW, ACCESS_MANAGE]
        : [ACCESS_NONE, ACCESS_VIEW];
};

export const buildDefaultPagePermissions = (roleName, companyFeatureKeys = []) => {
    const normalizedRole = String(roleName ?? "ROLE_USER").toUpperCase();
    const featureList = getCompanyFeatureList(companyFeatureKeys);
    const permissions = {};

    PAGE_CATALOG.forEach((page) => {
        permissions[page.key] = ACCESS_NONE;
    });

    if (normalizedRole === "ROLE_SUPERADMIN") {
        PAGE_CATALOG.forEach((page) => {
            permissions[page.key] = ACCESS_MANAGE;
        });
        return permissions;
    }

    Object.assign(permissions, USER_DEFAULTS);

    if (normalizedRole === "ROLE_ADMIN") {
        Object.assign(permissions, ADMIN_DEFAULTS);
    }

    if (normalizedRole === "ROLE_PAYROLL_ADMIN" && featureList.includes("payroll")) {
        permissions.adminPayslips = ACCESS_MANAGE;
    }

    PAGE_CATALOG.forEach((page) => {
        if (page.featureKey && !featureList.includes(page.featureKey) && normalizedRole !== "ROLE_SUPERADMIN") {
            permissions[page.key] = ACCESS_NONE;
            return;
        }

        if (!canRoleConfigurePage(normalizedRole, page)) {
            permissions[page.key] = ACCESS_NONE;
            return;
        }

        if (normalizedRole === "ROLE_ADMIN" && FEATURE_DEFAULTS[page.key]) {
            permissions[page.key] = FEATURE_DEFAULTS[page.key];
        }
    });

    return permissions;
};

export const normalizePagePermissionsForRole = (roleName, companyFeatureKeys = [], currentPermissions = {}) => {
    const defaults = buildDefaultPagePermissions(roleName, companyFeatureKeys);
    const normalizedRole = String(roleName ?? "ROLE_USER").toUpperCase();
    const featureList = getCompanyFeatureList(companyFeatureKeys);

    if (normalizedRole === "ROLE_SUPERADMIN") {
        return defaults;
    }

    PAGE_CATALOG.forEach((page) => {
        if (page.featureKey && !featureList.includes(page.featureKey)) {
            defaults[page.key] = ACCESS_NONE;
            return;
        }

        if (!canRoleConfigurePage(normalizedRole, page)) {
            defaults[page.key] = ACCESS_NONE;
            return;
        }

        const baseValue = defaults[page.key] ?? ACCESS_NONE;
        const requestedValue = normalizeAccessLevel(currentPermissions?.[page.key]);
        const requestedRank = ACCESS_RANK[requestedValue] ?? 0;
        const maxChoices = getAccessChoicesForPage(page.key);
        const maxValue = maxChoices[maxChoices.length - 1];
        const maxRank = ACCESS_RANK[maxValue] ?? 0;
        const effectiveValue = requestedRank > maxRank ? maxValue : requestedValue;

        defaults[page.key] = effectiveValue || baseValue;
    });

    return defaults;
};

export const getPermissionSectionsForRole = (roleName, companyFeatureKeys = [], t) => {
    const normalizedRole = String(roleName ?? "ROLE_USER").toUpperCase();
    if (normalizedRole === "ROLE_SUPERADMIN") {
        return [];
    }

    const featureList = getCompanyFeatureList(companyFeatureKeys);

    const visiblePages = PAGE_CATALOG
        .filter((page) => canRoleConfigurePage(normalizedRole, page))
        .filter((page) => !page.featureKey || featureList.includes(page.featureKey))
        .sort((left, right) => left.order - right.order);

    const grouped = new Map();
    visiblePages.forEach((page) => {
        const groupName = translateValue(t, `pageCatalog.groups.${page.group ?? "Seiten"}`, page.group ?? "Seiten");
        const existing = grouped.get(groupName) ?? [];
        existing.push(translatePageDefinition(page, t));
        grouped.set(groupName, existing);
    });

    return Array.from(grouped.entries()).map(([group, pages]) => ({ group, pages }));
};

export const getDashboardPagesForContext = (user, context, t) => PAGE_CATALOG
    .filter((page) => page.dashboardContexts.includes(context))
    .filter((page) => hasFeatureAccess(user, page.featureKey))
    .filter((page) => hasPageAccess(user, page.key, ACCESS_VIEW))
    .sort((left, right) => left.order - right.order)
    .map((page) => translatePageDefinition(page, t));

export const getMobilePagesForContext = (user, context, t) => getDashboardPagesForContext(user, context, t)
    .filter((page) => page.mobileContexts.includes(context))
    .slice(0, 4);

const LANDING_PRIORITY = [
    "adminDashboard",
    "dashboard",
    "supplyChain",
    "adminProjects",
    "adminProjectReport",
    "adminAccounting",
    "chronoTwo",
    "crm",
    "banking",
    "payslips",
    "personalData",
    "adminChangePassword",
    "demoTour",
    "printReport",
    "adminAnalytics",
    "adminPayslips",
    "adminSchedule",
    "adminKnowledge",
    "companySettings",
    "adminImportTimes",
    "companyManagement",
];

export const getDefaultLandingPage = (user) => {
    if (!user) {
        return "/";
    }

    if (isSuperAdminUser(user) && hasPageAccess(user, "companyManagement", ACCESS_VIEW)) {
        return getRouteForPage("companyManagement");
    }

    if (!isSuperAdminUser(user)
        && isAdminUser(user)
        && hasPageAccess(user, "adminDashboard", ACCESS_VIEW)) {
        return getRouteForPage("adminDashboard");
    }

    if (hasPageAccess(user, "dashboard", ACCESS_VIEW)) {
        return user?.isPercentage ? "/percentage-punch" : getRouteForPage("dashboard");
    }

    const firstAllowedPage = LANDING_PRIORITY.find((pageKey) => {
        const page = getPageDefinition(pageKey);
        return page
            && hasFeatureAccess(user, page.featureKey)
            && hasPageAccess(user, pageKey, ACCESS_VIEW);
    });

    return firstAllowedPage ? getRouteForPage(firstAllowedPage) : "/";
};
