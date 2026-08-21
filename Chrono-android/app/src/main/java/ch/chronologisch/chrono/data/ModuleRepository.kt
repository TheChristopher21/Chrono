package ch.chronologisch.chrono.data

import ch.chronologisch.chrono.ui.dashboard.AppSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime

class ModuleRepository(
    private val baseUrl: String,
) {
    suspend fun loadModuleSummary(section: AppSection, session: AuthenticatedSession): ModuleSummary =
        withContext(Dispatchers.IO) {
            val endpoints = endpointsFor(section, session.user)
            ModuleSummary(
                section = section,
                endpoints = endpoints.map { loadEndpoint(it, session.token) },
                actions = actionsFor(section),
                loadedAt = LocalDateTime.now(),
            )
        }

    suspend fun submitModuleAction(
        action: ModuleAction,
        values: Map<String, String>,
        session: AuthenticatedSession,
    ): ModuleActionSubmitResult = withContext(Dispatchers.IO) {
        action.fields.firstOrNull { it.required && values[it.key].isNullOrBlank() }?.let {
            return@withContext ModuleActionSubmitResult(success = false, message = "${it.label} fehlt.")
        }

        val response = request(
            path = renderTemplate(action.pathTemplate, values, session.user, encodeForUrl = true),
            method = action.method.name,
            token = session.token,
            body = action.bodyTemplate?.let { renderTemplate(it, values, session.user, encodeForUrl = false) },
        )

        if (response.code !in 200..299) {
            return@withContext ModuleActionSubmitResult(
                success = false,
                message = extractErrorMessage(response.body).ifBlank { "${action.title} fehlgeschlagen (${response.code})." },
            )
        }

        ModuleActionSubmitResult(
            success = true,
            message = extractErrorMessage(response.body).ifBlank { "${action.title} gespeichert." },
        )
    }

    suspend fun submitMobileFeedback(
        section: AppSection,
        message: String,
        session: AuthenticatedSession,
        appVersionName: String,
        appVersionCode: Int,
        deviceInfo: String,
    ): ModuleActionSubmitResult = withContext(Dispatchers.IO) {
        val trimmedMessage = message.trim()
        if (trimmedMessage.isBlank()) {
            return@withContext ModuleActionSubmitResult(success = false, message = "Feedback fehlt.")
        }

        val body = JSONObject()
            .put("message", trimmedMessage)
            .put("appMenuKey", section.name)
            .put("appMenuTitle", section.title)
            .put("appMenuGroup", section.group)
            .put("appVersionName", appVersionName)
            .put("appVersionCode", appVersionCode)
            .put("deviceInfo", deviceInfo)
            .toString()

        val response = request(
            path = "/api/mobile-feedback",
            method = "POST",
            token = session.token,
            body = body,
        )

        if (response.code !in 200..299) {
            return@withContext ModuleActionSubmitResult(
                success = false,
                message = extractErrorMessage(response.body).ifBlank { "Feedback konnte nicht gesendet werden (${response.code})." },
            )
        }

        ModuleActionSubmitResult(success = true, message = "Danke, Feedback gesendet.")
    }

    private fun loadEndpoint(endpoint: ModuleEndpoint, token: String): ModuleEndpointResult =
        try {
            val response = request(endpoint.path, "GET", token)
            when {
                response.code == HttpURLConnection.HTTP_FORBIDDEN || response.code == HttpURLConnection.HTTP_UNAUTHORIZED ->
                    ModuleEndpointResult(
                        label = endpoint.label,
                        path = endpoint.path,
                        status = ModuleRequestStatus.FORBIDDEN,
                        message = extractErrorMessage(response.body).ifBlank { "Keine Berechtigung." },
                    )
                response.code !in 200..299 ->
                    ModuleEndpointResult(
                        label = endpoint.label,
                        path = endpoint.path,
                        status = ModuleRequestStatus.ERROR,
                        message = extractErrorMessage(response.body).ifBlank { "HTTP ${response.code}" },
                    )
                else -> {
                    val snapshot = summarizeJson(response.body)
                    ModuleEndpointResult(
                        label = endpoint.label,
                        path = endpoint.path,
                        status = if (snapshot.count == 0) ModuleRequestStatus.EMPTY else ModuleRequestStatus.READY,
                        count = snapshot.count,
                        preview = snapshot.preview,
                        items = snapshot.items,
                        message = snapshot.message,
                    )
                }
            }
        } catch (_: SocketTimeoutException) {
            ModuleEndpointResult(endpoint.label, endpoint.path, ModuleRequestStatus.ERROR, message = "Backend antwortet nicht.")
        } catch (ex: Exception) {
            ModuleEndpointResult(
                endpoint.label,
                endpoint.path,
                ModuleRequestStatus.ERROR,
                message = ex.message?.takeIf { it.isNotBlank() } ?: "Konnte nicht geladen werden.",
            )
        }

    private fun request(path: String, method: String, token: String, body: String? = null): ApiResponse {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }

        return try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            ApiResponse(
                code = connection.responseCode,
                body = readBody(if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun endpointsFor(section: AppSection, user: UserProfile): List<ModuleEndpoint> {
        val today = LocalDate.now()
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val weekEnd = weekStart.plusDays(6)
        val monthStart = today.withDayOfMonth(1)
        val username = user.username.urlEncoded()

        return when (section) {
            AppSection.TIME -> e(
                "Heute" to "/api/timetracking/daily-summary?username=$username&date=$today",
                "Monatsreport" to "/api/timetracking/report?username=$username&startDate=$monthStart&endDate=$today",
                "Differenz" to "/api/timetracking/work-difference?username=$username&date=$today",
                "Korrekturen" to "/api/correction/my?username=$username",
                "Kunden" to "/api/customers",
                "Projekte" to "/api/projects",
            )
            AppSection.WORK_MODEL -> e(
                "Woche" to "/api/dashboard/user/$username/week?startDate=$weekStart&endDate=$weekEnd",
                "Differenz heute" to "/api/timetracking/work-difference?username=$username&date=$today",
                "Profil" to "/api/users/profile/$username",
            )
            AppSection.ABSENCES -> e(
                "Urlaub" to "/api/vacation/my",
                "Krankheit" to "/api/sick-leave/my",
                "Korrekturen" to "/api/correction/my?username=$username",
                "Resturlaub" to "/api/vacation/remaining?username=$username&year=${today.year}",
            )
            AppSection.PAYSLIPS -> e("Meine Lohnabrechnungen" to "/api/payslips/my")
            AppSection.REPORTS -> e(
                "Monatsreport" to "/api/timetracking/report?username=$username&startDate=$monthStart&endDate=$today",
                "Woche" to "/api/dashboard/user/$username/week?startDate=$weekStart&endDate=$weekEnd",
                "Differenz" to "/api/timetracking/work-difference?username=$username&date=$today",
            )
            AppSection.PROFILE -> e("Session" to "/api/auth/me", "Profil" to "/api/users/profile/$username")
            AppSection.SUPPLY_CHAIN -> e(
                "Artikel" to "/api/supply-chain/products?page=0&size=100",
                "Lager" to "/api/supply-chain/warehouses",
                "Bestand" to "/api/supply-chain/stock?page=0&size=100",
                "Bewegungen" to "/api/supply-chain/stock-movements?page=0&size=100",
                "Einkauf" to "/api/supply-chain/purchase-orders?page=0&size=100",
                "Verkauf" to "/api/supply-chain/sales-orders?page=0&size=100",
                "Produktion" to "/api/supply-chain/production-orders?page=0&size=100",
                "Services" to "/api/supply-chain/service-requests?page=0&size=100",
                "Inventur" to "/api/supply-chain/cycle-counts?page=0&size=100",
                "Audit" to "/api/audit?limit=25",
            )
            AppSection.INFO -> e(
                "Letzte Änderung" to "/api/changelog/latest",
                "Änderungen" to "/api/changelog",
                "Chat Status" to "/api/chat/status",
            )
            AppSection.ADMIN_HOME -> e(
                "Zeitübersicht" to "/api/admin/timetracking/all-summaries",
                "Mitarbeiter" to "/api/admin/users",
                "Kunden" to "/api/customers",
                "Projekte" to "/api/projects",
                "Zeitkonten" to "/api/admin/timetracking/admin/tracking-balances",
                "Wochensaldo" to "/api/admin/timetracking/admin/weekly-balance?monday=$weekStart",
                "Korrekturen" to "/api/correction/all",
                "Urlaub" to "/api/vacation/all",
                "Krankheit" to "/api/sick-leave/company",
                "Abrechnungen offen" to "/api/payslips/admin/pending",
            )
            AppSection.TIME_OVERVIEW -> e(
                "Zeitübersicht" to "/api/admin/timetracking/all-summaries",
                "Mitarbeiter" to "/api/admin/users",
                "Zeitkonten" to "/api/admin/timetracking/admin/tracking-balances",
                "Wochensaldo" to "/api/admin/timetracking/admin/weekly-balance?monday=$weekStart",
            )
            AppSection.TEAM_CALENDAR -> e(
                "Urlaub" to "/api/vacation/all",
                "Krankheit" to "/api/sick-leave/company",
                "Mitarbeiter" to "/api/admin/users",
            )
            AppSection.SUPERADMIN_HOME -> e(
                "App Feedback" to "/api/mobile-feedback?limit=100",
                "Firmen" to "/api/superadmin/companies",
                "Analytics" to "/api/superadmin/analytics/summary?days=30",
                "Ausgeschlossene IPs" to "/api/superadmin/analytics/excluded-ips",
                "Änderungen" to "/api/changelog",
            )
            AppSection.EMPLOYEES -> e(
                "Mitarbeiter" to "/api/admin/users",
                "Zeitkonten" to "/api/admin/timetracking/admin/tracking-balances",
                "Wochensaldo" to "/api/admin/timetracking/admin/weekly-balance?monday=$weekStart",
                "Urlaub" to "/api/vacation/all",
                "Krankheit" to "/api/sick-leave/company",
            )
            AppSection.USERS -> e(
                "Benutzer" to "/api/admin/users",
                "Mitarbeiter" to "/api/admin/users",
                "Zeitkonten" to "/api/admin/timetracking/admin/tracking-balances",
                "Wochensaldo" to "/api/admin/timetracking/admin/weekly-balance?monday=$weekStart",
                "Urlaub" to "/api/vacation/all",
                "Krankheit" to "/api/sick-leave/company",
                "Audit" to "/api/audit?limit=25",
            )
            AppSection.ADMIN_PASSWORD -> e("Session" to "/api/auth/me", "Profil" to "/api/users/profile/$username")
            AppSection.CUSTOMERS_PROJECTS -> e(
                "Kunden" to "/api/customers",
                "Projekte" to "/api/projects",
                "Projektbaum" to "/api/projects/hierarchy",
                "Projektanalyse" to "/api/report/analytics/projects?startDate=$monthStart&endDate=$today",
            )
            AppSection.CUSTOMERS -> e(
                "Kunden" to "/api/customers",
                "Zuletzt" to "/api/customers/recent",
                "Audit" to "/api/audit?limit=25",
            )
            AppSection.PROJECTS -> e(
                "Projekte" to "/api/projects",
                "Projektbaum" to "/api/projects/hierarchy",
                "Projektanalyse" to "/api/report/analytics/projects?startDate=$monthStart&endDate=$today",
                "Integrationen" to "/api/integrations",
            )
            AppSection.TASKS -> e(
                "Projekte" to "/api/projects",
                "Projektbaum" to "/api/projects/hierarchy",
            )
            AppSection.PROJECT_REPORT -> e(
                "Projekte" to "/api/projects",
                "Projektanalyse" to "/api/report/analytics/projects?startDate=$monthStart&endDate=$today",
                "Audit" to "/api/audit?limit=25",
            )
            AppSection.ANALYTICS -> e(
                "Zeitübersicht" to "/api/admin/timetracking/all-summaries",
                "Projektanalyse" to "/api/report/analytics/projects?startDate=$monthStart&endDate=$today",
                "Audit" to "/api/audit?limit=25",
            )
            AppSection.TIME_IMPORT -> e(
                "Zeitübersicht" to "/api/admin/timetracking/all-summaries",
                "Zeitkonten" to "/api/admin/timetracking/admin/tracking-balances",
                "Audit" to "/api/audit?limit=25",
            )
            AppSection.SCHEDULE -> e(
                "Dienstplan" to "/api/admin/schedule?start=$weekStart&end=$weekEnd",
                "Mitarbeiter" to "/api/admin/users",
                "Schichten" to "/api/admin/shift-definitions",
                "Planregeln" to "/api/admin/schedule-rules/all",
                "Sollzeit heute" to "/api/admin/schedule-rules/expected-work-minutes?username=$username&date=$today",
                "Feiertagsoptionen" to "/api/admin/user-holiday-options/week?username=$username&mondayInWeek=$weekStart",
            )
            AppSection.PRINT_SCHEDULE -> e(
                "Dienstplan" to "/api/admin/schedule?start=$weekStart&end=$weekEnd",
                "Mitarbeiter" to "/api/admin/users",
                "Schichten" to "/api/admin/shift-definitions",
                "Planregeln" to "/api/admin/schedule-rules/all",
            )
            AppSection.SHIFT_RULES -> e(
                "Schichten" to "/api/admin/shift-definitions",
                "Alle Schichten" to "/api/admin/shift-definitions/all",
                "Planregeln" to "/api/admin/schedule-rules/all",
                "Sollzeit heute" to "/api/admin/schedule-rules/expected-work-minutes?username=$username&date=$today",
                "Mitarbeiter" to "/api/admin/users",
            )
            AppSection.PAYROLL_ADMIN -> e(
                "Mitarbeiter" to "/api/admin/users",
                "Offen" to "/api/payslips/admin/pending",
                "Freigegeben" to "/api/payslips/admin/approved",
                "Alle" to "/api/payslips/admin/all",
            )
            AppSection.ACCOUNTING -> e(
                "Debitoren" to "/api/accounting/receivables/open?page=0&size=5",
                "Kreditoren" to "/api/accounting/payables/open?page=0&size=5",
                "Konten" to "/api/accounting/accounts",
                "Journal" to "/api/accounting/journal?page=0&size=5",
                "Anlagen" to "/api/accounting/assets",
            )
            AppSection.CHRONO_TWO -> e(
                "Produkte" to "/api/chrono2/products",
                "Lagerorte" to "/api/chrono2/locations",
                "Bestand" to "/api/chrono2/inventory",
                "Analytics" to "/api/chrono2/analytics/kpis",
                "Retouren" to "/api/chrono2/outbound/returns",
                "Blockchain" to "/api/chrono2/blockchain/movement",
            )
            AppSection.CRM -> e(
                "Kunden" to "/api/customers",
                "Leads" to "/api/crm/leads",
                "Opportunities" to "/api/crm/opportunities",
                "Kampagnen" to "/api/crm/campaigns",
            )
            AppSection.BANKING -> e(
                "Bankkonten" to "/api/banking/accounts",
                "Zahlungsläufe" to "/api/banking/batches",
                "Offene Läufe" to "/api/banking/batches/open?page=0&size=5",
                "Kreditoren" to "/api/accounting/payables/open?page=0&size=25",
                "Debitoren" to "/api/accounting/receivables/open?page=0&size=25",
                "Signaturen" to "/api/banking/signatures",
                "Nachrichten" to "/api/banking/messages",
            )
            AppSection.KNOWLEDGE -> e("Knowledge" to "/api/admin/knowledge")
            AppSection.COMPANY_SETTINGS -> e(
                "Firma" to "/api/admin/company",
                "Einstellungen" to "/api/admin/company/settings",
                "Feiertage" to "/api/admin/company/holiday-catalog",
            )
            AppSection.COMPANY -> e(
                "Firmen" to "/api/superadmin/companies",
                "Analytics" to "/api/superadmin/analytics/summary?days=30",
                "Ausgeschlossene IPs" to "/api/superadmin/analytics/excluded-ips",
                "Änderungen" to "/api/changelog",
            )
        }
    }

    private fun actionsFor(section: AppSection): List<ModuleAction> {
        val today = LocalDate.now()
        val now = LocalDateTime.now().withSecond(0).withNano(0).toString()
        return when (section) {
            AppSection.TIME -> listOf(correction(today, now), userDayCustomer(today), userDayProject(today))
            AppSection.WORK_MODEL, AppSection.PAYSLIPS, AppSection.REPORTS, AppSection.INFO, AppSection.SUPERADMIN_HOME -> emptyList()
            AppSection.ABSENCES -> listOf(vacation(today), sickLeave(today), correction(today, now))
            AppSection.PROFILE -> listOf(profileUpdate(), changePassword())
            AppSection.ADMIN_PASSWORD -> listOf(changePassword())
            AppSection.SUPPLY_CHAIN -> listOf(
                product(),
                warehouse(),
                stockAdjust(),
                cycleCount(),
                cycleCountSubmit(),
                cycleCountApprove(),
                purchaseOrder(today),
                purchaseReceive(),
                salesOrder(today),
                salesFulfill(),
                receivingPreview(),
                receivingApply(),
                replenishmentPreview(),
                autoReplenish(),
                wavePick(),
                serviceRequest(today),
                serviceStatus(today),
                productionOrder(today),
                productionStatus(today),
            )
            AppSection.ADMIN_HOME -> listOf(
                approveCorrection(),
                denyCorrection(),
                approveVacation(),
                denyVacation(),
                adminEditDay(today),
                timeEntryApprove(),
                timeEntryRevoke(),
                timeEntryCustomer(),
                timeEntryProject(),
                dayCustomer(today),
                dayProject(today),
                adminVacation(today),
                adminSickLeave(today),
                vacationDelete(),
                sickLeaveDelete(),
                payslipApprove(),
                payslipSetPayout(today),
            )
            AppSection.TIME_OVERVIEW -> listOf(
                adminEditDay(today),
                timeEntryApprove(),
                timeEntryRevoke(),
                timeEntryCustomer(),
                timeEntryProject(),
                dayCustomer(today),
                dayProject(today),
            )
            AppSection.TEAM_CALENDAR -> listOf(adminVacation(today), adminSickLeave(today))
            AppSection.EMPLOYEES, AppSection.USERS -> listOf(userCreate(), userVisibilityUpdate(), userDelete())
            AppSection.CUSTOMERS_PROJECTS -> listOf(customerCreate(), customerUpdate(), projectCreate(), projectUpdate(), taskCreate(), taskUpdate(), customerDelete(), projectDelete(), taskDelete())
            AppSection.CUSTOMERS -> listOf(customerCreate(), customerUpdate(), customerDelete())
            AppSection.PROJECTS -> listOf(projectCreate(), projectUpdate(), projectDelete(), taskCreate(), taskUpdate())
            AppSection.TASKS -> listOf(taskCreate(), taskUpdate(), taskDelete())
            AppSection.PROJECT_REPORT -> emptyList()
            AppSection.ANALYTICS -> emptyList()
            AppSection.TIME_IMPORT -> listOf(timeImport(), rebuildBalances())
            AppSection.SCHEDULE -> listOf(scheduleEntry(today), scheduleEntryUpdate(today), scheduleAutofill(today), scheduleCopy(today), scheduleDelete())
            AppSection.PRINT_SCHEDULE -> emptyList()
            AppSection.SHIFT_RULES -> listOf(shiftDefinition(), shiftDefinitionUpdate(), scheduleRuleCreate(today), scheduleRuleUpdate(today), scheduleRuleDelete())
            AppSection.PAYROLL_ADMIN -> listOf(payslipGenerate(today), payslipApprove(), payslipApproveAll(), payslipSetPayout(today), payslipScheduleUser(today), payslipScheduleAll(today), payslipReopen(), payslipDelete())
            AppSection.ACCOUNTING -> listOf(accountCreate(), journalEntry(today), assetCreate(today), assetDepreciate(), receivablePayment(today), payablePayment(today))
            AppSection.CHRONO_TWO -> listOf(chronoTwoProduct(), chronoTwoSlotting(), chronoTwoIot(), chronoTwoMovement(), chronoTwoSourcing(), chronoTwoPickRoute(), chronoTwoReturn(), chronoTwoNlp())
            AppSection.CRM -> listOf(leadCreate(), leadStatus(), opportunityCreate(today), opportunityUpdate(today), campaignCreate(today), campaignUpdate(today), crmContactCreate(), crmActivityCreate(now), crmAddressCreate(), crmDocumentCreate(), crmDocumentDelete())
            AppSection.BANKING -> listOf(bankAccountCreate(), bankAccountUpdate(), bankAccountDelete(), paymentBatchCreate(), paymentBatchApprove(), paymentBatchTransmit(), bankMessageCreate(), signatureCreate(), signatureRefresh(), signatureComplete())
            AppSection.KNOWLEDGE -> listOf(knowledgeCreate(), knowledgeDelete())
            AppSection.COMPANY_SETTINGS -> listOf(companySettingsUpdate())
            AppSection.COMPANY -> listOf(companyCreate(), companyUpdate(), companyCreateWithAdmin(), companyAdminCreate(), companyPayment(), companyDelete(), excludedIpCreate(), excludedIpDelete(), changelogCreate())
        }
    }

    private fun correction(today: LocalDate, now: String) = action(
        "correction-create", "Korrektur beantragen", ModuleActionMethod.POST,
        "/api/correction/create?username={username}&requestDate={requestDate}&desiredTimestamp={desiredTimestamp}&desiredPunchType={desiredPunchType}&reason={reason}",
        fields = listOf(f("requestDate", "Datum", ModuleActionFieldType.DATE, "$today"), f("desiredTimestamp", "Zeitpunkt", ModuleActionFieldType.DATETIME, now), f("desiredPunchType", "Typ START/ENDE", default = "START"), f("reason", "Grund", ModuleActionFieldType.MULTILINE)),
        description = "Legt einen Zeiterfassungs-Korrekturantrag an.",
        refreshHistory = true,
    )

    private fun vacation(today: LocalDate) = action(
        "vacation-create", "Urlaub beantragen", ModuleActionMethod.POST,
        "/api/vacation/create?username={username}&startDate={startDate}&endDate={endDate}&halfDay={halfDay}&usesOvertime={usesOvertime}",
        fields = listOf(f("startDate", "Startdatum", ModuleActionFieldType.DATE, "$today"), f("endDate", "Enddatum", ModuleActionFieldType.DATE, "$today"), f("halfDay", "Halber Tag", ModuleActionFieldType.BOOLEAN, "false"), f("usesOvertime", "Überzeit verwenden", ModuleActionFieldType.BOOLEAN, "false")),
    )

    private fun sickLeave(today: LocalDate) = action(
        "sick-report", "Krankheit melden", ModuleActionMethod.POST,
        "/api/sick-leave/report?targetUsername={username}&startDate={startDate}&endDate={endDate}&halfDay={halfDay}&comment={comment}",
        fields = listOf(f("startDate", "Startdatum", ModuleActionFieldType.DATE, "$today"), f("endDate", "Enddatum", ModuleActionFieldType.DATE, "$today"), f("halfDay", "Halber Tag", ModuleActionFieldType.BOOLEAN, "false"), f("comment", "Kommentar", ModuleActionFieldType.MULTILINE, required = false)),
    )

    private fun changePassword() = action(
        "change-password", "Passwort ändern", ModuleActionMethod.PUT, "/api/user/change-password",
        body = """{"username":"{username}","currentPassword":"{currentPassword}","newPassword":"{newPassword}"}""",
        fields = listOf(f("currentPassword", "Aktuelles Passwort", ModuleActionFieldType.PASSWORD), f("newPassword", "Neues Passwort", ModuleActionFieldType.PASSWORD)),
    )
    private fun profileUpdate() = action(
        "profile-update", "Persönliche Daten speichern", ModuleActionMethod.PUT, "/api/user/update",
        body = """{"username":"{username}","firstName":"{firstName}","lastName":"{lastName}","email":"{email}","address":"{address}","mobilePhone":"{mobilePhone}","landlinePhone":"{landlinePhone}","civilStatus":"{civilStatus}","children":{children},"bankAccount":"{bankAccount}","emailNotifications":{emailNotifications}}""",
        fields = listOf(
            f("firstName", "Vorname"),
            f("lastName", "Nachname"),
            f("email", "E-Mail", required = false),
            f("address", "Adresse", required = false),
            f("mobilePhone", "Telefon", required = false),
            f("landlinePhone", "Festnetz", required = false),
            f("civilStatus", "Zivilstand", required = false),
            f("children", "Kinder", ModuleActionFieldType.NUMBER, "0"),
            f("bankAccount", "Bankkonto", required = false),
            f("emailNotifications", "E-Mail Benachrichtigungen", ModuleActionFieldType.BOOLEAN, "true"),
        ),
    )

    private fun product() = action(
        "product-create", "Artikel anlegen", ModuleActionMethod.POST, "/api/supply-chain/products",
        body = """{"sku":"{sku}","name":"{name}","description":"{description}","unitOfMeasure":"{unitOfMeasure}","unitCost":{unitCost},"unitPrice":{unitPrice},"active":{active}}""",
        fields = listOf(f("sku", "SKU"), f("name", "Name"), f("description", "Beschreibung", ModuleActionFieldType.MULTILINE, required = false), f("unitOfMeasure", "Einheit", default = "Stk"), f("unitCost", "Einstandspreis", ModuleActionFieldType.NUMBER, "0"), f("unitPrice", "Verkaufspreis", ModuleActionFieldType.NUMBER, "0"), f("active", "Aktiv", ModuleActionFieldType.BOOLEAN, "true")),
    )

    private fun warehouse() = action(
        "warehouse-create", "Lager anlegen", ModuleActionMethod.POST, "/api/supply-chain/warehouses",
        body = """{"code":"{code}","name":"{name}","location":"{location}"}""",
        fields = listOf(f("code", "Code"), f("name", "Name"), f("location", "Ort", required = false)),
    )

    private fun stockAdjust() = action(
        "stock-adjust", "Bestand buchen", ModuleActionMethod.POST, "/api/supply-chain/stock/adjust",
        body = """{"productId":{productId},"warehouseId":{warehouseId},"quantityChange":{quantityChange},"type":"{type}","reference":"{reference}","lotNumber":"{lotNumber}","serialNumber":"{serialNumber}"}""",
        fields = listOf(f("productId", "Artikel"), f("warehouseId", "Lager"), f("quantityChange", "Menge +/-", ModuleActionFieldType.NUMBER, "1"), f("type", "Typ", default = "ADJUSTMENT"), f("reference", "Referenz", required = false), f("lotNumber", "Charge", required = false), f("serialNumber", "Seriennummer", required = false)),
    )

    private fun cycleCount() = action(
        "cycle-count-create", "Inventur starten", ModuleActionMethod.POST, "/api/supply-chain/cycle-counts",
        body = """{"productId":{productId},"warehouseId":{warehouseId}}""",
        fields = listOf(f("productId", "Artikel"), f("warehouseId", "Lager")),
    )

    private fun serviceRequest(today: LocalDate) = action(
        "service-request-create", "Servicefall anlegen", ModuleActionMethod.POST, "/api/supply-chain/service-requests",
        body = """{"customerName":"{customerName}","subject":"{subject}","description":"{description}","status":"{status}","openedDate":"{openedDate}"}""",
        fields = listOf(f("customerName", "Kunde"), f("subject", "Betreff"), f("description", "Beschreibung", ModuleActionFieldType.MULTILINE, required = false), f("status", "Status", default = "OPEN"), f("openedDate", "Geöffnet am", ModuleActionFieldType.DATE, "$today")),
    )

    private fun productionOrder(today: LocalDate) = action(
        "production-order-create", "Produktionsauftrag", ModuleActionMethod.POST, "/api/supply-chain/production-orders",
        body = """{"orderNumber":"{orderNumber}","productId":{productId},"quantity":{quantity},"status":"{status}","startDate":"{startDate}"}""",
        fields = listOf(f("orderNumber", "Auftragsnummer"), f("productId", "Produkt-ID", ModuleActionFieldType.NUMBER), f("quantity", "Menge", ModuleActionFieldType.NUMBER, "1"), f("status", "Status", default = "PLANNED"), f("startDate", "Startdatum", ModuleActionFieldType.DATE, "$today")),
    )

    private fun cycleCountSubmit() = action(
        "cycle-count-submit", "Inventur zählen", ModuleActionMethod.POST, "/api/supply-chain/cycle-counts/{id}/submit",
        body = """{"countedQuantity":{countedQuantity}}""",
        fields = listOf(f("id", "Inventur-ID", ModuleActionFieldType.NUMBER), f("countedQuantity", "Gezählte Menge", ModuleActionFieldType.NUMBER, "0")),
    )

    private fun cycleCountApprove() = action(
        "cycle-count-approve", "Inventur freigeben", ModuleActionMethod.POST, "/api/supply-chain/cycle-counts/{id}/approve",
        fields = listOf(f("id", "Inventur-ID", ModuleActionFieldType.NUMBER)),
    )

    private fun purchaseOrder(today: LocalDate) = action(
        "purchase-order-create", "Einkauf anlegen", ModuleActionMethod.POST, "/api/supply-chain/purchase-orders",
        body = """{"orderNumber":"{orderNumber}","vendorName":"{vendorName}","expectedDate":"{expectedDate}","lines":[{"productId":{productId},"quantity":{quantity},"unitCost":{unitCost}}]}""",
        fields = listOf(f("orderNumber", "Bestellnummer"), f("vendorName", "Lieferant"), f("expectedDate", "Erwartet", ModuleActionFieldType.DATE, "$today"), f("productId", "Artikel"), f("quantity", "Menge", ModuleActionFieldType.NUMBER, "1"), f("unitCost", "Preis", ModuleActionFieldType.NUMBER, "0")),
    )

    private fun purchaseReceive() = action(
        "purchase-order-receive", "Einkauf einbuchen", ModuleActionMethod.POST, "/api/supply-chain/purchase-orders/{id}/receive",
        body = """{"warehouseId":{warehouseId}}""",
        fields = listOf(f("id", "Einkauf-ID", ModuleActionFieldType.NUMBER), f("warehouseId", "Lager")),
    )

    private fun salesOrder(today: LocalDate) = action(
        "sales-order-create", "Verkauf anlegen", ModuleActionMethod.POST, "/api/supply-chain/sales-orders",
        body = """{"orderNumber":"{orderNumber}","customerName":"{customerName}","dueDate":"{dueDate}","lines":[{"productId":{productId},"quantity":{quantity},"unitPrice":{unitPrice}}]}""",
        fields = listOf(f("orderNumber", "Auftragsnummer"), f("customerName", "Kunde"), f("dueDate", "Fällig", ModuleActionFieldType.DATE, "$today"), f("productId", "Artikel"), f("quantity", "Menge", ModuleActionFieldType.NUMBER, "1"), f("unitPrice", "Preis", ModuleActionFieldType.NUMBER, "0")),
    )

    private fun salesFulfill() = action(
        "sales-order-fulfill", "Verkauf liefern", ModuleActionMethod.POST, "/api/supply-chain/sales-orders/{id}/fulfill",
        body = """{"warehouseId":{warehouseId}}""",
        fields = listOf(f("id", "Verkauf-ID", ModuleActionFieldType.NUMBER), f("warehouseId", "Lager")),
    )

    private fun receivingPreview() = action(
        "receiving-preview",
        "Wareneingang prüfen",
        ModuleActionMethod.POST,
        "/api/supply-chain/receiving/preview",
        body = """{"scanValue":"{scanValue}","documentText":"{documentText}","fileName":"{fileName}","detectedCodes":{raw:detectedCodes}}""",
        fields = listOf(
            f("scanValue", "Scan / Referenz", required = false),
            f("documentText", "Dokumenttext", ModuleActionFieldType.MULTILINE, required = false),
            f("fileName", "Dateiname", required = false),
            f("detectedCodes", "Erkannte Codes JSON", ModuleActionFieldType.MULTILINE, "[]"),
        ),
    )

    private fun receivingApply() = action(
        "receiving-apply",
        "Wareneingang buchen",
        ModuleActionMethod.POST,
        "/api/supply-chain/receiving/apply",
        body = """{"warehouseId":{warehouseId},"reference":"{reference}","purchaseOrderId":{purchaseOrderId},"completePurchaseOrder":{completePurchaseOrder},"items":{raw:items}}""",
        fields = listOf(
            f("warehouseId", "Lager"),
            f("reference", "Referenz", required = false),
            f("purchaseOrderId", "Einkauf-ID", ModuleActionFieldType.NUMBER, "null", required = false),
            f("completePurchaseOrder", "Bestellung abschließen", ModuleActionFieldType.BOOLEAN, "true"),
            f("items", "Positionen JSON", ModuleActionFieldType.MULTILINE, """[{"productId":0,"quantity":1}]"""),
        ),
        refreshHistory = true,
    )

    private fun replenishmentPreview() = action(
        "replenishment-preview",
        "Nachschub Vorschau",
        ModuleActionMethod.POST,
        "/api/supply-chain/procurement/replenishment-preview",
        body = """{"productIds":{raw:productIds},"planningHorizonDays":{planningHorizonDays},"safetyDays":{safetyDays},"serviceLevelTarget":{serviceLevelTarget},"supplierPreferences":{raw:supplierPreferences}}""",
        fields = listOf(
            f("productIds", "Artikel-IDs JSON", ModuleActionFieldType.MULTILINE, "[]"),
            f("planningHorizonDays", "Planungstage", ModuleActionFieldType.NUMBER, "30"),
            f("safetyDays", "Sicherheitstage", ModuleActionFieldType.NUMBER, "7"),
            f("serviceLevelTarget", "Service-Level", ModuleActionFieldType.NUMBER, "0.95"),
            f("supplierPreferences", "Lieferanten JSON", ModuleActionFieldType.MULTILINE, "[]", required = false),
        ),
    )

    private fun autoReplenish() = action(
        "auto-replenish",
        "Nachschub automatisch",
        ModuleActionMethod.POST,
        "/api/supply-chain/procurement/auto-replenish",
        body = """{"productIds":{raw:productIds},"planningHorizonDays":{planningHorizonDays},"safetyDays":{safetyDays},"serviceLevelTarget":{serviceLevelTarget},"supplierPreferences":{raw:supplierPreferences}}""",
        fields = listOf(
            f("productIds", "Artikel-IDs JSON", ModuleActionFieldType.MULTILINE, "[]"),
            f("planningHorizonDays", "Planungstage", ModuleActionFieldType.NUMBER, "30"),
            f("safetyDays", "Sicherheitstage", ModuleActionFieldType.NUMBER, "7"),
            f("serviceLevelTarget", "Service-Level", ModuleActionFieldType.NUMBER, "0.95"),
            f("supplierPreferences", "Lieferanten JSON", ModuleActionFieldType.MULTILINE, "[]", required = false),
        ),
        refreshHistory = true,
    )

    private fun wavePick() = action(
        "wave-pick",
        "Pick-Waves planen",
        ModuleActionMethod.POST,
        "/api/supply-chain/sales-orders/pick-waves",
        body = """{"salesOrderIds":{raw:salesOrderIds},"maxOrdersPerWave":{maxOrdersPerWave},"includeDrafts":{includeDrafts}}""",
        fields = listOf(
            f("salesOrderIds", "Verkaufs-IDs JSON", ModuleActionFieldType.MULTILINE, "[]"),
            f("maxOrdersPerWave", "Max. Aufträge/Wave", ModuleActionFieldType.NUMBER, "10"),
            f("includeDrafts", "Entwürfe einbeziehen", ModuleActionFieldType.BOOLEAN, "false"),
        ),
    )

    private fun serviceStatus(today: LocalDate) = action(
        "service-request-status", "Service aktualisieren", ModuleActionMethod.POST, "/api/supply-chain/service-requests/{id}/status",
        body = """{"status":"{status}","closedDate":"{closedDate}"}""",
        fields = listOf(f("id", "Service-ID", ModuleActionFieldType.NUMBER), f("status", "Status", default = "CLOSED"), f("closedDate", "Abschluss", ModuleActionFieldType.DATE, "$today")),
    )

    private fun productionStatus(today: LocalDate) = action(
        "production-order-status", "Produktion aktualisieren", ModuleActionMethod.POST, "/api/supply-chain/production-orders/{id}/status",
        body = """{"status":"{status}","startDate":"{startDate}","completionDate":"{completionDate}"}""",
        fields = listOf(f("id", "Produktion-ID", ModuleActionFieldType.NUMBER), f("status", "Status", default = "COMPLETED"), f("startDate", "Start", ModuleActionFieldType.DATE, "$today"), f("completionDate", "Fertig", ModuleActionFieldType.DATE, "$today")),
    )

    private fun approveCorrection() = action("correction-approve", "Korrektur genehmigen", ModuleActionMethod.POST, "/api/correction/approve/{id}?comment={comment}", fields = listOf(f("id", "Korrektur-ID", ModuleActionFieldType.NUMBER), f("comment", "Kommentar", ModuleActionFieldType.MULTILINE, required = false)), refreshHistory = true)
    private fun denyCorrection() = action("correction-deny", "Korrektur ablehnen", ModuleActionMethod.POST, "/api/correction/deny/{id}?comment={comment}", fields = listOf(f("id", "Korrektur-ID", ModuleActionFieldType.NUMBER), f("comment", "Kommentar", ModuleActionFieldType.MULTILINE, required = false)), refreshHistory = true)
    private fun approveVacation() = action("vacation-approve", "Urlaub genehmigen", ModuleActionMethod.POST, "/api/vacation/approve/{id}", fields = listOf(f("id", "Urlaubs-ID", ModuleActionFieldType.NUMBER)))
    private fun denyVacation() = action("vacation-deny", "Urlaub ablehnen", ModuleActionMethod.POST, "/api/vacation/deny/{id}", fields = listOf(f("id", "Urlaubs-ID", ModuleActionFieldType.NUMBER)))
    private fun vacationDelete() = action("vacation-delete", "Urlaub löschen", ModuleActionMethod.DELETE, "/api/vacation/{id}", fields = listOf(f("id", "Urlaubs-ID", ModuleActionFieldType.NUMBER)))
    private fun sickLeaveDelete() = action("sick-delete", "Krankheit löschen", ModuleActionMethod.DELETE, "/api/sick-leave/{id}", fields = listOf(f("id", "Krankheits-ID", ModuleActionFieldType.NUMBER)))

    private fun adminEditDay(today: LocalDate) = action(
        "admin-edit-day",
        "Zeit-Tag bearbeiten",
        ModuleActionMethod.PUT,
        "/api/admin/timetracking/editDay/{targetUsername}/{date}",
        body = "{raw:entries}",
        fields = listOf(
            f("targetUsername", "Mitarbeiter"),
            f("date", "Datum", ModuleActionFieldType.DATE, "$today"),
            f("entries", "Zeit-Einträge JSON", ModuleActionFieldType.MULTILINE, "[]"),
        ),
        description = "Kompletten Tag als JSON-Liste speichern, wie im Web-Admin.",
        refreshHistory = true,
    )

    private fun timeEntryApprove() = action("time-entry-approve", "Zeit freigeben", ModuleActionMethod.POST, "/api/timetracking/entry/{id}/approve", fields = listOf(f("id", "Zeit-Eintrag", ModuleActionFieldType.NUMBER)), refreshHistory = true)
    private fun timeEntryRevoke() = action("time-entry-revoke", "Zeitfreigabe aufheben", ModuleActionMethod.POST, "/api/timetracking/entry/{id}/revoke-approval", fields = listOf(f("id", "Zeit-Eintrag", ModuleActionFieldType.NUMBER)), refreshHistory = true)
    private fun timeEntryCustomer() = action("time-entry-customer", "Eintrag Kunde", ModuleActionMethod.PUT, "/api/timetracking/entry/{id}/customer?customerId={customerId}", fields = listOf(f("id", "Zeit-Eintrag", ModuleActionFieldType.NUMBER), f("customerId", "Kunde", ModuleActionFieldType.NUMBER, "0")), refreshHistory = true)
    private fun timeEntryProject() = action("time-entry-project", "Eintrag Projekt", ModuleActionMethod.PUT, "/api/timetracking/entry/{id}/project?projectId={projectId}", fields = listOf(f("id", "Zeit-Eintrag", ModuleActionFieldType.NUMBER), f("projectId", "Projekt", ModuleActionFieldType.NUMBER, "0")), refreshHistory = true)
    private fun userDayCustomer(today: LocalDate) = action("user-day-customer", "Tag Kunde zuweisen", ModuleActionMethod.PUT, "/api/timetracking/day/customer?username={username}&date={date}&customerId={customerId}", fields = listOf(f("date", "Datum", ModuleActionFieldType.DATE, "$today"), f("customerId", "Kunde", ModuleActionFieldType.NUMBER, "0")), refreshHistory = true)
    private fun userDayProject(today: LocalDate) = action("user-day-project", "Tag Projekt zuweisen", ModuleActionMethod.PUT, "/api/timetracking/day/project?username={username}&date={date}&projectId={projectId}", fields = listOf(f("date", "Datum", ModuleActionFieldType.DATE, "$today"), f("projectId", "Projekt", ModuleActionFieldType.NUMBER, "0")), refreshHistory = true)
    private fun dayCustomer(today: LocalDate) = action("day-customer", "Tag Kunde", ModuleActionMethod.PUT, "/api/timetracking/day/customer?username={targetUsername}&date={date}&customerId={customerId}", fields = listOf(f("targetUsername", "Mitarbeiter"), f("date", "Datum", ModuleActionFieldType.DATE, "$today"), f("customerId", "Kunde", ModuleActionFieldType.NUMBER, "0")), refreshHistory = true)
    private fun dayProject(today: LocalDate) = action("day-project", "Tag Projekt", ModuleActionMethod.PUT, "/api/timetracking/day/project?username={targetUsername}&date={date}&projectId={projectId}", fields = listOf(f("targetUsername", "Mitarbeiter"), f("date", "Datum", ModuleActionFieldType.DATE, "$today"), f("projectId", "Projekt", ModuleActionFieldType.NUMBER, "0")), refreshHistory = true)

    private fun adminVacation(today: LocalDate) = action(
        "admin-vacation-create", "Urlaub für Mitarbeiter", ModuleActionMethod.POST,
        "/api/vacation/adminCreate?adminUsername={username}&username={targetUsername}&startDate={startDate}&endDate={endDate}&halfDay={halfDay}&usesOvertime={usesOvertime}",
        fields = listOf(f("targetUsername", "Mitarbeiter"), f("startDate", "Start", ModuleActionFieldType.DATE, "$today"), f("endDate", "Ende", ModuleActionFieldType.DATE, "$today"), f("halfDay", "Halber Tag", ModuleActionFieldType.BOOLEAN, "false"), f("usesOvertime", "Überzeit verwenden", ModuleActionFieldType.BOOLEAN, "false")),
    )

    private fun adminSickLeave(today: LocalDate) = action(
        "admin-sick-create", "Krankheit für Mitarbeiter", ModuleActionMethod.POST,
        "/api/sick-leave/report?targetUsername={targetUsername}&startDate={startDate}&endDate={endDate}&halfDay={halfDay}&comment={comment}",
        fields = listOf(f("targetUsername", "Mitarbeiter"), f("startDate", "Start", ModuleActionFieldType.DATE, "$today"), f("endDate", "Ende", ModuleActionFieldType.DATE, "$today"), f("halfDay", "Halber Tag", ModuleActionFieldType.BOOLEAN, "false"), f("comment", "Kommentar", ModuleActionFieldType.MULTILINE, required = false)),
    )

    private fun userCreate() = action(
        "user-create", "Mitarbeiter anlegen", ModuleActionMethod.POST, "/api/admin/users",
        body = """{"username":"{newUsername}","password":"{password}","firstName":"{firstName}","lastName":"{lastName}","country":"{country}","taxClass":"{taxClass}","tarifCode":"{tarifCode}","personnelNumber":"{personnelNumber}","roles":["{role}"],"dailyWorkHours":{dailyWorkHours},"isHourly":{isHourly},"isPercentage":{isPercentage},"workPercentage":{workPercentage},"expectedWorkDays":{expectedWorkDays}}""",
        fields = listOf(f("newUsername", "Benutzername"), f("password", "Startpasswort", ModuleActionFieldType.PASSWORD), f("firstName", "Vorname"), f("lastName", "Nachname"), f("country", "Land", default = "CH"), f("taxClass", "Steuerklasse DE", required = false), f("tarifCode", "Tarifcode CH", default = "A0"), f("personnelNumber", "Personalnummer"), f("role", "Rolle", default = "ROLE_USER"), f("dailyWorkHours", "Tages-Soll", ModuleActionFieldType.NUMBER, "8.5"), f("isHourly", "Stundenlohn", ModuleActionFieldType.BOOLEAN, "false"), f("isPercentage", "Prozentmodell", ModuleActionFieldType.BOOLEAN, "false"), f("workPercentage", "Pensum %", ModuleActionFieldType.NUMBER, "100"), f("expectedWorkDays", "Arbeitstage", ModuleActionFieldType.NUMBER, "5")),
    )

    private fun userDelete() = action("user-delete", "Mitarbeiter löschen", ModuleActionMethod.DELETE, "/api/admin/users/{id}", fields = listOf(f("id", "Benutzer-ID", ModuleActionFieldType.NUMBER)))
    private fun userVisibilityUpdate() = action("user-visibility-update", "Zeitübersicht umschalten", ModuleActionMethod.PATCH, "/api/admin/users/{id}/time-tracking-visibility", body = """{"includeInTimeTracking":{includeInTimeTracking}}""", fields = listOf(f("id", "Mitarbeiter"), f("includeInTimeTracking", "In Zeitübersicht anzeigen", ModuleActionFieldType.BOOLEAN, "true")))
    private fun customerCreate() = action("customer-create", "Kunde anlegen", ModuleActionMethod.POST, "/api/customers", body = """{"name":"{name}"}""", fields = listOf(f("name", "Kundenname")))
    private fun customerUpdate() = action("customer-update", "Kunde bearbeiten", ModuleActionMethod.PUT, "/api/customers/{id}", body = """{"name":"{name}"}""", fields = listOf(f("id", "Kunde"), f("name", "Kundenname")))
    private fun customerDelete() = action("customer-delete", "Kunde löschen", ModuleActionMethod.DELETE, "/api/customers/{id}", fields = listOf(f("id", "Kunden-ID", ModuleActionFieldType.NUMBER)))
    private fun projectCreate() = action("project-create", "Projekt anlegen", ModuleActionMethod.POST, "/api/projects", body = """{"name":"{name}","customer":{"id":{customerId}},"budgetMinutes":{budgetMinutes},"hourlyRate":{hourlyRate}}""", fields = listOf(f("name", "Projektname"), f("customerId", "Kunden-ID", ModuleActionFieldType.NUMBER), f("budgetMinutes", "Budget Minuten", ModuleActionFieldType.NUMBER, "0"), f("hourlyRate", "Stundensatz", ModuleActionFieldType.NUMBER, "0")))
    private fun projectUpdate() = action("project-update", "Projekt bearbeiten", ModuleActionMethod.PUT, "/api/projects/{id}", body = """{"name":"{name}","customer":{"id":{customerId}},"budgetMinutes":{budgetMinutes},"parent":{"id":{parentId}},"hourlyRate":{hourlyRate}}""", fields = listOf(f("id", "Projekt"), f("name", "Projektname"), f("customerId", "Kunde"), f("budgetMinutes", "Budget Minuten", ModuleActionFieldType.NUMBER, "0"), f("parentId", "Parent Projekt-ID", ModuleActionFieldType.NUMBER, "null", required = false), f("hourlyRate", "Stundensatz", ModuleActionFieldType.NUMBER, "0")))
    private fun projectDelete() = action("project-delete", "Projekt löschen", ModuleActionMethod.DELETE, "/api/projects/{id}", fields = listOf(f("id", "Projekt-ID", ModuleActionFieldType.NUMBER)))
    private fun taskCreate() = action("task-create", "Task anlegen", ModuleActionMethod.POST, "/api/tasks", body = """{"name":"{name}","project":{"id":{projectId}},"budgetMinutes":{budgetMinutes},"billable":{billable}}""", fields = listOf(f("name", "Taskname"), f("projectId", "Projekt-ID", ModuleActionFieldType.NUMBER), f("budgetMinutes", "Budget Minuten", ModuleActionFieldType.NUMBER, "0"), f("billable", "Verrechenbar", ModuleActionFieldType.BOOLEAN, "true")))
    private fun taskUpdate() = action("task-update", "Task bearbeiten", ModuleActionMethod.PUT, "/api/tasks/{id}", body = """{"name":"{name}","budgetMinutes":{budgetMinutes},"billable":{billable}}""", fields = listOf(f("id", "Task-ID", ModuleActionFieldType.NUMBER), f("name", "Taskname"), f("budgetMinutes", "Budget Minuten", ModuleActionFieldType.NUMBER, "0"), f("billable", "Verrechenbar", ModuleActionFieldType.BOOLEAN, "true")))
    private fun taskDelete() = action("task-delete", "Task löschen", ModuleActionMethod.DELETE, "/api/tasks/{id}", fields = listOf(f("id", "Task-ID", ModuleActionFieldType.NUMBER)))
    private fun timeImport() = action("time-import-json", "Zeitimport JSON", ModuleActionMethod.POST, "/api/admin/timetracking/import/json", body = "{raw:rows}", fields = listOf(f("rows", "JSON-Zeilen", ModuleActionFieldType.MULTILINE, "[]")), refreshHistory = true)
    private fun rebuildBalances() = action("time-rebuild-balances", "Salden neu berechnen", ModuleActionMethod.POST, "/api/admin/timetracking/rebuild-balances", refreshHistory = true)
    private fun scheduleEntry(today: LocalDate) = action("schedule-entry-create", "Schicht eintragen", ModuleActionMethod.POST, "/api/admin/schedule", body = """{"userId":{userId},"date":"{date}","shift":"{shift}","note":"{note}"}""", fields = listOf(f("userId", "Benutzer-ID", ModuleActionFieldType.NUMBER), f("date", "Datum", ModuleActionFieldType.DATE, "$today"), f("shift", "Schicht-Key"), f("note", "Notiz", ModuleActionFieldType.MULTILINE, required = false)))
    private fun scheduleEntryUpdate(today: LocalDate) = action("schedule-entry-update", "Schicht bearbeiten", ModuleActionMethod.POST, "/api/admin/schedule", body = """{"id":{id},"userId":{userId},"date":"{date}","shift":"{shift}","note":"{note}"}""", fields = listOf(f("id", "Dienstplan-ID", ModuleActionFieldType.NUMBER), f("userId", "Benutzer-ID", ModuleActionFieldType.NUMBER), f("date", "Datum", ModuleActionFieldType.DATE, "$today"), f("shift", "Schicht-Key"), f("note", "Notiz", ModuleActionFieldType.MULTILINE, required = false)))
    private fun scheduleAutofill(today: LocalDate) = action("schedule-autofill", "Dienstplan Autofill", ModuleActionMethod.POST, "/api/admin/schedule/autofill", body = "{raw:entries}", fields = listOf(f("entries", "Schichten JSON", ModuleActionFieldType.MULTILINE, """[{"userId":0,"date":"$today","shift":"EARLY"}]""")), refreshHistory = true)
    private fun scheduleCopy(today: LocalDate) = action("schedule-copy", "Woche kopieren", ModuleActionMethod.POST, "/api/admin/schedule/copy", body = "{raw:entries}", fields = listOf(f("entries", "Neue Woche JSON", ModuleActionFieldType.MULTILINE, """[{"userId":0,"date":"$today","shift":"EARLY"}]""")), refreshHistory = true)
    private fun scheduleDelete() = action("schedule-entry-delete", "Schicht löschen", ModuleActionMethod.DELETE, "/api/admin/schedule/{id}", fields = listOf(f("id", "Eintrag-ID", ModuleActionFieldType.NUMBER)))
    private fun shiftDefinition() = action("shift-definition-create", "Schichtdefinition speichern", ModuleActionMethod.POST, "/api/admin/shift-definitions", body = """{"shiftKey":"{shiftKey}","label":"{label}","startTime":"{startTime}","endTime":"{endTime}","isActive":{isActive}}""", fields = listOf(f("shiftKey", "Schicht-Key", default = "EARLY"), f("label", "Name"), f("startTime", "Start HH:mm", default = "08:00"), f("endTime", "Ende HH:mm", default = "17:00"), f("isActive", "Aktiv", ModuleActionFieldType.BOOLEAN, "true")))
    private fun shiftDefinitionUpdate() = action("shift-definition-update", "Schichtdefinition bearbeiten", ModuleActionMethod.POST, "/api/admin/shift-definitions", body = """{"id":{id},"shiftKey":"{shiftKey}","label":"{label}","startTime":"{startTime}","endTime":"{endTime}","isActive":{isActive}}""", fields = listOf(f("id", "Schicht"), f("shiftKey", "Schicht-Key", required = false), f("label", "Name"), f("startTime", "Start HH:mm", default = "08:00"), f("endTime", "Ende HH:mm", default = "17:00"), f("isActive", "Aktiv", ModuleActionFieldType.BOOLEAN, "true")))
    private fun scheduleRuleCreate(today: LocalDate) = action("schedule-rule-create", "Planregel anlegen", ModuleActionMethod.POST, "/api/admin/schedule-rules", body = """{"userId":{userId},"ruleType":"{ruleType}","startDate":"{startDate}","repeatIntervalDays":{repeatIntervalDays},"dayOfWeek":{dayOfWeek},"dayMode":"{dayMode}"}""", fields = listOf(f("userId", "Mitarbeiter", ModuleActionFieldType.NUMBER), f("ruleType", "Regeltyp", default = "REPEATING_DAY_MODE"), f("startDate", "Start", ModuleActionFieldType.DATE, "$today"), f("repeatIntervalDays", "Intervall Tage", ModuleActionFieldType.NUMBER, "14"), f("dayOfWeek", "Wochentag 1-7", ModuleActionFieldType.NUMBER, "5"), f("dayMode", "Modus", default = "OFF")), refreshHistory = true)
    private fun scheduleRuleUpdate(today: LocalDate) = action("schedule-rule-update", "Planregel bearbeiten", ModuleActionMethod.PUT, "/api/admin/schedule-rules/{id}", body = """{"ruleType":"{ruleType}","startDate":"{startDate}","repeatIntervalDays":{repeatIntervalDays},"dayOfWeek":{dayOfWeek},"dayMode":"{dayMode}"}""", fields = listOf(f("id", "Planregel", ModuleActionFieldType.NUMBER), f("ruleType", "Regeltyp", default = "REPEATING_DAY_MODE"), f("startDate", "Start", ModuleActionFieldType.DATE, "$today"), f("repeatIntervalDays", "Intervall Tage", ModuleActionFieldType.NUMBER, "14"), f("dayOfWeek", "Wochentag 1-7", ModuleActionFieldType.NUMBER, "5"), f("dayMode", "Modus", default = "OFF")), refreshHistory = true)
    private fun scheduleRuleDelete() = action("schedule-rule-delete", "Planregel löschen", ModuleActionMethod.DELETE, "/api/admin/schedule-rules/{id}", fields = listOf(f("id", "Planregel", ModuleActionFieldType.NUMBER)), refreshHistory = true)
    private fun payslipGenerate(today: LocalDate) = action("payslip-generate", "Lohnabrechnung generieren", ModuleActionMethod.POST, "/api/payslips/generate?userId={userId}&start={start}&end={end}&payoutOvertime={payoutOvertime}", fields = listOf(f("userId", "Mitarbeiter", ModuleActionFieldType.NUMBER), f("start", "Start", ModuleActionFieldType.DATE, "${today.withDayOfMonth(1)}"), f("end", "Ende", ModuleActionFieldType.DATE, "$today"), f("payoutOvertime", "Überzeit auszahlen", ModuleActionFieldType.BOOLEAN, "false")))
    private fun payslipApprove() = action("payslip-approve", "Lohnabrechnung freigeben", ModuleActionMethod.POST, "/api/payslips/approve/{id}?comment={comment}", fields = listOf(f("id", "Abrechnung", ModuleActionFieldType.NUMBER), f("comment", "Kommentar", ModuleActionFieldType.MULTILINE, required = false)))
    private fun payslipApproveAll() = action("payslip-approve-all", "Alle freigeben", ModuleActionMethod.POST, "/api/payslips/approve-all?comment={comment}", fields = listOf(f("comment", "Kommentar", ModuleActionFieldType.MULTILINE, required = false)))
    private fun payslipSetPayout(today: LocalDate) = action("payslip-set-payout", "Auszahlung setzen", ModuleActionMethod.POST, "/api/payslips/set-payout/{id}?payoutDate={payoutDate}", fields = listOf(f("id", "Abrechnung", ModuleActionFieldType.NUMBER), f("payoutDate", "Auszahlung", ModuleActionFieldType.DATE, "$today")))
    private fun payslipScheduleUser(today: LocalDate) = action("payslip-schedule-user", "Auszahlungstag je Mitarbeiter", ModuleActionMethod.POST, "/api/payslips/schedule?userId={userId}&day={day}", fields = listOf(f("userId", "Mitarbeiter", ModuleActionFieldType.NUMBER), f("day", "Tag im Monat", ModuleActionFieldType.NUMBER, "${today.dayOfMonth}")))
    private fun payslipScheduleAll(today: LocalDate) = action("payslip-schedule-all", "Auszahlungstag für alle", ModuleActionMethod.POST, "/api/payslips/schedule-all?day={day}", fields = listOf(f("day", "Tag im Monat", ModuleActionFieldType.NUMBER, "${today.dayOfMonth}")))
    private fun payslipReopen() = action("payslip-reopen", "Abrechnung wieder öffnen", ModuleActionMethod.POST, "/api/payslips/reopen/{id}", fields = listOf(f("id", "Abrechnung", ModuleActionFieldType.NUMBER)))
    private fun payslipDelete() = action("payslip-delete", "Abrechnung löschen", ModuleActionMethod.DELETE, "/api/payslips/{id}", fields = listOf(f("id", "Abrechnung", ModuleActionFieldType.NUMBER)))
    private fun accountCreate() = action("account-create", "Konto anlegen", ModuleActionMethod.POST, "/api/accounting/accounts", body = """{"code":"{code}","name":"{name}","type":"{type}","active":{active}}""", fields = listOf(f("code", "Kontocode"), f("name", "Name"), f("type", "Typ", default = "ASSET"), f("active", "Aktiv", ModuleActionFieldType.BOOLEAN, "true")))
    private fun journalEntry(today: LocalDate) = action(
        "journal-create", "Journalbuchung", ModuleActionMethod.POST, "/api/accounting/journal",
        body = """{"entryDate":"{entryDate}","description":"{description}","source":"MANUAL","documentReference":"{documentReference}","lines":[{"accountId":{debitAccountId},"debit":{debit},"credit":0,"memo":"{memo}"},{"accountId":{creditAccountId},"debit":0,"credit":{credit},"memo":"{memo}"}]}""",
        fields = listOf(f("entryDate", "Datum", ModuleActionFieldType.DATE, "$today"), f("description", "Beschreibung"), f("documentReference", "Beleg", required = false), f("debitAccountId", "Soll-Konto", ModuleActionFieldType.NUMBER), f("creditAccountId", "Haben-Konto", ModuleActionFieldType.NUMBER), f("debit", "Soll", ModuleActionFieldType.NUMBER, "0"), f("credit", "Haben", ModuleActionFieldType.NUMBER, "0"), f("memo", "Notiz", required = false)),
    )
    private fun assetCreate(today: LocalDate) = action("asset-create", "Anlage anlegen", ModuleActionMethod.POST, "/api/accounting/assets", body = """{"assetName":"{assetName}","acquisitionCost":{acquisitionCost},"acquisitionDate":"{acquisitionDate}","usefulLifeMonths":{usefulLifeMonths},"residualValue":{residualValue},"status":"{status}"}""", fields = listOf(f("assetName", "Anlagename"), f("acquisitionCost", "Anschaffungskosten", ModuleActionFieldType.NUMBER, "0"), f("acquisitionDate", "Anschaffungsdatum", ModuleActionFieldType.DATE, "$today"), f("usefulLifeMonths", "Nutzungsdauer Monate", ModuleActionFieldType.NUMBER, "36"), f("residualValue", "Restwert", ModuleActionFieldType.NUMBER, "0"), f("status", "Status", default = "ACTIVE")))
    private fun assetDepreciate() = action("asset-depreciate", "Anlage abschreiben", ModuleActionMethod.POST, "/api/accounting/assets/{id}/depreciate", fields = listOf(f("id", "Anlagen-ID", ModuleActionFieldType.NUMBER)))
    private fun receivablePayment(today: LocalDate) = action("receivable-payment", "Debitor Zahlung", ModuleActionMethod.POST, "/api/accounting/receivables/{id}/payments", body = """{"amount":{amount},"paymentDate":"{paymentDate}","memo":"{memo}"}""", fields = listOf(f("id", "Debitor-ID", ModuleActionFieldType.NUMBER), f("amount", "Betrag", ModuleActionFieldType.NUMBER, "0"), f("paymentDate", "Datum", ModuleActionFieldType.DATE, "$today"), f("memo", "Notiz", required = false)))
    private fun payablePayment(today: LocalDate) = action("payable-payment", "Kreditor Zahlung", ModuleActionMethod.POST, "/api/accounting/payables/{id}/payments", body = """{"amount":{amount},"paymentDate":"{paymentDate}","memo":"{memo}"}""", fields = listOf(f("id", "Kreditor-ID", ModuleActionFieldType.NUMBER), f("amount", "Betrag", ModuleActionFieldType.NUMBER, "0"), f("paymentDate", "Datum", ModuleActionFieldType.DATE, "$today"), f("memo", "Notiz", required = false)))
    private fun chronoTwoProduct() = action("chrono2-product-create", "Chrono 2.0 Produkt", ModuleActionMethod.POST, "/api/chrono2/products", body = """{"id":"{productId}","name":"{name}","category":"{category}","weightKg":{weightKg},"volumeCubicM":{volumeCubicM},"costPrice":{costPrice},"salesPrice":{salesPrice},"attributes":[]}""", fields = listOf(f("productId", "Produkt-ID"), f("name", "Name"), f("category", "Kategorie", default = "General"), f("weightKg", "Gewicht kg", ModuleActionFieldType.NUMBER, "1"), f("volumeCubicM", "Volumen m3", ModuleActionFieldType.NUMBER, "0.1"), f("costPrice", "Kosten", ModuleActionFieldType.NUMBER, "0"), f("salesPrice", "Preis", ModuleActionFieldType.NUMBER, "0")))
    private fun chronoTwoSlotting() = action("chrono2-slotting", "Slotting berechnen", ModuleActionMethod.POST, "/api/chrono2/slotting", body = """{"productId":"{productId}","weightKg":{weightKg},"volumeCubicM":{volumeCubicM},"zonePreference":"{zonePreference}","expectedTurnoverDays":{expectedTurnoverDays}}""", fields = listOf(f("productId", "Produkt-ID"), f("weightKg", "Gewicht kg", ModuleActionFieldType.NUMBER, "1"), f("volumeCubicM", "Volumen m3", ModuleActionFieldType.NUMBER, "0.1"), f("zonePreference", "Zone", default = "A"), f("expectedTurnoverDays", "Umschlag Tage", ModuleActionFieldType.NUMBER, "14")))
    private fun chronoTwoIot() = action("chrono2-iot", "IoT Messung", ModuleActionMethod.POST, "/api/chrono2/iot", body = """{"locationId":"{locationId}","temperature":{temperature},"humidity":{humidity},"weight":{weight}}""", fields = listOf(f("locationId", "Lagerort"), f("temperature", "Temperatur", ModuleActionFieldType.NUMBER, "20"), f("humidity", "Feuchte", ModuleActionFieldType.NUMBER, "40"), f("weight", "Gewicht", ModuleActionFieldType.NUMBER, "0")))
    private fun chronoTwoMovement() = action("chrono2-movement", "Blockchain Bewegung", ModuleActionMethod.POST, "/api/chrono2/blockchain/movement?productId={productId}&from={from}&to={to}&quantity={quantity}", fields = listOf(f("productId", "Produkt-ID"), f("from", "Von", required = false), f("to", "Nach"), f("quantity", "Menge", ModuleActionFieldType.NUMBER, "1")))
    private fun chronoTwoSourcing() = action("chrono2-sourcing", "Supplier Empfehlung", ModuleActionMethod.POST, "/api/chrono2/procurement/sourcing", body = """{"productId":"{productId}","quantity":{quantity},"preferences":[]}""", fields = listOf(f("productId", "Produkt-ID"), f("quantity", "Menge", ModuleActionFieldType.NUMBER, "1")))
    private fun chronoTwoPickRoute() = action("chrono2-pick-route", "Pick Route", ModuleActionMethod.POST, "/api/chrono2/outbound/pick-route", body = """{"items":[{"productId":"{productId}","quantity":{quantity}}]}""", fields = listOf(f("productId", "Produkt-ID"), f("quantity", "Menge", ModuleActionFieldType.NUMBER, "1")))
    private fun chronoTwoReturn() = action("chrono2-return", "Retour erfassen", ModuleActionMethod.POST, "/api/chrono2/outbound/returns", body = """{"productId":"{productId}","reason":"{reason}"}""", fields = listOf(f("productId", "Produkt-ID"), f("reason", "Grund", ModuleActionFieldType.MULTILINE)))
    private fun chronoTwoNlp() = action("chrono2-nlp", "KI Frage", ModuleActionMethod.POST, "/api/chrono2/analytics/nlp", body = """{"query":"{query}"}""", fields = listOf(f("query", "Frage", ModuleActionFieldType.MULTILINE)))
    private fun leadCreate() = action("lead-create", "Lead anlegen", ModuleActionMethod.POST, "/api/crm/leads", body = """{"companyName":"{companyName}","contactName":"{contactName}","email":"{email}","phone":"{phone}","source":"{source}","status":"{status}"}""", fields = listOf(f("companyName", "Firma"), f("contactName", "Kontakt"), f("email", "E-Mail", required = false), f("phone", "Telefon", required = false), f("source", "Quelle", default = "App"), f("status", "Status", default = "NEW")))
    private fun leadStatus() = action("lead-status", "Lead Status", ModuleActionMethod.PUT, "/api/crm/leads/{id}", body = """{"status":"{status}"}""", fields = listOf(f("id", "Lead-ID", ModuleActionFieldType.NUMBER), f("status", "Status", default = "QUALIFIED")))
    private fun opportunityCreate(today: LocalDate) = action("opportunity-create", "Opportunity anlegen", ModuleActionMethod.POST, "/api/crm/opportunities", body = """{"title":"{title}","stage":"{stage}","value":{value},"probability":{probability},"expectedCloseDate":"{expectedCloseDate}","customerId":{customerId}}""", fields = listOf(f("title", "Titel"), f("stage", "Stage", default = "QUALIFICATION"), f("value", "Wert", ModuleActionFieldType.NUMBER, "0"), f("probability", "Wahrscheinlichkeit", ModuleActionFieldType.NUMBER, "50"), f("expectedCloseDate", "Abschlussdatum", ModuleActionFieldType.DATE, "$today"), f("customerId", "Kunden-ID", ModuleActionFieldType.NUMBER, "0")))
    private fun opportunityUpdate(today: LocalDate) = action("opportunity-update", "Opportunity Status", ModuleActionMethod.PUT, "/api/crm/opportunities/{id}", body = """{"stage":"{stage}","probability":{probability},"expectedCloseDate":"{expectedCloseDate}"}""", fields = listOf(f("id", "Opportunity-ID", ModuleActionFieldType.NUMBER), f("stage", "Stage", default = "PROPOSAL"), f("probability", "Wahrscheinlichkeit", ModuleActionFieldType.NUMBER, "75"), f("expectedCloseDate", "Abschluss", ModuleActionFieldType.DATE, "$today")))
    private fun campaignCreate(today: LocalDate) = action("campaign-create", "Kampagne anlegen", ModuleActionMethod.POST, "/api/crm/campaigns", body = """{"name":"{name}","status":"{status}","startDate":"{startDate}","endDate":"{endDate}","channel":"{channel}","budget":{budget}}""", fields = listOf(f("name", "Name"), f("status", "Status", default = "PLANNED"), f("startDate", "Start", ModuleActionFieldType.DATE, "$today"), f("endDate", "Ende", ModuleActionFieldType.DATE, "${today.plusDays(30)}"), f("channel", "Kanal", default = "Online"), f("budget", "Budget", ModuleActionFieldType.NUMBER, "0")))
    private fun campaignUpdate(today: LocalDate) = action("campaign-update", "Kampagne Status", ModuleActionMethod.PUT, "/api/crm/campaigns/{id}", body = """{"name":"{name}","status":"{status}","startDate":"{startDate}","endDate":"{endDate}","budget":{budget},"channel":"{channel}"}""", fields = listOf(f("id", "Kampagnen-ID", ModuleActionFieldType.NUMBER), f("name", "Name"), f("status", "Status", default = "ACTIVE"), f("startDate", "Start", ModuleActionFieldType.DATE, "$today"), f("endDate", "Ende", ModuleActionFieldType.DATE, "${today.plusDays(30)}"), f("budget", "Budget", ModuleActionFieldType.NUMBER, "0"), f("channel", "Kanal", default = "Online")))
    private fun crmContactCreate() = action("crm-contact-create", "Kontakt anlegen", ModuleActionMethod.POST, "/api/crm/customers/{customerId}/contacts", body = """{"firstName":"{firstName}","lastName":"{lastName}","email":"{email}","phone":"{phone}","role":"{role}"}""", fields = listOf(f("customerId", "Kunde"), f("firstName", "Vorname"), f("lastName", "Nachname"), f("email", "E-Mail", required = false), f("phone", "Telefon", required = false), f("role", "Rolle", required = false)))
    private fun crmActivityCreate(now: String) = action("crm-activity-create", "Aktivität", ModuleActionMethod.POST, "/api/crm/customers/{customerId}/activities", body = """{"type":"{type}","notes":"{notes}","contactId":{contactId},"timestamp":"{timestamp}"}""", fields = listOf(f("customerId", "Kunde"), f("type", "Typ", default = "NOTE"), f("notes", "Notiz", ModuleActionFieldType.MULTILINE), f("contactId", "Kontakt-ID", ModuleActionFieldType.NUMBER, "0"), f("timestamp", "Zeitpunkt", ModuleActionFieldType.DATETIME, now)))
    private fun crmAddressCreate() = action("crm-address-create", "Adresse anlegen", ModuleActionMethod.POST, "/api/crm/customers/{customerId}/addresses", body = """{"type":"{type}","street":"{street}","postalCode":"{postalCode}","city":"{city}","country":"{country}"}""", fields = listOf(f("customerId", "Kunde"), f("type", "Typ", default = "OFFICE"), f("street", "Straße"), f("postalCode", "PLZ"), f("city", "Ort"), f("country", "Land", default = "CH")))
    private fun crmDocumentCreate() = action("crm-document-create", "Dokument-Link", ModuleActionMethod.POST, "/api/crm/customers/{customerId}/documents", body = """{"fileName":"{fileName}","url":"{url}"}""", fields = listOf(f("customerId", "Kunde"), f("fileName", "Dateiname"), f("url", "URL")))
    private fun crmDocumentDelete() = action("crm-document-delete", "Dokument löschen", ModuleActionMethod.DELETE, "/api/crm/customers/{customerId}/documents/{id}", fields = listOf(f("customerId", "Kunde"), f("id", "Dokument-ID", ModuleActionFieldType.NUMBER)))
    private fun bankAccountCreate() = action("bank-account-create", "Bankkonto anlegen", ModuleActionMethod.POST, "/api/banking/accounts", body = """{"iban":"{iban}","bic":"{bic}","name":"{name}","clearingNumber":"{clearingNumber}"}""", fields = listOf(f("iban", "IBAN"), f("bic", "BIC", required = false), f("name", "Name"), f("clearingNumber", "Clearing", required = false)))
    private fun bankAccountUpdate() = action("bank-account-update", "Bankkonto bearbeiten", ModuleActionMethod.PUT, "/api/banking/accounts/{id}", body = """{"iban":"{iban}","bic":"{bic}","name":"{name}","clearingNumber":"{clearingNumber}"}""", fields = listOf(f("id", "Bankkonto"), f("iban", "IBAN"), f("bic", "BIC", required = false), f("name", "Name"), f("clearingNumber", "Clearing", required = false)))
    private fun bankAccountDelete() = action("bank-account-delete", "Bankkonto löschen", ModuleActionMethod.DELETE, "/api/banking/accounts/{id}", fields = listOf(f("id", "Bankkonto-ID", ModuleActionFieldType.NUMBER)))
    private fun paymentBatchCreate() = action("payment-batch-create", "Zahlungslauf", ModuleActionMethod.POST, "/api/banking/batches", body = """{"bankAccountId":{bankAccountId},"instructions":[{"creditorName":"{creditorName}","creditorIban":"{creditorIban}","creditorBic":"{creditorBic}","amount":{amount},"currency":"{currency}","reference":"{reference}"}]}""", fields = listOf(f("bankAccountId", "Bankkonto"), f("creditorName", "Empfänger"), f("creditorIban", "IBAN"), f("creditorBic", "BIC", required = false), f("amount", "Betrag", ModuleActionFieldType.NUMBER, "0"), f("currency", "Währung", default = "CHF"), f("reference", "Referenz", required = false)))
    private fun paymentBatchApprove() = action("payment-batch-approve", "Zahlung freigeben", ModuleActionMethod.POST, "/api/banking/batches/{id}/approve", fields = listOf(f("id", "Zahlungslauf-ID", ModuleActionFieldType.NUMBER)))
    private fun paymentBatchTransmit() = action("payment-batch-transmit", "Zahlung senden", ModuleActionMethod.POST, "/api/banking/batches/{id}/transmit", body = """{"reference":"{reference}"}""", fields = listOf(f("id", "Zahlungslauf-ID", ModuleActionFieldType.NUMBER), f("reference", "Referenz")))
    private fun bankMessageCreate() = action("bank-message-create", "Banknachricht senden", ModuleActionMethod.POST, "/api/banking/messages", body = """{"recipient":"{recipient}","subject":"{subject}","body":"{body}","transport":"{transport}"}""", fields = listOf(f("recipient", "Empfänger"), f("subject", "Betreff"), f("body", "Nachricht", ModuleActionFieldType.MULTILINE), f("transport", "Transport", default = "EMAIL")))
    private fun signatureCreate() = action("signature-create", "Signatur anfragen", ModuleActionMethod.POST, "/api/banking/signatures", body = """{"documentType":"{documentType}","documentPath":"{documentPath}","email":"{email}"}""", fields = listOf(f("documentType", "Dokumenttyp", default = "PDF"), f("documentPath", "Dokumentpfad"), f("email", "E-Mail")))
    private fun signatureRefresh() = action("signature-refresh", "Signatur prüfen", ModuleActionMethod.POST, "/api/banking/signatures/{id}/refresh", fields = listOf(f("id", "Signatur-ID", ModuleActionFieldType.NUMBER)))
    private fun signatureComplete() = action("signature-complete", "Signatur abschließen", ModuleActionMethod.POST, "/api/banking/signatures/{id}/complete", fields = listOf(f("id", "Signatur-ID", ModuleActionFieldType.NUMBER)))
    private fun knowledgeCreate() = action("knowledge-create", "Wissenseintrag anlegen", ModuleActionMethod.POST, "/api/admin/knowledge", body = """{"title":"{title}","content":"{content}","accessLevel":"{accessLevel}"}""", fields = listOf(f("title", "Titel"), f("content", "Inhalt", ModuleActionFieldType.MULTILINE), f("accessLevel", "Zugriff", default = "ALL")))
    private fun knowledgeDelete() = action("knowledge-delete", "Wissenseintrag löschen", ModuleActionMethod.DELETE, "/api/admin/knowledge/{id}", fields = listOf(f("id", "Eintrag-ID", ModuleActionFieldType.NUMBER)))
    private fun companySettingsUpdate() = action(
        "company-settings-update",
        "Firmenparameter speichern",
        ModuleActionMethod.PUT,
        "/api/admin/company/settings",
        body = """{"uvgBuRate":{uvgBuRate},"uvgNbuRate":{uvgNbuRate},"ktgRateEmployee":{ktgRateEmployee},"ktgRateEmployer":{ktgRateEmployer},"fakRate":{fakRate},"midijobFactor":{midijobFactor},"customHolidaySelectionEnabled":{customHolidaySelectionEnabled},"holidayPreferences":{raw:holidayPreferences}}""",
        fields = listOf(
            f("uvgBuRate", "UVG BU", ModuleActionFieldType.NUMBER, "null"),
            f("uvgNbuRate", "UVG NBU", ModuleActionFieldType.NUMBER, "null"),
            f("ktgRateEmployee", "KTG AN", ModuleActionFieldType.NUMBER, "null"),
            f("ktgRateEmployer", "KTG AG", ModuleActionFieldType.NUMBER, "null"),
            f("fakRate", "FAK", ModuleActionFieldType.NUMBER, "null"),
            f("midijobFactor", "Midijob Faktor", ModuleActionFieldType.NUMBER, "null"),
            f("customHolidaySelectionEnabled", "Feiertagsauswahl", ModuleActionFieldType.BOOLEAN, "true"),
            f("holidayPreferences", "Feiertage JSON", ModuleActionFieldType.MULTILINE, "[]"),
        ),
    )
    private fun companyCreate() = action("company-create", "Firma anlegen", ModuleActionMethod.POST, "/api/superadmin/companies", body = """{"name":"{name}","addressLine1":"{addressLine1}","postalCode":"{postalCode}","city":"{city}","active":{active},"cantonAbbreviation":"{cantonAbbreviation}","customerTrackingEnabled":{customerTrackingEnabled},"enabledFeatures":["{featureKey}"]}""", fields = listOf(f("name", "Firmenname"), f("addressLine1", "Adresse", required = false), f("postalCode", "PLZ", required = false), f("city", "Ort", required = false), f("active", "Aktiv", ModuleActionFieldType.BOOLEAN, "true"), f("cantonAbbreviation", "Kanton", required = false), f("customerTrackingEnabled", "Projektzeiten", ModuleActionFieldType.BOOLEAN, "true"), f("featureKey", "Feature", default = "projects")))
    private fun companyUpdate() = action("company-update", "Firma bearbeiten", ModuleActionMethod.PUT, "/api/superadmin/companies/{id}", body = """{"name":"{name}","addressLine1":"{addressLine1}","postalCode":"{postalCode}","city":"{city}","active":{active},"customerTrackingEnabled":{customerTrackingEnabled}}""", fields = listOf(f("id", "Firma"), f("name", "Firmenname"), f("addressLine1", "Adresse", required = false), f("postalCode", "PLZ", required = false), f("city", "Ort", required = false), f("active", "Aktiv", ModuleActionFieldType.BOOLEAN, "true"), f("customerTrackingEnabled", "Projektzeiten", ModuleActionFieldType.BOOLEAN, "true")))
    private fun companyCreateWithAdmin() = action("company-create-admin", "Firma + Admin", ModuleActionMethod.POST, "/api/superadmin/companies/create-with-admin", body = """{"companyName":"{companyName}","adminUsername":"{adminUsername}","adminPassword":"{adminPassword}","adminEmail":"{adminEmail}","adminFirstName":"{adminFirstName}","adminLastName":"{adminLastName}","city":"{city}","active":true,"enabledFeatures":["{featureKey}"],"customerTrackingEnabled":true}""", fields = listOf(f("companyName", "Firma"), f("adminUsername", "Admin Benutzer"), f("adminPassword", "Admin Passwort", ModuleActionFieldType.PASSWORD), f("adminEmail", "Admin E-Mail", required = false), f("adminFirstName", "Admin Vorname", required = false), f("adminLastName", "Admin Nachname", required = false), f("city", "Ort", required = false), f("featureKey", "Feature", default = "projects")))
    private fun companyAdminCreate() = action("company-admin-create", "Admin für Firma", ModuleActionMethod.POST, "/api/superadmin/users/createAdminForCompany", body = """{"companyId":{companyId},"username":"{adminUsername}","password":"{adminPassword}","roles":[{"roleName":"ROLE_ADMIN"}]}""", fields = listOf(f("companyId", "Firma"), f("adminUsername", "Admin Benutzer"), f("adminPassword", "Admin Passwort", ModuleActionFieldType.PASSWORD)), refreshHistory = true)
    private fun companyPayment() = action("company-payment", "Zahlungsstatus", ModuleActionMethod.PUT, "/api/superadmin/companies/{id}/payment", body = """{"paymentMethod":"{paymentMethod}","paid":{paid},"canceled":{canceled}}""", fields = listOf(f("id", "Firma"), f("paymentMethod", "Zahlungsart", default = "invoice"), f("paid", "Bezahlt", ModuleActionFieldType.BOOLEAN, "true"), f("canceled", "Gekündigt", ModuleActionFieldType.BOOLEAN, "false")))
    private fun companyDelete() = action("company-delete", "Firma löschen", ModuleActionMethod.DELETE, "/api/superadmin/companies/{id}", fields = listOf(f("id", "Firma")))
    private fun excludedIpCreate() = action("excluded-ip-create", "IP ausschliessen", ModuleActionMethod.POST, "/api/superadmin/analytics/excluded-ips", body = """{"ipAddress":"{ipAddress}","label":"{label}"}""", fields = listOf(f("ipAddress", "IP-Adresse"), f("label", "Label", required = false)))
    private fun excludedIpDelete() = action("excluded-ip-delete", "IP entfernen", ModuleActionMethod.DELETE, "/api/superadmin/analytics/excluded-ips/{id}", fields = listOf(f("id", "IP-ID", ModuleActionFieldType.NUMBER)))
    private fun changelogCreate() = action("changelog-create", "Changelog veröffentlichen", ModuleActionMethod.POST, "/api/changelog", body = """{"version":"{version}","title":"{title}","content":"{content}"}""", fields = listOf(f("version", "Version"), f("title", "Titel"), f("content", "Inhalt", ModuleActionFieldType.MULTILINE)), refreshHistory = true)

    private fun action(
        id: String,
        title: String,
        method: ModuleActionMethod,
        path: String,
        body: String? = null,
        fields: List<ModuleActionField> = emptyList(),
        description: String = "",
        refreshHistory: Boolean = false,
    ) = ModuleAction(id, title, description, method, path, body, fields, refreshHistory)

    private fun f(
        key: String,
        label: String,
        type: ModuleActionFieldType = ModuleActionFieldType.TEXT,
        default: String = "",
        required: Boolean = true,
    ) = ModuleActionField(key, label, type, default, required)

    private fun renderTemplate(template: String, values: Map<String, String>, user: UserProfile, encodeForUrl: Boolean): String {
        val today = LocalDate.now()
        val reserved = mapOf(
            "username" to user.username,
            "today" to today.toString(),
            "monthStart" to today.withDayOfMonth(1).toString(),
            "weekStart" to today.minusDays((today.dayOfWeek.value - 1).toLong()).toString(),
            "weekEnd" to today.minusDays((today.dayOfWeek.value - 1).toLong()).plusDays(6).toString(),
            "now" to LocalDateTime.now().withSecond(0).withNano(0).toString(),
        )
        if (!encodeForUrl) {
            var rendered = Regex("\\{raw:([A-Za-z0-9_]+)\\}").replace(template) { match ->
                values[match.groupValues[1]] ?: reserved[match.groupValues[1]] ?: ""
            }
            Regex("^\\{([A-Za-z0-9_]+)\\}$").matchEntire(template.trim())?.let { match ->
                return values[match.groupValues[1]] ?: reserved[match.groupValues[1]] ?: ""
            }
            return Regex("\\{([A-Za-z0-9_]+)\\}").replace(rendered) { match ->
                val rawValue = values[match.groupValues[1]] ?: reserved[match.groupValues[1]] ?: ""
                rawValue.jsonEscaped()
            }
        }
        return Regex("\\{([A-Za-z0-9_]+)\\}").replace(template) { match ->
            val rawValue = values[match.groupValues[1]] ?: reserved[match.groupValues[1]] ?: ""
            if (encodeForUrl) rawValue.urlEncoded() else rawValue.jsonEscaped()
        }
    }

    private fun summarizeJson(body: String): JsonSnapshot {
        if (body.isBlank()) return JsonSnapshot(count = 0)
        val trimmed = body.trim()
        return when {
            trimmed.startsWith("[") -> summarizeArray(JSONArray(trimmed))
            trimmed.startsWith("{") -> summarizeObject(JSONObject(trimmed))
            else -> JsonSnapshot(count = 1, preview = listOf(trimmed.take(80)))
        }
    }

    private fun summarizeObject(json: JSONObject): JsonSnapshot {
        json.optJSONArray("content")?.let { content ->
            return JsonSnapshot(
                count = json.optInt("totalElements", content.length()),
                preview = previewFromArray(content),
                items = itemsFromArray(content),
            )
        }

        val arrays = json.keysList().mapNotNull { key -> json.optJSONArray(key)?.let { key to it } }
        if (arrays.size == 1) return summarizeArray(arrays.first().second)

        return JsonSnapshot(
            count = if (json.length() == 0) 0 else 1,
            preview = previewObject(json),
            message = json.optNullableString("message") ?: json.optNullableString("error"),
        )
    }

    private fun summarizeArray(array: JSONArray) =
        JsonSnapshot(array.length(), previewFromArray(array), itemsFromArray(array))

    private fun previewFromArray(array: JSONArray): List<String> =
        (0 until minOf(array.length(), 3)).mapNotNull { previewValue(array.opt(it)) }.filter { it.isNotBlank() }

    private fun itemsFromArray(array: JSONArray): List<ModuleListItem> =
        (0 until array.length())
            .mapNotNull { index -> listItemValue(array.opt(index), index) }
            .take(500)

    private fun listItemValue(value: Any?, index: Int): ModuleListItem? {
        if (value !is JSONObject) return null
        val id = value.firstString("id", "userId", "username", "orderNumber", "code", "sku") ?: return null
        val fullName = listOf(value.optNullableString("firstName"), value.optNullableString("lastName"))
            .filterNotNull()
            .joinToString(" ")
            .ifBlank { null }
        val owner = fullName ?: value.firstString("username", "targetUsername", "userName", "employeeName", "name")
        val label = when {
            value.has("requestDate") || value.has("desiredTimestamp") -> listOfNotNull(
                owner,
                value.firstString("requestDate", "desiredTimestamp")?.take(10),
            ).joinToString(" - ").ifBlank { null }
            value.has("startDate") || value.has("endDate") -> listOfNotNull(owner, value.dateRangeLabel()).joinToString(" - ").ifBlank { null }
            value.has("periodStart") || value.has("periodEnd") -> listOfNotNull(owner, value.dateRangeLabel("periodStart", "periodEnd")).joinToString(" - ").ifBlank { null }
            else -> null
        } ?: fullName ?: value.firstString(
            "name",
            "title",
            "message",
            "displayName",
            "username",
            "appMenuTitle",
            "companyName",
            "customerName",
            "projectName",
            "taskName",
            "subject",
            "orderNumber",
            "code",
            "sku",
            "iban",
            "assetName",
            "date",
            "periodStart",
        ) ?: "Eintrag ${index + 1}"
        val detail = listOfNotNull(
            value.statusLabel(),
            value.firstString("displayName", "username"),
            value.firstString("appMenuTitle", "appMenuGroup"),
            value.firstString("email", "roleName"),
            value.firstString("desiredPunchType", "punchType", "type"),
            value.firstString("customerName", "projectName", "taskName"),
            value.firstString("quantity", "quantityOnHand", "availableQuantity")?.let { "Menge $it" },
            value.firstString("reason", "comment", "adminComment"),
        ).distinct().joinToString(" | ").takeIf { it.isNotBlank() }
        return ModuleListItem(
            id = id,
            label = label,
            detail = detail,
            value = value.firstString("username", "shiftKey") ?: id,
            details = value.detailRows(),
        )
    }

    private fun previewObject(json: JSONObject): List<String> {
        previewValue(json)?.takeIf { it.isNotBlank() }?.let { return listOf(it) }
        return json.keysList().take(3).mapNotNull { key ->
            val value = json.opt(key)
            if (value == null || value == JSONObject.NULL) null else "$key: ${value.toString().take(40)}"
        }
    }

    private fun previewValue(value: Any?): String? =
        when (value) {
            null, JSONObject.NULL -> null
            is JSONObject -> {
                val fullName = listOf(value.optNullableString("firstName"), value.optNullableString("lastName"))
                    .filterNotNull()
                    .joinToString(" ")
                    .ifBlank { null }
                val primary = fullName ?: value.firstString(
                    "name", "title", "message", "displayName", "username", "appMenuTitle", "customerName", "projectName", "taskName",
                    "subject", "reference", "iban", "status", "state", "roleName", "date",
                    "startDate", "periodStart", "entryDate", "createdAt",
                )
                val status = value.statusLabel()
                when {
                    primary == null && status == null -> value.keysList().firstOrNull()?.let { key ->
                        "$key: ${value.opt(key)?.toString()?.take(40).orEmpty()}"
                    }
                    primary != null && status != null && primary != status -> "$primary - $status"
                    else -> primary ?: status
                }
            }
            is JSONArray -> "${value.length()} Einträge"
            else -> value.toString().take(80)
        }

    private fun extractErrorMessage(body: String): String {
        if (body.isBlank()) return ""
        return runCatching {
            val json = JSONObject(body)
            json.optNullableString("message") ?: json.optNullableString("error") ?: body
        }.getOrElse { body }.take(160)
    }

    private fun readBody(stream: InputStream?): String {
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }
}

private data class ModuleEndpoint(val label: String, val path: String)
private data class ApiResponse(val code: Int, val body: String)
private data class JsonSnapshot(
    val count: Int,
    val preview: List<String> = emptyList(),
    val items: List<ModuleListItem> = emptyList(),
    val message: String? = null,
)

private fun e(vararg items: Pair<String, String>): List<ModuleEndpoint> =
    items.map { ModuleEndpoint(it.first, it.second) }

private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")

private fun String.jsonEscaped(): String =
    buildString {
        this@jsonEscaped.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }

private fun JSONObject.optNullableString(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

private fun JSONObject.firstString(vararg names: String): String? =
    names.firstNotNullOfOrNull { optNullableString(it) }

private fun JSONObject.statusLabel(): String? {
    val denied = optNullableString("denied")?.equals("true", ignoreCase = true) == true
    val approved = optNullableString("approved")
    if (denied) return "Abgelehnt"
    if (approved != null) return if (approved.equals("true", ignoreCase = true)) "Genehmigt" else "Offen"
    return firstString("status", "state", "workflowStatus", "approvalStatus")
}

private fun JSONObject.dateRangeLabel(startKey: String = "startDate", endKey: String = "endDate"): String? {
    val start = optNullableString(startKey)?.take(10)
    val end = optNullableString(endKey)?.take(10)
    return when {
        start != null && end != null && start != end -> "$start bis $end"
        start != null -> start
        else -> end
    }
}

private fun JSONObject.keysList(): List<String> {
    val result = mutableListOf<String>()
    val iterator = keys()
    while (iterator.hasNext()) result.add(iterator.next())
    return result
}

private fun JSONObject.detailRows(): List<ModuleItemDetail> {
    val rows = mutableListOf<ModuleItemDetail>()
    optJSONObject("primaryTimes")?.let { primaryTimes ->
        primaryTimes.optNullableString("firstStartTime")?.let { rows += ModuleItemDetail("Start", it.take(5)) }
        primaryTimes.optNullableString("lastEndTime")?.let { rows += ModuleItemDetail("Ende", it.take(5)) }
        primaryTimes.optNullableString("open")?.let { rows += ModuleItemDetail("Offen", it.renderBooleanText()) }
        primaryTimes.optNullableString("isOpen")?.let { rows += ModuleItemDetail("Offen", it.renderBooleanText()) }
    }
    optJSONArray("entries")?.let { entries ->
        rows += ModuleItemDetail("Stempel", "${entries.length()} Einträge")
        rows += ModuleItemDetail("Einträge JSON", entries.toAdminEditEntriesJson())
        (0 until minOf(entries.length(), 6)).forEach { index ->
            val entry = entries.optJSONObject(index) ?: return@forEach
            rows += ModuleItemDetail("Stempel ${index + 1}", entry.renderPunchEntry())
        }
    }

    val preferredKeys = listOf(
        "id",
        "createdAt",
        "message",
        "displayName",
        "username",
        "appMenuTitle",
        "appMenuGroup",
        "appVersionName",
        "appVersionCode",
        "deviceInfo",
        "firstName",
        "lastName",
        "email",
        "mobilePhone",
        "personnelNumber",
        "department",
        "userId",
        "roleName",
        "country",
        "taxClass",
        "tarifCode",
        "canton",
        "name",
        "title",
        "companyName",
        "addressLine1",
        "postalCode",
        "city",
        "userCount",
        "customerName",
        "projectName",
        "taskName",
        "status",
        "state",
        "workflowStatus",
        "approvalStatus",
        "shiftKey",
        "shift",
        "label",
        "startTime",
        "endTime",
        "note",
        "ruleType",
        "repeatIntervalDays",
        "dayOfWeek",
        "dayMode",
        "isActive",
        "approved",
        "denied",
        "active",
        "paid",
        "canceled",
        "includeInTimeTracking",
        "workedMinutes",
        "breakMinutes",
        "needsCorrection",
        "trackingBalance",
        "weeklyBalance",
        "startDate",
        "endDate",
        "requestDate",
        "desiredTimestamp",
        "date",
        "periodStart",
        "periodEnd",
        "grossSalary",
        "deductions",
        "netSalary",
        "currency",
        "payoutDate",
        "approved",
        "locked",
        "version",
        "overtimeHours",
        "payoutOvertime",
        "punchType",
        "desiredPunchType",
        "quantity",
        "quantityOnHand",
        "availableQuantity",
        "amount",
        "currency",
        "budgetMinutes",
        "hourlyRate",
        "monthlySalary",
        "annualVacationDays",
        "expectedWorkDays",
        "isHourly",
        "isPercentage",
        "workPercentage",
        "dailyWorkHours",
        "iban",
        "bic",
        "clearingNumber",
        "bankAccount",
        "orderNumber",
        "sku",
        "code",
        "type",
        "source",
        "stage",
        "probability",
        "expectedCloseDate",
        "channel",
        "budget",
        "reason",
        "comment",
        "adminComment",
    )
    val orderedKeys = (preferredKeys + keysList())
        .distinct()
        .filter { has(it) && !isNull(it) && it !in setOf("primaryTimes", "entries") }
    rows += orderedKeys.mapNotNull { key ->
        val rendered = opt(key).renderDetailValue().takeIf { it.isNotBlank() } ?: return@mapNotNull null
        ModuleItemDetail(key.toDetailLabel(), rendered.take(160))
    }
    return rows.distinctBy { it.label to it.value }.take(28)
}

private fun Any?.renderDetailValue(): String =
    when (this) {
        null, JSONObject.NULL -> ""
        is Boolean -> if (this) "Ja" else "Nein"
        is JSONArray -> "${length()} Einträge"
        is JSONObject -> firstString(
            "name",
            "title",
            "username",
            "customerName",
            "projectName",
            "taskName",
            "companyName",
            "status",
            "state",
        ) ?: "${length()} Felder"
        else -> toString()
    }

private fun JSONObject.renderPunchEntry(): String {
    val time = optNullableString("entryTimestamp")?.let { timestamp ->
        timestamp.substringAfter('T', timestamp).take(5)
    }.orEmpty()
    val type = firstString("punchType", "type").orEmpty()
    val assignment = listOfNotNull(
        optNullableString("customerName"),
        optNullableString("projectName"),
        optNullableString("taskName"),
    ).joinToString(" / ")
    return listOf(time, type, assignment).filter { it.isNotBlank() }.joinToString(" - ")
}

private fun JSONArray.toAdminEditEntriesJson(): String {
    val entries = (0 until length()).mapNotNull { index ->
        optJSONObject(index)?.toAdminEditEntryJson()
    }
    return if (entries.isEmpty()) "[]" else entries.joinToString(separator = ",\n", prefix = "[\n", postfix = "\n]") { "  $it" }
}

private fun JSONObject.toAdminEditEntryJson(): String {
    val entry = JSONObject()
    listOf(
        "id",
        "entryTimestamp",
        "punchType",
        "source",
        "customerId",
        "projectId",
        "taskId",
        "durationMinutes",
        "description",
        "approved",
        "systemGeneratedNote",
    ).forEach { key ->
        if (has(key) && !isNull(key)) entry.put(key, opt(key))
    }
    entry.put("correctedByUser", true)
    return entry.toString()
}

private fun String.renderBooleanText(): String =
    if (equals("true", ignoreCase = true) || equals("ja", ignoreCase = true)) "Ja" else "Nein"

private fun String.toDetailLabel(): String =
    when (this) {
        "id" -> "ID"
        "createdAt" -> "Erstellt"
        "message" -> "Feedback"
        "displayName" -> "Name"
        "username" -> "Benutzer"
        "appMenuTitle" -> "App-Menü"
        "appMenuGroup" -> "Bereich"
        "appVersionName" -> "App-Version"
        "appVersionCode" -> "Version Code"
        "deviceInfo" -> "Gerät"
        "firstName" -> "Vorname"
        "lastName" -> "Nachname"
        "email" -> "E-Mail"
        "mobilePhone" -> "Telefon"
        "personnelNumber" -> "Personalnummer"
        "department" -> "Abteilung"
        "userId" -> "Benutzer-ID"
        "roleName" -> "Rolle"
        "country" -> "Land"
        "taxClass" -> "Steuerklasse"
        "tarifCode" -> "Tarifcode"
        "canton" -> "Kanton"
        "name" -> "Name"
        "title" -> "Titel"
        "addressLine1" -> "Adresse"
        "postalCode" -> "PLZ"
        "city" -> "Ort"
        "userCount" -> "Benutzer"
        "companyName" -> "Firma"
        "customerName" -> "Kunde"
        "projectName" -> "Projekt"
        "taskName" -> "Aufgabe"
        "status", "state", "workflowStatus", "approvalStatus" -> "Status"
        "shiftKey" -> "Schicht-Key"
        "shift" -> "Schicht"
        "label" -> "Bezeichnung"
        "startTime" -> "Startzeit"
        "endTime" -> "Endzeit"
        "note" -> "Notiz"
        "ruleType" -> "Regeltyp"
        "repeatIntervalDays" -> "Intervall Tage"
        "dayOfWeek" -> "Wochentag"
        "dayMode" -> "Tagesmodus"
        "isActive" -> "Aktiv"
        "approved" -> "Genehmigt"
        "denied" -> "Abgelehnt"
        "active" -> "Aktiv"
        "paid" -> "Bezahlt"
        "canceled" -> "Gekündigt"
        "includeInTimeTracking" -> "Zeitübersicht"
        "workedMinutes" -> "Ist Minuten"
        "breakMinutes" -> "Pause Minuten"
        "needsCorrection" -> "Korrektur nötig"
        "trackingBalance" -> "Saldo Minuten"
        "weeklyBalance" -> "Wochensaldo Minuten"
        "color" -> "Farbe"
        "startDate" -> "Start"
        "endDate" -> "Ende"
        "requestDate" -> "Antrag"
        "desiredTimestamp" -> "Zeitpunkt"
        "date" -> "Datum"
        "periodStart" -> "Periode Start"
        "periodEnd" -> "Periode Ende"
        "grossSalary" -> "Bruttolohn"
        "deductions" -> "Abzüge"
        "netSalary" -> "Nettolohn"
        "allowances" -> "Zulagen"
        "bonuses" -> "Bonus"
        "oneTimePayments" -> "Einmalzahlungen"
        "taxFreeAllowances" -> "Steuerfreie Zulagen"
        "earnings" -> "Verdienstbestandteile"
        "deductionsList" -> "Abzugsliste"
        "employerContribList" -> "Arbeitgeberbeiträge"
        "employerContributions" -> "Arbeitgeberanteil"
        "payoutDate" -> "Auszahlung"
        "socialSecurityNumber" -> "Sozialversicherung"
        "payType" -> "Lohnart"
        "locked" -> "Gesperrt"
        "pdfPath" -> "PDF"
        "version" -> "Version"
        "overtimeHours" -> "Überstunden"
        "payoutOvertime" -> "Überzeit auszahlen"
        "punchType", "desiredPunchType" -> "Stempel"
        "quantity" -> "Menge"
        "quantityOnHand" -> "Bestand"
        "availableQuantity" -> "Verfügbar"
        "amount" -> "Betrag"
        "currency" -> "Währung"
        "budgetMinutes" -> "Budget Minuten"
        "hourlyRate" -> "Stundensatz"
        "monthlySalary" -> "Monatslohn"
        "annualVacationDays" -> "Urlaubstage"
        "expectedWorkDays" -> "Arbeitstage"
        "isHourly" -> "Stundenlohn"
        "isPercentage" -> "Prozentmodell"
        "workPercentage" -> "Pensum"
        "dailyWorkHours" -> "Sollstunden"
        "iban" -> "IBAN"
        "bic" -> "BIC"
        "clearingNumber" -> "Clearing"
        "bankAccount" -> "Bankkonto"
        "orderNumber" -> "Auftragsnummer"
        "sku" -> "SKU"
        "code" -> "Code"
        "type" -> "Typ"
        "source" -> "Quelle"
        "stage" -> "Phase"
        "probability" -> "Wahrscheinlichkeit"
        "expectedCloseDate" -> "Abschluss"
        "channel" -> "Kanal"
        "budget" -> "Budget"
        "reason" -> "Grund"
        "comment", "adminComment" -> "Kommentar"
        else -> replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() }
    }
