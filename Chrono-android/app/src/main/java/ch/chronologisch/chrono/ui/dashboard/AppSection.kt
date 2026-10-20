package ch.chronologisch.chrono.ui.dashboard

import ch.chronologisch.chrono.data.UserProfile

enum class AppSection(
    val title: String,
    val group: String,
    val subtitle: String,
    val primaryItems: List<String>,
    val adminOnly: Boolean = false,
    val superadminOnly: Boolean = false,
    val payrollRoleAllowed: Boolean = false,
) {
    TIME(
        title = "Heute",
        group = "Meine Arbeit",
        subtitle = "Stempeln, aktuelle Zuordnung, Wochenblick und Tagesnotiz.",
        primaryItems = listOf("Start / Ende", "Kunde & Projekt", "Woche", "Notiz"),
    ),
    WORK_MODEL(
        title = "Arbeitsmodell",
        group = "Meine Arbeit",
        subtitle = "Prozentmodell, Stundenlohn und persönliche Sollzeit.",
        primaryItems = listOf("Pensum", "Stundenlohn", "Saldo", "Sollzeit"),
    ),
    ABSENCES(
        title = "Absenzen",
        group = "Meine Arbeit",
        subtitle = "Urlaub, Krankheit und Korrekturanträge.",
        primaryItems = listOf("Urlaub", "Krankmeldung", "Korrekturen", "Freigabestatus"),
    ),
    PAYSLIPS(
        title = "Lohn",
        group = "Meine Arbeit",
        subtitle = "Lohnabrechnungen und Freigaben.",
        primaryItems = listOf("Meine Lohnabrechnungen", "Monatsstatus", "Download", "Archiv"),
        payrollRoleAllowed = true,
    ),
    REPORTS(
        title = "Berichte",
        group = "Meine Arbeit",
        subtitle = "Monatsbericht, Arbeitszeiten und Exportdaten.",
        primaryItems = listOf("Monatsreport", "Arbeitszeit", "Saldo", "Exportdaten"),
    ),
    PROFILE(
        title = "Profil",
        group = "Meine Arbeit",
        subtitle = "Persönliche Daten, Konto und Sicherheit.",
        primaryItems = listOf("Stammdaten", "Rollen", "Passwort", "Benachrichtigungen"),
    ),
    SUPPLY_CHAIN(
        title = "Supply Chain",
        group = "Workspace",
        subtitle = "Lager, Artikel, Bestellungen, Produktion und Services.",
        primaryItems = listOf("Artikel", "Lagerbestand", "Bestellungen", "Produktion"),
    ),
    INFO(
        title = "Hilfe",
        group = "Workspace",
        subtitle = "Demo, Neuigkeiten und rechtliche Seiten.",
        primaryItems = listOf("Demo-Tour", "Was ist neu", "Datenschutz", "Impressum"),
    ),
    ADMIN_HOME(
        title = "Admin",
        group = "Admin",
        subtitle = "Zentrale Übersicht für Team, Zeit, Lohn und Prozesse.",
        primaryItems = listOf("Teamstatus", "Offene Punkte", "Monatsabschluss", "Kennzahlen"),
        adminOnly = true,
    ),
    TIME_OVERVIEW(
        title = "Zeitübersicht",
        group = "Admin",
        subtitle = "Team-Wochenübersicht, Mitarbeitende und Zeiteinträge.",
        primaryItems = listOf("Mitarbeiter", "Woche", "Stempel", "Korrekturen"),
        adminOnly = true,
    ),
    TEAM_CALENDAR(
        title = "Urlaubskalender",
        group = "Admin",
        subtitle = "Urlaub und Krankheit im Team als Monatskalender.",
        primaryItems = listOf("Monat", "Urlaub", "Krankheit", "Team"),
        adminOnly = true,
    ),
    SUPERADMIN_HOME(
        title = "Superadmin",
        group = "Superadmin",
        subtitle = "App-Feedback, Firmen und zentrale Superadmin-Übersicht.",
        primaryItems = listOf("App Feedback", "Firmen", "Analytics", "Systemstatus"),
        adminOnly = true,
        superadminOnly = true,
    ),
    EMPLOYEES(
        title = "Mitarbeiter",
        group = "Admin",
        subtitle = "Mitarbeiterübersicht, Profile und Zeitkonten.",
        primaryItems = listOf("Alle Mitarbeiter", "Zeitkonten", "Detailansicht", "Rollen"),
        adminOnly = true,
    ),
    USERS(
        title = "Benutzer",
        group = "Admin",
        subtitle = "Benutzerverwaltung, Berechtigungen und Passwortwechsel.",
        primaryItems = listOf("Benutzer", "Rollen", "Seitenrechte", "Passwort ändern"),
        adminOnly = true,
    ),
    ADMIN_PASSWORD(
        title = "Admin-Passwort",
        group = "Admin",
        subtitle = "Eigenes Admin-Passwort ändern.",
        primaryItems = listOf("Aktuelles Passwort", "Neues Passwort", "Sicherheit"),
        adminOnly = true,
    ),
    CUSTOMERS_PROJECTS(
        title = "Projekt-Hub",
        group = "Admin",
        subtitle = "Kunden, Projekte, Aufgaben und Projektberichte als Übersicht.",
        primaryItems = listOf("Kunden", "Projekte", "Aufgaben", "Projektbericht"),
        adminOnly = true,
    ),
    CUSTOMERS(
        title = "Kunden",
        group = "Projekte",
        subtitle = "Kundenverwaltung und Zuordnungen.",
        primaryItems = listOf("Kundenliste", "Anlegen", "Löschen", "Zuordnung"),
        adminOnly = true,
    ),
    PROJECTS(
        title = "Projekte",
        group = "Projekte",
        subtitle = "Projekte, Hierarchie, Budgets und Stundensätze.",
        primaryItems = listOf("Projektliste", "Hierarchie", "Budget", "Stundensatz"),
        adminOnly = true,
    ),
    TASKS(
        title = "Aufgaben",
        group = "Projekte",
        subtitle = "Aufgaben und operative Arbeitspakete.",
        primaryItems = listOf("Taskliste", "Projektzuordnung", "Budget", "Verrechenbar"),
        adminOnly = true,
    ),
    PROJECT_REPORT(
        title = "Projektzeiten",
        group = "Projekte",
        subtitle = "Projektberichte, Zeiten und Auswertungen.",
        primaryItems = listOf("Projektanalyse", "Monatsfilter", "Audit", "Exportbasis"),
        adminOnly = true,
    ),
    ANALYTICS(
        title = "Analytics",
        group = "Admin",
        subtitle = "Auswertungen, Reports, Druckansichten und Zeitimporte.",
        primaryItems = listOf("Analytics", "Print Report", "Zeitimport", "Export"),
        adminOnly = true,
    ),
    TIME_IMPORT(
        title = "Zeitimport",
        group = "Admin",
        subtitle = "Zeitdaten importieren und Salden neu berechnen.",
        primaryItems = listOf("JSON-Import", "Salden", "Importstatus", "Audit"),
        adminOnly = true,
    ),
    SCHEDULE(
        title = "Dienstplan",
        group = "Admin",
        subtitle = "Schichten, Dienstplan, Druckplan und Regeln.",
        primaryItems = listOf("Dienstplan", "Schichtregeln", "Druckplan", "Schichten"),
        adminOnly = true,
    ),
    PRINT_SCHEDULE(
        title = "Druckplan",
        group = "Dienstplan",
        subtitle = "Dienstpläne für Woche und Team vorbereiten.",
        primaryItems = listOf("Wochenplan", "Mitarbeiter", "Schichten", "Druckdaten"),
        adminOnly = true,
    ),
    SHIFT_RULES(
        title = "Planregeln",
        group = "Dienstplan",
        subtitle = "Schichtdefinitionen und Planregeln pflegen.",
        primaryItems = listOf("Schichtdefinitionen", "Regeln", "Aktiv/Inaktiv", "Sollzeiten"),
        adminOnly = true,
    ),
    PAYROLL_ADMIN(
        title = "Abrechnungen",
        group = "Admin",
        subtitle = "Lohnläufe, Mitarbeiter-Abrechnungen und Freigaben.",
        primaryItems = listOf("Lohnläufe", "Abrechnungen", "Freigaben", "Archiv"),
        adminOnly = true,
        payrollRoleAllowed = true,
    ),
    ACCOUNTING(
        title = "Buchhaltung",
        group = "Admin",
        subtitle = "Accounting, Rechnungen und Finanzübersicht.",
        primaryItems = listOf("Belege", "Rechnungen", "Konten", "Auswertungen"),
        adminOnly = true,
    ),
    CHRONO_TWO(
        title = "Lager 2.0",
        group = "Module",
        subtitle = "Warehouse, IoT, Procurement und KI-Funktionen.",
        primaryItems = listOf("Produkte", "Bestand", "IoT", "Analytics"),
        adminOnly = true,
    ),
    CRM(
        title = "CRM",
        group = "Admin",
        subtitle = "Kontakte, Deals, Aktivitäten und Pipeline.",
        primaryItems = listOf("Kontakte", "Deals", "Aktivitäten", "Pipeline"),
        adminOnly = true,
    ),
    BANKING(
        title = "Banking",
        group = "Admin",
        subtitle = "Bankkonten, Transaktionen und Zahlungsabgleich.",
        primaryItems = listOf("Konten", "Transaktionen", "Abgleich", "Zahlungen"),
        adminOnly = true,
    ),
    KNOWLEDGE(
        title = "Firmenwissen",
        group = "Admin",
        subtitle = "Interne Wissensbasis und KI-Inhalte.",
        primaryItems = listOf("Wissen", "Anlegen", "Zugriff", "Löschen"),
        adminOnly = true,
    ),
    COMPANY_SETTINGS(
        title = "Firma",
        group = "Admin",
        subtitle = "Globale Firmen- und Systemkonfiguration.",
        primaryItems = listOf("Firma", "Settings", "Feiertage", "Logo"),
        adminOnly = true,
    ),
    COMPANY(
        title = "Firmen",
        group = "Superadmin",
        subtitle = "Mandanten, Firmenstatus, Zahlungsstatus und Superadmin-Übersicht.",
        primaryItems = listOf("Firmen", "Aktiv/Inaktiv", "Zahlungen", "Mandanten"),
        adminOnly = true,
        superadminOnly = true,
    );

    companion object {
        fun visibleFor(user: UserProfile): List<AppSection> {
            val roles = user.roles.toSet()
            val isAdmin = roles.any { it == "ROLE_ADMIN" || it == "ROLE_SUPERADMIN" }
            val isSuperadmin = roles.any { it == "ROLE_SUPERADMIN" }
            val isPayroll = roles.any { it == "ROLE_PAYROLL_ADMIN" }

            return entries.filter { section ->
                when {
                    section.superadminOnly && !isSuperadmin -> false
                    section.adminOnly && !isAdmin && !(section.payrollRoleAllowed && isPayroll) -> false
                    !section.hasFeatureAccess(user) -> false
                    !section.hasPageAccess(user) -> false
                    else -> true
                }
            }
        }

        private fun AppSection.hasFeatureAccess(user: UserProfile): Boolean {
            val features = user.companyFeatureKeys
            if (features.isEmpty()) return true
            return when (this) {
                SUPPLY_CHAIN -> features.hasAny("supplyChain")
                CUSTOMERS_PROJECTS, CUSTOMERS, PROJECTS, TASKS, PROJECT_REPORT -> features.hasAny("projects")
                ANALYTICS -> false
                SCHEDULE, PRINT_SCHEDULE, SHIFT_RULES -> features.hasAny("roster")
                PAYROLL_ADMIN -> features.hasAny("payroll")
                ACCOUNTING -> features.hasAny("accounting")
                CHRONO_TWO -> features.hasAny("chrono2", "chronoTwo")
                CRM -> features.hasAny("crm")
                BANKING -> features.hasAny("banking")
                else -> true
            }
        }

        private fun AppSection.hasPageAccess(user: UserProfile): Boolean {
            if (user.pagePermissions.isEmpty()) {
                return true
            }
            return when (this) {
                TIME -> user.hasAnyPageAccess("dashboard")
                WORK_MODEL -> user.hasAnyPageAccess("dashboard", "personalData")
                ABSENCES -> user.hasAnyPageAccess("dashboard", "personalData", "adminDashboard")
                PAYSLIPS -> user.hasAnyPageAccess("payslips", "adminPayslips")
                REPORTS -> user.hasAnyPageAccess("dashboard", "printReport", "adminProjectReport")
                PROFILE -> user.hasAnyPageAccess("personalData", "dashboard")
                SUPPLY_CHAIN -> user.hasAnyPageAccess("supplyChain")
                INFO -> user.hasAnyPageAccess("demoTour")
                ADMIN_HOME -> user.hasAnyPageAccess("adminDashboard")
                TIME_OVERVIEW -> user.hasAnyPageAccess("adminDashboard")
                TEAM_CALENDAR -> user.hasAnyPageAccess("adminDashboard")
                SUPERADMIN_HOME -> user.roles.contains("ROLE_SUPERADMIN")
                EMPLOYEES -> user.hasAnyPageAccess("adminUsers", "adminDashboard")
                USERS -> user.hasAnyPageAccess("adminUsers")
                ADMIN_PASSWORD -> user.hasAnyPageAccess("adminChangePassword")
                CUSTOMERS_PROJECTS -> user.hasAnyPageAccess("adminCustomers", "adminProjects", "adminTasks", "adminProjectReport")
                CUSTOMERS -> user.hasAnyPageAccess("adminCustomers")
                PROJECTS -> user.hasAnyPageAccess("adminProjects")
                TASKS -> user.hasAnyPageAccess("adminTasks")
                PROJECT_REPORT -> user.hasAnyPageAccess("adminProjectReport")
                ANALYTICS -> user.hasAnyPageAccess("adminAnalytics", "adminDashboard")
                TIME_IMPORT -> user.hasAnyPageAccess("adminImportTimes")
                SCHEDULE -> user.hasAnyPageAccess("adminSchedule", "adminPrintSchedule", "adminShiftRules")
                PRINT_SCHEDULE -> user.hasAnyPageAccess("adminPrintSchedule", "adminSchedule")
                SHIFT_RULES -> user.hasAnyPageAccess("adminShiftRules")
                PAYROLL_ADMIN -> user.hasAnyPageAccess("adminPayslips")
                ACCOUNTING -> user.hasAnyPageAccess("adminAccounting")
                CHRONO_TWO -> user.hasAnyPageAccess("chronoTwo")
                CRM -> user.hasAnyPageAccess("crm")
                BANKING -> user.hasAnyPageAccess("banking")
                KNOWLEDGE -> user.hasAnyPageAccess("adminKnowledge")
                COMPANY_SETTINGS -> user.hasAnyPageAccess("companySettings")
                COMPANY -> user.roles.contains("ROLE_SUPERADMIN") || user.hasAnyPageAccess("companyManagement")
            }
        }

        private fun UserProfile.hasAnyPageAccess(vararg keys: String): Boolean =
            keys.any { key -> pagePermissions[key]?.uppercase() in setOf("VIEW", "MANAGE") }

        private fun Set<String>.hasAny(vararg keys: String): Boolean =
            keys.any { key -> contains(key) }
    }
}
