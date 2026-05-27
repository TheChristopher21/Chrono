package ch.chronologisch.chrono

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AssignmentTurnedIn
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Workspaces
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.chronologisch.chrono.data.AuthenticatedSession
import ch.chronologisch.chrono.data.CustomerOption
import ch.chronologisch.chrono.data.DailyTimeSummary
import ch.chronologisch.chrono.data.ModuleAction
import ch.chronologisch.chrono.data.ModuleActionField
import ch.chronologisch.chrono.data.ModuleActionFieldType
import ch.chronologisch.chrono.data.ModuleActionMethod
import ch.chronologisch.chrono.data.ModuleEndpointResult
import ch.chronologisch.chrono.data.ModuleItemDetail
import ch.chronologisch.chrono.data.ModuleListItem
import ch.chronologisch.chrono.data.ModuleRequestStatus
import ch.chronologisch.chrono.data.ModuleSummary
import ch.chronologisch.chrono.data.ProjectOption
import ch.chronologisch.chrono.data.PunchType
import ch.chronologisch.chrono.data.TaskOption
import ch.chronologisch.chrono.data.TimeTrackingEntry
import ch.chronologisch.chrono.data.UserProfile
import ch.chronologisch.chrono.ui.dashboard.AppSection
import ch.chronologisch.chrono.ui.dashboard.DashboardUiState
import ch.chronologisch.chrono.ui.dashboard.DashboardViewModel
import ch.chronologisch.chrono.ui.login.LoginUiState
import ch.chronologisch.chrono.ui.login.LoginViewModel
import ch.chronologisch.chrono.ui.theme.ChronoTheme
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    private val loginViewModel: LoginViewModel by viewModels { LoginViewModel.factory(applicationContext) }
    private val dashboardViewModel: DashboardViewModel by viewModels { DashboardViewModel.factory(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val loginState = loginViewModel.uiState
            val dashboardState = dashboardViewModel.uiState

            LaunchedEffect(loginState.session?.token) {
                loginState.session?.let(dashboardViewModel::bindSession) ?: dashboardViewModel.clear()
            }

            ChronoTheme {
                ChronoApp(
                    loginState = loginState,
                    dashboardState = dashboardState,
                    onUsernameChange = loginViewModel::updateUsername,
                    onPasswordChange = loginViewModel::updatePassword,
                    onLogin = loginViewModel::login,
                    onDemoLogin = loginViewModel::demoLogin,
                    onLogout = loginViewModel::logout,
                    onSelectSection = dashboardViewModel::selectSection,
                    onSubmitModuleAction = dashboardViewModel::submitModuleAction,
                    onSubmitFeedback = dashboardViewModel::submitAppFeedback,
                    onPunch = dashboardViewModel::punch,
                    onRefresh = dashboardViewModel::refresh,
                    onSelectCustomer = dashboardViewModel::selectCustomer,
                    onSelectProject = dashboardViewModel::selectProject,
                    onSelectTask = dashboardViewModel::selectTask,
                    onNoteChange = dashboardViewModel::updateNoteDraft,
                    onSaveNote = dashboardViewModel::saveDailyNote,
                )
            }
        }
    }
}

@Composable
private fun ChronoApp(
    loginState: LoginUiState,
    dashboardState: DashboardUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onDemoLogin: () -> Unit,
    onLogout: () -> Unit,
    onSelectSection: (AppSection) -> Unit,
    onSubmitModuleAction: (ModuleAction, Map<String, String>) -> Unit,
    onSubmitFeedback: (AppSection, String) -> Unit,
    onPunch: () -> Unit,
    onRefresh: () -> Unit,
    onSelectCustomer: (Long?) -> Unit,
    onSelectProject: (Long?) -> Unit,
    onSelectTask: (Long?) -> Unit,
    onNoteChange: (String) -> Unit,
    onSaveNote: () -> Unit,
) {
    when {
        loginState.isRestoringSession -> LoadingScreen()
        loginState.session != null -> DashboardScreen(
            session = loginState.session,
            state = dashboardState,
            onLogout = onLogout,
            onSelectSection = onSelectSection,
            onSubmitModuleAction = onSubmitModuleAction,
            onSubmitFeedback = onSubmitFeedback,
            onPunch = onPunch,
            onRefresh = onRefresh,
            onSelectCustomer = onSelectCustomer,
            onSelectProject = onSelectProject,
            onSelectTask = onSelectTask,
            onNoteChange = onNoteChange,
            onSaveNote = onSaveNote,
        )
        else -> LoginScreen(loginState, onUsernameChange, onPasswordChange, onLogin, onDemoLogin)
    }
}

@Composable
private fun LoadingScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(14.dp))
            Text("Chrono wird vorbereitet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LoginScreen(
    state: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onDemoLogin: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { AppHeader("Login") }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeroCard("Willkommen bei Chrono", "Einloggen, dann zeigt dir die App zuerst Heute, Aufgaben und deine freigegebenen Bereiche.")
            FeatureListCard("Nach dem Login", listOf("Heute stempeln", "Absenzen sehen", "Lohn & Berichte", "Admin nur bei Zugriff"))
            CardBox {
                SectionTitle("Anmeldung")
                OutlinedTextField(
                    value = state.username,
                    onValueChange = onUsernameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Benutzername") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                )
                PasswordField(state.password, !state.isLoading, focusManager, onPasswordChange, onLogin)
                state.errorMessage?.let { MessageCard(it, true) }
                Button(onClick = onLogin, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.isLoading) "Anmelden..." else "Anmelden")
                }
                if (BuildConfig.DEMO_LOGIN_ENABLED) {
                    OutlinedButton(onClick = onDemoLogin, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
                        Text("Demo starten")
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordField(
    password: String,
    enabled: Boolean,
    focusManager: FocusManager,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
) {
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Passwort") },
        singleLine = true,
        enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); onLogin() }),
    )
}

@Composable
private fun DashboardScreen(
    session: AuthenticatedSession,
    state: DashboardUiState,
    onLogout: () -> Unit,
    onSelectSection: (AppSection) -> Unit,
    onSubmitModuleAction: (ModuleAction, Map<String, String>) -> Unit,
    onSubmitFeedback: (AppSection, String) -> Unit,
    onPunch: () -> Unit,
    onRefresh: () -> Unit,
    onSelectCustomer: (Long?) -> Unit,
    onSelectProject: (Long?) -> Unit,
    onSelectTask: (Long?) -> Unit,
    onNoteChange: (String) -> Unit,
    onSaveNote: () -> Unit,
) {
    val today = LocalDate.now()
    val todaySummary = state.history.firstOrNull { it.date == today }
    val weekStart = startOfWeek(today)
    val weekSummaries = (0..6).map { offset ->
        val date = weekStart.plusDays(offset.toLong())
        date to state.history.firstOrNull { it.date == date }
    }
    val sections = AppSection.visibleFor(session.user)
    val selectedSection = state.selectedSection.takeIf { it in sections } ?: AppSection.TIME
    val showActionsFirst = selectedSection.prefersActionsFirst()
    var feedbackDialogOpen by remember { mutableStateOf(false) }

    if (feedbackDialogOpen) {
        AppFeedbackDialog(
            section = selectedSection,
            isSubmitting = state.isSubmittingFeedback,
            onDismiss = { feedbackDialogOpen = false },
            onSubmit = { message ->
                onSubmitFeedback(selectedSection, message)
                feedbackDialogOpen = false
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppHeader(selectedSection.title) },
        bottomBar = {
            PrimaryNavigationBar(
                sections = sections,
                selectedSection = selectedSection,
                onSelectSection = onSelectSection,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DashboardToolbar(session.user, selectedSection, state.isRefreshing, onRefresh, onLogout) { feedbackDialogOpen = true }
            state.errorMessage?.let { MessageCard(it, true) }
            state.infoMessage?.let { MessageCard(it, false) }
            ModuleNavigation(sections, selectedSection, onSelectSection)
            if (showActionsFirst && selectedSection != AppSection.ADMIN_HOME) {
                ModuleActionsCard(
                    summary = state.moduleSummaries[selectedSection],
                    isSubmitting = state.isSubmittingModuleAction,
                    onSubmit = onSubmitModuleAction,
                )
            }

            when (selectedSection) {
                AppSection.TIME -> TimeScreen(
                    user = session.user,
                    state = state,
                    todaySummary = todaySummary,
                    weekSummaries = weekSummaries,
                    onPunch = onPunch,
                    onSelectCustomer = onSelectCustomer,
                    onSelectProject = onSelectProject,
                    onSelectTask = onSelectTask,
                    onNoteChange = onNoteChange,
                    onSaveNote = onSaveNote,
                )
                AppSection.WORK_MODEL -> WorkModelScreen(session.user, weekSummaries, todaySummary, state.now, state.moduleSummaries[selectedSection])
                AppSection.ABSENCES -> AbsenceScreen(session.user, state.moduleSummaries[selectedSection])
                AppSection.PAYSLIPS -> PayslipScreen(session, state.moduleSummaries[selectedSection])
                AppSection.REPORTS -> ReportsScreen(session, state.moduleSummaries[selectedSection])
                AppSection.PROFILE -> ProfileScreen(session.user, state.moduleSummaries[selectedSection])
                AppSection.SUPPLY_CHAIN -> SupplyChainScreen(state.moduleSummaries[selectedSection])
                AppSection.INFO -> InfoScreen(state.moduleSummaries[selectedSection])
                AppSection.ADMIN_HOME -> AdminHomeScreen(
                    user = session.user,
                    summary = state.moduleSummaries[selectedSection],
                    isSubmitting = state.isSubmittingModuleAction,
                    onSubmitModuleAction = onSubmitModuleAction,
                    onSelectSection = onSelectSection,
                )
                AppSection.SUPERADMIN_HOME -> SuperAdminScreen(state.moduleSummaries[selectedSection], onSelectSection)
                AppSection.EMPLOYEES -> EmployeesScreen(state.moduleSummaries[selectedSection])
                AppSection.USERS -> UsersScreen(state.moduleSummaries[selectedSection])
                AppSection.ADMIN_PASSWORD -> AdminPasswordScreen(state.moduleSummaries[selectedSection])
                AppSection.CUSTOMERS_PROJECTS -> CustomersProjectsScreen(session.user, state.moduleSummaries[selectedSection])
                AppSection.CUSTOMERS -> CustomersScreen(state.moduleSummaries[selectedSection])
                AppSection.PROJECTS -> ProjectsScreen(state.moduleSummaries[selectedSection])
                AppSection.TASKS -> TasksScreen(state.moduleSummaries[selectedSection])
                AppSection.PROJECT_REPORT -> ProjectReportScreen(state.moduleSummaries[selectedSection])
                AppSection.ANALYTICS -> AnalyticsScreen(state.moduleSummaries[selectedSection])
                AppSection.TIME_IMPORT -> TimeImportScreen(state.moduleSummaries[selectedSection])
                AppSection.SCHEDULE -> ScheduleScreen(state.moduleSummaries[selectedSection])
                AppSection.PRINT_SCHEDULE -> PrintScheduleScreen(state.moduleSummaries[selectedSection])
                AppSection.SHIFT_RULES -> ShiftRulesScreen(state.moduleSummaries[selectedSection])
                AppSection.PAYROLL_ADMIN -> PayrollAdminScreen(session, state.moduleSummaries[selectedSection])
                AppSection.ACCOUNTING -> AccountingScreen(state.moduleSummaries[selectedSection])
                AppSection.CHRONO_TWO -> ChronoTwoScreen(state.moduleSummaries[selectedSection])
                AppSection.CRM -> CrmScreen(state.moduleSummaries[selectedSection])
                AppSection.BANKING -> BankingScreen(session, state.moduleSummaries[selectedSection])
                AppSection.KNOWLEDGE -> KnowledgeScreen(state.moduleSummaries[selectedSection])
                AppSection.COMPANY_SETTINGS -> CompanySettingsScreen(state.moduleSummaries[selectedSection])
                AppSection.COMPANY -> CompanyManagementScreen(session, state.moduleSummaries[selectedSection])
            }

            if (!showActionsFirst && selectedSection != AppSection.ADMIN_HOME) {
                ModuleActionsCard(
                    summary = state.moduleSummaries[selectedSection],
                    isSubmitting = state.isSubmittingModuleAction,
                    onSubmit = onSubmitModuleAction,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AppFeedbackDialog(
    section: AppSection,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var message by remember(section) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Feedback zur App") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Bereich: ${section.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Was fehlt oder soll besser werden?") },
                    minLines = 4,
                    maxLines = 7,
                    enabled = !isSubmitting,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(message.trim()) },
                enabled = !isSubmitting && message.trim().length >= 3,
            ) {
                Text(if (isSubmitting) "Senden..." else "Senden")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Abbrechen")
            }
        },
    )
}

@Composable
private fun PrimaryNavigationBar(
    sections: List<AppSection>,
    selectedSection: AppSection,
    onSelectSection: (AppSection) -> Unit,
) {
    val primarySections = when {
        AppSection.SUPERADMIN_HOME in sections -> listOfNotNull(
            AppSection.TIME.takeIf { it in sections },
            AppSection.SUPERADMIN_HOME,
            AppSection.COMPANY.takeIf { it in sections },
            AppSection.ADMIN_HOME.takeIf { it in sections },
            AppSection.PROFILE.takeIf { it in sections },
        )
        AppSection.ADMIN_HOME in sections -> listOfNotNull(
            AppSection.TIME.takeIf { it in sections },
            AppSection.ADMIN_HOME,
            AppSection.EMPLOYEES.takeIf { it in sections },
            AppSection.SCHEDULE.takeIf { it in sections },
            AppSection.PROFILE.takeIf { it in sections },
        )
        else -> listOfNotNull(
            AppSection.TIME.takeIf { it in sections },
            AppSection.ABSENCES.takeIf { it in sections },
            AppSection.PAYSLIPS.takeIf { it in sections },
            AppSection.PROFILE.takeIf { it in sections },
            AppSection.ADMIN_HOME.takeIf { it in sections },
        )
    }
    if (primarySections.isEmpty()) return

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        primarySections.forEach { section ->
            NavigationBarItem(
                selected = selectedSection == section,
                onClick = { onSelectSection(section) },
                icon = { Icon(section.navIcon(), contentDescription = null) },
                label = { Text(section.navLabel(), maxLines = 1, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

private fun AppSection.navIcon(): ImageVector = when (this) {
    AppSection.TIME -> Icons.Rounded.Home
    AppSection.WORK_MODEL -> Icons.Rounded.Tune
    AppSection.ABSENCES -> Icons.Rounded.BeachAccess
    AppSection.PAYSLIPS, AppSection.PAYROLL_ADMIN -> Icons.Rounded.Payments
    AppSection.REPORTS, AppSection.ANALYTICS, AppSection.PROJECT_REPORT -> Icons.AutoMirrored.Rounded.ReceiptLong
    AppSection.PROFILE -> Icons.Rounded.Person
    AppSection.SUPPLY_CHAIN, AppSection.CHRONO_TWO -> Icons.Rounded.Inventory2
    AppSection.INFO -> Icons.AutoMirrored.Rounded.HelpOutline
    AppSection.ADMIN_HOME, AppSection.SUPERADMIN_HOME -> Icons.Rounded.Dashboard
    AppSection.EMPLOYEES, AppSection.USERS -> Icons.Rounded.Groups
    AppSection.CUSTOMERS_PROJECTS, AppSection.CUSTOMERS, AppSection.PROJECTS, AppSection.TASKS -> Icons.Rounded.Workspaces
    AppSection.SCHEDULE, AppSection.PRINT_SCHEDULE, AppSection.SHIFT_RULES -> Icons.Rounded.CalendarMonth
    AppSection.ACCOUNTING, AppSection.BANKING -> Icons.Rounded.AccountBalance
    AppSection.CRM -> Icons.Rounded.Business
    AppSection.KNOWLEDGE -> Icons.Rounded.AssignmentTurnedIn
    AppSection.COMPANY_SETTINGS, AppSection.COMPANY -> Icons.Rounded.Settings
    else -> Icons.Rounded.AdminPanelSettings
}

private fun AppSection.navLabel(): String = when (this) {
    AppSection.TIME -> "Heute"
    AppSection.ABSENCES -> "Absenzen"
    AppSection.PAYSLIPS -> "Lohn"
    AppSection.ADMIN_HOME -> "Admin"
    AppSection.SUPERADMIN_HOME -> "Super"
    AppSection.COMPANY -> "Firmen"
    AppSection.EMPLOYEES -> "Team"
    AppSection.PAYROLL_ADMIN -> "Payroll"
    AppSection.SCHEDULE -> "Plan"
    else -> title
}

private fun AppSection.adminHomeVisible(): Boolean =
    this != AppSection.TIME &&
        this != AppSection.WORK_MODEL &&
        this != AppSection.ABSENCES &&
        this != AppSection.PAYSLIPS &&
        this != AppSection.REPORTS &&
        this != AppSection.PROFILE &&
        this != AppSection.SUPPLY_CHAIN &&
        this != AppSection.INFO

private fun AppSection.prefersActionsFirst(): Boolean =
    this in setOf(
        AppSection.ABSENCES,
        AppSection.SUPPLY_CHAIN,
        AppSection.EMPLOYEES,
        AppSection.USERS,
        AppSection.ADMIN_PASSWORD,
        AppSection.CUSTOMERS_PROJECTS,
        AppSection.CUSTOMERS,
        AppSection.PROJECTS,
        AppSection.TASKS,
        AppSection.TIME_IMPORT,
        AppSection.SCHEDULE,
        AppSection.SHIFT_RULES,
        AppSection.PAYROLL_ADMIN,
        AppSection.ACCOUNTING,
        AppSection.CHRONO_TWO,
        AppSection.CRM,
        AppSection.BANKING,
        AppSection.KNOWLEDGE,
        AppSection.COMPANY_SETTINGS,
        AppSection.COMPANY,
    )

@Composable
private fun TimeScreen(
    user: UserProfile,
    state: DashboardUiState,
    todaySummary: DailyTimeSummary?,
    weekSummaries: List<Pair<LocalDate, DailyTimeSummary?>>,
    onPunch: () -> Unit,
    onSelectCustomer: (Long?) -> Unit,
    onSelectProject: (Long?) -> Unit,
    onSelectTask: (Long?) -> Unit,
    onNoteChange: (String) -> Unit,
    onSaveNote: () -> Unit,
) {
    if (state.isLoading && state.history.isEmpty()) {
        LoadingDataCard()
        return
    }
    TodayTrackingCard(user, todaySummary, state.now, state.isPunching, onPunch)
    TimeQuickStats(user, todaySummary, state.now)
    TrackingSelectionCard(user, state, onSelectCustomer, onSelectProject, onSelectTask)
    WeekOverview(weekSummaries, state.now)
    TodayEntries(todaySummary)
    DailyNoteCard(state.noteDraft, state.isSavingNote, onNoteChange, onSaveNote)
    DataSourceStatusCard(state.moduleSummaries[state.selectedSection])
}

@Composable
private fun DashboardToolbar(
    user: UserProfile,
    selectedSection: AppSection,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onFeedback: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                    Text(
                        userInitials(user),
                        Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(user.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(selectedSection.group, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRefresh, enabled = !isRefreshing, modifier = Modifier.size(40.dp)) {
                    if (isRefreshing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Aktualisieren")
                    }
                }
                IconButton(onClick = onFeedback, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = "Feedback")
                }
                IconButton(onClick = onLogout, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Abmelden")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactFact("Bereich", selectedSection.title, Modifier.weight(1f))
                CompactFact("Modell", userModelLabel(user), Modifier.weight(1f))
                CompactFact("Saldo", formatSignedMinutes(user.trackingBalanceInMinutes), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModuleNavigation(sections: List<AppSection>, selectedSection: AppSection, onSelectSection: (AppSection) -> Unit) {
    val groupedSections = sections.groupBy { it.group }
    val activeGroup = selectedSection.group
    val activeSections = groupedSections[activeGroup].orEmpty()
    val otherGroups = groupedSections.filterKeys { it != activeGroup }
    val navKey = sections.joinToString("|") { it.name }
    var expanded by remember(navKey, activeGroup) { mutableStateOf(false) }
    val visibleActiveSections = if (expanded) {
        activeSections
    } else {
        activeSections.withSelectedFirst(selectedSection).take(6)
    }

    CardBox {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionTitle("Bereiche")
                Text(
                    "Aktuell: ${selectedSection.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(999.dp)) {
                Text(
                    "${sections.size}",
                    Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        SectionGroupButtons(activeGroup, visibleActiveSections, selectedSection, onSelectSection)
        if (!expanded && activeSections.size > visibleActiveSections.size) {
            Text(
                "${activeSections.size - visibleActiveSections.size} weitere in $activeGroup",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            otherGroups.forEach { (group, groupSections) ->
                SectionGroupButtons(group, groupSections, selectedSection, onSelectSection)
            }
        }
        if (activeSections.size > visibleActiveSections.size || otherGroups.isNotEmpty()) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Text(if (expanded) "Weniger Bereiche anzeigen" else "Alle Bereiche anzeigen")
            }
        }
    }
}

private fun List<AppSection>.withSelectedFirst(selectedSection: AppSection): List<AppSection> =
    sortedWith(compareBy<AppSection> { it != selectedSection }.thenBy { it.ordinal })

@Composable
private fun SectionGroupButtons(
    group: String,
    sections: List<AppSection>,
    selectedSection: AppSection,
    onSelectSection: (AppSection) -> Unit,
) {
    if (sections.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(group, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        sections.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { section ->
                    SectionSelectButton(
                        section = section,
                        selected = section == selectedSection,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectSection(section) },
                    )
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SectionSelectButton(
    section: AppSection,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(section.navIcon(), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(section.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = 44.dp),
            contentPadding = ButtonDefaults.ContentPadding,
        ) { content() }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 44.dp),
            contentPadding = ButtonDefaults.ContentPadding,
        ) { content() }
    }
}

@Composable
private fun WorkModelScreen(
    user: UserProfile,
    weekSummaries: List<Pair<LocalDate, DailyTimeSummary?>>,
    todaySummary: DailyTimeSummary?,
    now: LocalDateTime,
    summary: ModuleSummary?,
) {
    val workedToday = displayWorkedMinutes(todaySummary, now)
    val workedWeek = weekSummaries.sumOf { (date, day) ->
        displayWorkedMinutes(day, if (date == now.toLocalDate()) now else LocalDateTime.of(date, LocalTime.MAX))
    }
    HeroCard("Pensum & Stundenlohn", "Arbeitsmodell fuer ${user.displayName}")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Pensum", "${user.workPercentage}%", Modifier.weight(1f))
        MetricCard("Stundenlohn", if (user.isHourly) "Aktiv" else "Nein", Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Heute", formatMinutes(workedToday), Modifier.weight(1f))
        MetricCard("Woche", formatMinutes(workedWeek), Modifier.weight(1f))
    }
    DetailRowsCard("Arbeitsmodell", listOf("Modell" to userModelLabel(user), "Saldo" to formatSignedMinutes(user.trackingBalanceInMinutes)))
    DataSourceStatusCard(summary)
}

@Composable
private fun ProfileScreen(user: UserProfile, summary: ModuleSummary?) {
    HeroCard("Profil", "Konto, Rollen und persoenliche Arbeitsdaten.")
    DetailRowsCard("Stammdaten", listOf("Name" to user.displayName, "Benutzername" to user.username, "Rollen" to user.roles.joinToString(", ").ifBlank { "-" }))
    DetailRowsCard("Arbeitszeit", listOf("Tages-Soll" to (user.dailyWorkHours?.let { "${it.formatHours()} h" } ?: "-"), "Pensum" to "${user.workPercentage}%", "Stundenlohn" to if (user.isHourly) "Ja" else "Nein"))
    DataSourceStatusCard(summary)
}

@Composable
private fun AbsenceScreen(user: UserProfile, summary: ModuleSummary?) {
    val vacation = endpoint(summary, "Urlaub")
    val sickLeave = endpoint(summary, "Krankheit")
    val corrections = endpoint(summary, "Korrekturen")
    HeroCard("Abwesenheit", "Urlaub, Krankheit und Korrekturen fuer ${user.displayName}.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Urlaub", endpointCount(vacation), Modifier.weight(1f))
        MetricCard("Korrekturen", endpointCount(corrections), Modifier.weight(1f))
    }
    CalendarStripCard("Abwesenheitskalender", listOfNotNull(vacation, sickLeave), "Keine geplanten Abwesenheiten.")
    NativeListCard("Urlaub", vacation, "Keine Urlaubsantraege.")
    NativeListCard("Krankheit", sickLeave, "Keine Krankmeldungen.")
    NativeListCard("Korrekturen", corrections, "Keine Korrekturantraege.")
}

@Composable
private fun PayslipScreen(session: AuthenticatedSession, summary: ModuleSummary?) {
    val user = session.user
    val payslips = endpoint(summary, "Lohnabrechnungen")
    HeroCard("Lohn", "Lohnabrechnungen und Archiv fuer ${user.displayName}.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Payslips", endpointCount(payslips), Modifier.weight(1f))
        MetricCard("Modell", userModelLabel(user), Modifier.weight(1f))
    }
    DownloadListCard(
        title = "PDF herunterladen",
        endpoint = payslips,
        emptyText = "Keine PDFs verfuegbar.",
        session = session,
        pathFor = { item -> "/api/payslips/pdf/${item.id}?lang=de" },
        fileNameFor = { item -> "payslip-${item.id}.pdf" },
    )
    NativeListCard("Meine Lohnabrechnungen", payslips, "Noch keine Lohnabrechnungen.")
}

@Composable
private fun ReportsScreen(session: AuthenticatedSession, summary: ModuleSummary?) {
    val user = session.user
    val today = LocalDate.now()
    val monthStart = today.withDayOfMonth(1)
    val username = user.username.urlEncoded()
    val month = endpoint(summary, "Monatsreport")
    val difference = endpoint(summary, "Differenz")
    val week = endpoint(summary, "Woche")
    HeroCard("Zeitbericht", "Monatsbericht, Woche und Arbeitszeit-Saldo fuer ${user.displayName}.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Saldo", formatSignedMinutes(user.trackingBalanceInMinutes), Modifier.weight(1f))
        MetricCard("Pensum", "${user.workPercentage}%", Modifier.weight(1f))
    }
    DownloadsCard(
        title = "Monat exportieren",
        session = session,
        downloads = listOf(
            DownloadSpec("PDF", "/api/report/timesheet/pdf?username=$username&startDate=$monthStart&endDate=$today", "timesheet-$monthStart-$today.pdf"),
            DownloadSpec("CSV", "/api/report/timesheet/csv?username=$username&startDate=$monthStart&endDate=$today", "timesheet-$monthStart-$today.csv"),
            DownloadSpec("Kalender", "/api/report/timesheet/ics?username=$username&startDate=$monthStart&endDate=$today", "timesheet-$monthStart-$today.ics"),
        ),
    )
    NativeListCard("Monatsreport", month, "Keine Monatsdaten.")
    NativeListCard("Woche", week, "Keine Wochendaten.")
    NativeListCard("Differenz", difference, "Keine Differenzdaten.")
}

@Composable
private fun SupplyChainScreen(summary: ModuleSummary?) {
    val products = endpoint(summary, "Artikel")
    val warehouses = endpoint(summary, "Lager")
    val stock = endpoint(summary, "Bestand")
    val purchases = endpoint(summary, "Einkauf")
    val sales = endpoint(summary, "Verkauf")
    val production = endpoint(summary, "Produktion")
    val services = endpoint(summary, "Services")
    val counts = endpoint(summary, "Inventur")
    val movements = endpoint(summary, "Bewegungen")
    val audit = endpoint(summary, "Audit")
    HeroCard("Supply Chain", "Artikel, Lager, Einkauf, Verkauf, Produktion und Service.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Artikel", endpointCount(products), Modifier.weight(1f))
        MetricCard("Bestand", endpointCount(stock), Modifier.weight(1f))
    }
    NativeListCard("Artikel", products, "Keine Artikel.")
    NativeListCard("Lager", warehouses, "Keine Lager.")
    NativeListCard("Bestand", stock, "Kein Bestand.")
    NativeListCard("Bewegungen", movements, "Keine Lagerbewegungen.")
    NativeListCard("Einkauf", purchases, "Keine Einkaufsauftraege.")
    NativeListCard("Verkauf", sales, "Keine Verkaufsauftraege.")
    NativeListCard("Produktion", production, "Keine Produktionsauftraege.")
    NativeListCard("Service", services, "Keine Servicefaelle.")
    NativeListCard("Inventur", counts, "Keine Inventuren.")
    NativeListCard("Audit", audit, "Keine Audit-Eintraege.")
}

@Composable
private fun InfoScreen(summary: ModuleSummary?) {
    val latest = endpoint(summary, "Letzte Aenderung")
    val changes = endpoint(summary, "Aenderungen")
    val chat = endpoint(summary, "Chat Status")
    HeroCard("Info", "Neuigkeiten, Demo-Status und Systeminformationen.")
    DetailRowsCard(
        "Rechtliches",
        listOf(
            "Datenschutz" to "https://chrono-logisch.ch/datenschutz",
            "Impressum" to "https://chrono-logisch.ch/impressum",
            "Kontakt" to "info@chrono-logisch.ch",
        ),
    )
    NativeListCard("Letzte Aenderung", latest, "Keine aktuelle Aenderung.")
    NativeListCard("Aenderungen", changes, "Keine Aenderungen.")
    NativeListCard("Chat", chat, "Kein Chat-Status.")
}

@Composable
private fun AdminHomeScreen(
    user: UserProfile,
    summary: ModuleSummary?,
    isSubmitting: Boolean,
    onSubmitModuleAction: (ModuleAction, Map<String, String>) -> Unit,
    onSelectSection: (AppSection) -> Unit,
) {
    val adminSections = AppSection.visibleFor(user).filter { it.adminHomeVisible() }
    val time = endpoint(summary, "Zeituebersicht")
    val employees = endpoint(summary, "Mitarbeiter")
    val balances = endpoint(summary, "Zeitkonten")
    val weekly = endpoint(summary, "Wochensaldo")
    val corrections = endpoint(summary, "Korrekturen")
    val vacation = endpoint(summary, "Urlaub")
    val sick = endpoint(summary, "Krankheit")
    val payroll = endpoint(summary, "Payroll offen")
    var activeTab by remember { mutableStateOf("overview") }
    val pendingRequests = pendingItemCount(corrections) + pendingItemCount(vacation)
    val tabs = listOf(
        DashboardTab("overview", "Uebersicht", pendingRequests),
        DashboardTab("time", "Teamzeit", endpointNumericCount(time)),
        DashboardTab("requests", "Antraege", pendingRequests),
        DashboardTab("calendar", "Kalender", endpointNumericCount(vacation) + endpointNumericCount(sick)),
        DashboardTab("modules", "Module", adminSections.count { it != AppSection.ADMIN_HOME }),
    )

    HeroCard("Admin Dashboard", "Team-Cockpit fuer offene Antraege, Zeitpruefung, Abwesenheiten und Module.")
    DashboardTabBar(tabs, activeTab) { activeTab = it }
    when (activeTab) {
        "time" -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Mitarbeiter", endpointCount(employees), Modifier.weight(1f))
                MetricCard("Zeitkonten", endpointCount(balances), Modifier.weight(1f))
            }
            NativeListCard("Team Zeituebersicht", time, "Keine Zeitdaten.")
            NativeListCard("Zeitkonten", balances, "Keine Zeitkonten.")
            NativeListCard("Wochensaldo", weekly, "Keine Wochensalden.")
        }
        "requests" -> {
            AdminInboxCard(corrections, vacation, sick, payroll)
            ModuleActionsCard(summary, isSubmitting, onSubmitModuleAction)
            NativeListCard("Korrekturen", corrections, "Keine Korrekturantraege.")
            NativeListCard("Urlaub", vacation, "Keine Urlaubsantraege.")
            NativeListCard("Payroll offen", payroll, "Keine offenen Lohnabrechnungen.")
        }
        "calendar" -> {
            CalendarStripCard("Abwesenheitskalender", listOfNotNull(vacation, sick), "Keine Abwesenheiten.", days = 14)
            NativeListCard("Urlaub", vacation, "Keine Urlaubsantraege.")
            NativeListCard("Krankheit", sick, "Keine Krankmeldungen.")
        }
        "modules" -> {
            AdminModuleGrid("Freigegebene Admin-Seiten", adminSections.filterNot { it == AppSection.ADMIN_HOME }, onSelectSection)
            DataSourceStatusCard(summary)
        }
        else -> {
            AdminDashboardOverviewPanel(
                user = user,
                employees = employees,
                corrections = corrections,
                vacation = vacation,
                sick = sick,
                payroll = payroll,
                balances = balances,
                onOpenTime = { activeTab = "time" },
                onOpenRequests = { activeTab = "requests" },
                onOpenCalendar = { activeTab = "calendar" },
                onOpenModules = { activeTab = "modules" },
            )
        }
    }
}

@Composable
private fun SuperAdminScreen(summary: ModuleSummary?, onSelectSection: (AppSection) -> Unit) {
    val feedback = endpoint(summary, "App Feedback")
    val companies = endpoint(summary, "Firmen")
    val analytics = endpoint(summary, "Analytics")
    val excludedIps = endpoint(summary, "Ausgeschlossene IPs")
    val changes = endpoint(summary, "Aenderungen")
    var activeTab by remember { mutableStateOf("overview") }
    val tabs = listOf(
        DashboardTab("overview", "Uebersicht", endpointNumericCount(feedback)),
        DashboardTab("companies", "Firmen", endpointNumericCount(companies)),
        DashboardTab("analytics", "Analytics", endpointNumericCount(analytics)),
        DashboardTab("feedback", "Feedback", endpointNumericCount(feedback)),
        DashboardTab("system", "System", endpointNumericCount(excludedIps)),
    )

    HeroCard("Superadmin Dashboard", "Mandanten, App-Feedback, Analytics und Systemstatus.")
    DashboardTabBar(tabs, activeTab) { activeTab = it }
    when (activeTab) {
        "companies" -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Firmen", endpointCount(companies), Modifier.weight(1f))
                MetricCard("Aktionen", "6", Modifier.weight(1f))
            }
            AdminModuleGrid("Firmenverwaltung", listOf(AppSection.COMPANY), onSelectSection)
            NativeListCard("Firmen", companies, "Keine Firmen.")
        }
        "analytics" -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Analytics", endpointCount(analytics), Modifier.weight(1f))
                MetricCard("IP Filter", endpointCount(excludedIps), Modifier.weight(1f))
            }
            NativeListCard("Analytics", analytics, "Keine Analytics-Daten.")
            NativeListCard("Ausgeschlossene IPs", excludedIps, "Keine ausgeschlossenen IPs.")
        }
        "feedback" -> NativeListCard("App Feedback", feedback, "Noch kein App-Feedback.")
        "system" -> {
            NativeListCard("Ausgeschlossene IPs", excludedIps, "Keine ausgeschlossenen IPs.")
            NativeListCard("Aenderungen", changes, "Keine Aenderungen.")
        }
        else -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Feedback", endpointCount(feedback), Modifier.weight(1f))
                MetricCard("Firmen", endpointCount(companies), Modifier.weight(1f))
            }
            CardBox {
                SectionTitle("Was jetzt wichtig ist")
                DashboardActionButton("App Feedback pruefen") { activeTab = "feedback" }
                DashboardActionButton("Firmen verwalten") { onSelectSection(AppSection.COMPANY) }
                DashboardActionButton("Analytics ansehen") { activeTab = "analytics" }
                DashboardActionButton("Admin Dashboard oeffnen") { onSelectSection(AppSection.ADMIN_HOME) }
            }
            NativeListCard("Neues App Feedback", feedback, "Noch kein App-Feedback.")
            NativeListCard("Firmen", companies, "Keine Firmen.")
        }
    }
}

@Composable
private fun EmployeesScreen(summary: ModuleSummary?) {
    val employees = endpoint(summary, "Mitarbeiter")
    val balances = endpoint(summary, "Zeitkonten")
    val weekly = endpoint(summary, "Wochensaldo")
    val vacation = endpoint(summary, "Urlaub")
    val sick = endpoint(summary, "Krankheit")
    HeroCard("Mitarbeiter", "Team, Zeitkonten und Abwesenheiten.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Team", endpointCount(employees), Modifier.weight(1f))
        MetricCard("Zeitkonten", endpointCount(balances), Modifier.weight(1f))
    }
    NativeListCard("Mitarbeiter", employees, "Keine Mitarbeiter.")
    NativeListCard("Zeitkonten", balances, "Keine Zeitkonten.")
    NativeListCard("Wochensaldo", weekly, "Keine Wochensalden.")
    NativeListCard("Urlaub", vacation, "Keine Urlaubsantraege.")
    NativeListCard("Krankheit", sick, "Keine Krankmeldungen.")
}

@Composable
private fun UsersScreen(summary: ModuleSummary?) {
    val users = endpoint(summary, "Benutzer")
    val audit = endpoint(summary, "Audit")
    HeroCard("Benutzer", "Benutzerverwaltung, Rollen und Audit.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Benutzer", endpointCount(users), Modifier.weight(1f))
        MetricCard("Audit", endpointCount(audit), Modifier.weight(1f))
    }
    NativeListCard("Benutzer", users, "Keine Benutzer.")
    NativeListCard("Audit", audit, "Keine Audit-Eintraege.")
}

@Composable
private fun AdminPasswordScreen(summary: ModuleSummary?) {
    HeroCard("Admin-Passwort", "Eigenes Admin-Passwort sicher aendern.")
    DetailRowsCard("Sicherheit", listOf("Aktion" to "Passwort aendern", "Kontext" to "Admin-Konto"))
    DataSourceStatusCard(summary)
}

@Composable
private fun CustomersProjectsScreen(user: UserProfile, summary: ModuleSummary?) {
    val customers = endpoint(summary, "Kunden")
    val projects = endpoint(summary, "Projekte")
    val hierarchy = endpoint(summary, "Projektbaum")
    HeroCard("Kunden & Projekte", "App-Arbeitsflaeche fuer Kunden, Projekte und Auswertungen.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Kunden", endpointCount(customers), Modifier.weight(1f))
        MetricCard("Projekte", endpointCount(projects), Modifier.weight(1f))
    }
    DetailRowsCard("Status", listOf("Kundenerfassung" to if (user.customerTrackingEnabled) "Aktiv" else "Aus", "Rollen" to user.roles.joinToString(", ").ifBlank { "-" }))
    NativeListCard("Kunden", customers, "Keine Kunden.")
    NativeListCard("Projekte", projects, "Keine Projekte.")
    NativeListCard("Projektbaum", hierarchy, "Keine Projektstruktur.")
}

@Composable
private fun CustomersScreen(summary: ModuleSummary?) {
    val customers = endpoint(summary, "Kunden")
    val recent = endpoint(summary, "Zuletzt")
    HeroCard("Kunden", "Kundenverwaltung und Zuordnungen.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Kunden", endpointCount(customers), Modifier.weight(1f))
        MetricCard("Zuletzt", endpointCount(recent), Modifier.weight(1f))
    }
    NativeListCard("Kunden", customers, "Keine Kunden.")
    NativeListCard("Zuletzt verwendet", recent, "Keine zuletzt verwendeten Kunden.")
}

@Composable
private fun ProjectsScreen(summary: ModuleSummary?) {
    val projects = endpoint(summary, "Projekte")
    val hierarchy = endpoint(summary, "Projektbaum")
    val analytics = endpoint(summary, "Projektanalyse")
    val integrations = endpoint(summary, "Integrationen")
    HeroCard("Projekte", "Projekte, Hierarchie, Budgets und Auswertungen.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Projekte", endpointCount(projects), Modifier.weight(1f))
        MetricCard("Analyse", endpointCount(analytics), Modifier.weight(1f))
    }
    NativeListCard("Projekte", projects, "Keine Projekte.")
    NativeListCard("Projektbaum", hierarchy, "Keine Projektstruktur.")
    NativeListCard("Projektanalyse", analytics, "Keine Projektdaten.")
    NativeListCard("Integrationen", integrations, "Keine Integrationen.")
}

@Composable
private fun TasksScreen(summary: ModuleSummary?) {
    val projects = endpoint(summary, "Projekte")
    val hierarchy = endpoint(summary, "Projektbaum")
    HeroCard("Aufgaben", "Aufgaben und operative Arbeitspakete.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Projekte", endpointCount(projects), Modifier.weight(1f))
        MetricCard("Aktionen", (summary?.actions?.size ?: 0).toString(), Modifier.weight(1f))
    }
    NativeListCard("Projekte", projects, "Keine Projekte.")
    NativeListCard("Projektbaum", hierarchy, "Keine Projektstruktur.")
}

@Composable
private fun ProjectReportScreen(summary: ModuleSummary?) {
    val projects = endpoint(summary, "Projekte")
    val analytics = endpoint(summary, "Projektanalyse")
    val audit = endpoint(summary, "Audit")
    HeroCard("Projektbericht", "Projektzeiten, Budgets und Auswertungen.")
    NativeListCard("Projekte", projects, "Keine Projekte.")
    NativeListCard("Projektanalyse", analytics, "Keine Projektdaten.")
    NativeListCard("Audit", audit, "Keine Audit-Eintraege.")
}

@Composable
private fun AnalyticsScreen(summary: ModuleSummary?) {
    val time = endpoint(summary, "Zeituebersicht")
    val projects = endpoint(summary, "Projektanalyse")
    val audit = endpoint(summary, "Audit")
    HeroCard("Analytics", "Zeit, Projekte, Reports und Audit-Auswertungen.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Zeit", endpointCount(time), Modifier.weight(1f))
        MetricCard("Projekte", endpointCount(projects), Modifier.weight(1f))
    }
    NativeListCard("Zeituebersicht", time, "Keine Zeitdaten.")
    NativeListCard("Projektanalyse", projects, "Keine Projektdaten.")
    NativeListCard("Audit", audit, "Keine Audit-Eintraege.")
}

@Composable
private fun TimeImportScreen(summary: ModuleSummary?) {
    val time = endpoint(summary, "Zeituebersicht")
    val balances = endpoint(summary, "Zeitkonten")
    val audit = endpoint(summary, "Audit")
    HeroCard("Zeitimport", "Zeitdaten importieren und Salden pruefen.")
    NativeListCard("Zeituebersicht", time, "Keine Zeitdaten.")
    NativeListCard("Zeitkonten", balances, "Keine Zeitkonten.")
    NativeListCard("Audit", audit, "Keine Audit-Eintraege.")
}

@Composable
private fun ScheduleScreen(summary: ModuleSummary?) {
    val entries = endpoint(summary, "Dienstplan")
    val shifts = endpoint(summary, "Schichten")
    val rules = endpoint(summary, "Planregeln")
    val expected = endpoint(summary, "Sollzeit")
    val holidays = endpoint(summary, "Feiertagsoptionen")
    HeroCard("Dienstplan", "Schichten, Wochenplan und Arbeitsregeln.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Eintraege", endpointCount(entries), Modifier.weight(1f))
        MetricCard("Regeln", endpointCount(rules), Modifier.weight(1f))
    }
    CalendarStripCard("Wochenplan", listOfNotNull(entries), "Keine geplanten Schichten.", days = 7, start = startOfWeek(LocalDate.now()))
    NativeListCard("Dienstplan", entries, "Keine Schichten in dieser Woche.")
    NativeListCard("Schichten", shifts, "Keine Schichtdefinitionen.")
    NativeListCard("Planregeln", rules, "Keine Planregeln.")
    NativeListCard("Sollzeit heute", expected, "Keine Sollzeitdaten.")
    NativeListCard("Feiertagsoptionen", holidays, "Keine Feiertagsoptionen.")
}

@Composable
private fun PrintScheduleScreen(summary: ModuleSummary?) {
    val entries = endpoint(summary, "Dienstplan")
    val users = endpoint(summary, "Mitarbeiter")
    val shifts = endpoint(summary, "Schichten")
    val rules = endpoint(summary, "Planregeln")
    HeroCard("Plan drucken", "Dienstplaene fuer Woche und Team vorbereiten.")
    CalendarStripCard("Druckvorschau", listOfNotNull(entries), "Keine geplanten Schichten.", days = 7, start = startOfWeek(LocalDate.now()))
    NativeListCard("Dienstplan", entries, "Keine Schichten in dieser Woche.")
    NativeListCard("Mitarbeiter", users, "Keine Mitarbeiter.")
    NativeListCard("Schichten", shifts, "Keine Schichtdefinitionen.")
    NativeListCard("Planregeln", rules, "Keine Planregeln.")
}

@Composable
private fun ShiftRulesScreen(summary: ModuleSummary?) {
    val shifts = endpoint(summary, "Schichten")
    val allShifts = endpoint(summary, "Alle Schichten")
    val rules = endpoint(summary, "Planregeln")
    val expected = endpoint(summary, "Sollzeit")
    val users = endpoint(summary, "Mitarbeiter")
    HeroCard("Schichtregeln", "Schichtdefinitionen und Planregeln pflegen.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Schichten", endpointCount(allShifts ?: shifts), Modifier.weight(1f))
        MetricCard("Planregeln", endpointCount(rules), Modifier.weight(1f))
    }
    NativeListCard("Schichten", shifts, "Keine aktiven Schichten.")
    NativeListCard("Alle Schichten", allShifts, "Keine Schichtdefinitionen.")
    NativeListCard("Planregeln", rules, "Keine Planregeln.")
    NativeListCard("Sollzeit heute", expected, "Keine Sollzeitdaten.")
    NativeListCard("Mitarbeiter", users, "Keine Mitarbeiter.")
}

@Composable
private fun PayrollAdminScreen(session: AuthenticatedSession, summary: ModuleSummary?) {
    val pending = endpoint(summary, "Offen")
    val approved = endpoint(summary, "Freigegeben")
    val all = endpoint(summary, "Alle")
    HeroCard("Payroll", "Lohnlaeufe, Payslips und Freigaben.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Offen", endpointCount(pending), Modifier.weight(1f))
        MetricCard("Alle", endpointCount(all), Modifier.weight(1f))
    }
    DownloadsCard(
        title = "Payroll Export",
        session = session,
        downloads = listOf(
            DownloadSpec("CSV", "/api/payslips/admin/export?lang=de", "payroll-export.csv"),
            DownloadSpec("Backup", "/api/payslips/admin/backup", "payroll-backup.csv"),
        ),
    )
    DownloadListCard(
        title = "Payslip PDF",
        endpoint = all ?: pending ?: approved,
        emptyText = "Keine Payslips fuer PDF-Download.",
        session = session,
        pathFor = { item -> "/api/payslips/admin/pdf/${item.id}?lang=de" },
        fileNameFor = { item -> "admin-payslip-${item.id}.pdf" },
    )
    NativeListCard("Offene Payslips", pending, "Keine offenen Lohnabrechnungen.")
    NativeListCard("Freigegeben", approved, "Keine freigegebenen Lohnabrechnungen.")
    NativeListCard("Alle Payslips", all, "Keine Lohnabrechnungen.")
}

@Composable
private fun AccountingScreen(summary: ModuleSummary?) {
    val receivables = endpoint(summary, "Debitoren")
    val payables = endpoint(summary, "Kreditoren")
    val accounts = endpoint(summary, "Konten")
    val journal = endpoint(summary, "Journal")
    val assets = endpoint(summary, "Anlagen")
    HeroCard("Buchhaltung", "Debitoren, Kreditoren, Konten, Journal und Anlagen.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Konten", endpointCount(accounts), Modifier.weight(1f))
        MetricCard("Journal", endpointCount(journal), Modifier.weight(1f))
    }
    NativeListCard("Debitoren", receivables, "Keine offenen Debitoren.")
    NativeListCard("Kreditoren", payables, "Keine offenen Kreditoren.")
    NativeListCard("Konten", accounts, "Keine Konten.")
    NativeListCard("Journal", journal, "Keine Journalbuchungen.")
    NativeListCard("Anlagen", assets, "Keine Anlagen.")
}

@Composable
private fun ChronoTwoScreen(summary: ModuleSummary?) {
    val products = endpoint(summary, "Produkte")
    val locations = endpoint(summary, "Lagerorte")
    val inventory = endpoint(summary, "Bestand")
    val analytics = endpoint(summary, "Analytics")
    val returns = endpoint(summary, "Retouren")
    val blockchain = endpoint(summary, "Blockchain")
    HeroCard("Chrono 2.0", "Warehouse, IoT, Procurement und KI-Funktionen.")
    NativeListCard("Produkte", products, "Keine Produkte.")
    NativeListCard("Lagerorte", locations, "Keine Lagerorte.")
    NativeListCard("Bestand", inventory, "Kein Bestand.")
    NativeListCard("Analytics", analytics, "Keine Analytics-Daten.")
    NativeListCard("Retouren", returns, "Keine Retouren.")
    NativeListCard("Blockchain", blockchain, "Keine Blockchain-Bewegungen.")
}

@Composable
private fun CrmScreen(summary: ModuleSummary?) {
    val customers = endpoint(summary, "Kunden")
    val leads = endpoint(summary, "Leads")
    val opportunities = endpoint(summary, "Opportunities")
    val campaigns = endpoint(summary, "Kampagnen")
    HeroCard("CRM", "Kunden, Leads, Pipeline und Kampagnen.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Leads", endpointCount(leads), Modifier.weight(1f))
        MetricCard("Deals", endpointCount(opportunities), Modifier.weight(1f))
    }
    NativeListCard("Kunden", customers, "Keine Kunden.")
    NativeListCard("Leads", leads, "Keine Leads.")
    NativeListCard("Opportunities", opportunities, "Keine Opportunities.")
    NativeListCard("Kampagnen", campaigns, "Keine Kampagnen.")
}

@Composable
private fun BankingScreen(session: AuthenticatedSession, summary: ModuleSummary?) {
    val accounts = endpoint(summary, "Bankkonten")
    val batches = endpoint(summary, "Zahlungslaeufe")
    val open = endpoint(summary, "Offene Laeufe")
    val payables = endpoint(summary, "Kreditoren")
    val receivables = endpoint(summary, "Debitoren")
    val signatures = endpoint(summary, "Signaturen")
    val messages = endpoint(summary, "Nachrichten")
    HeroCard("Banking", "Bankkonten, Zahlungslaeufe, Signaturen und Nachrichten.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Konten", endpointCount(accounts), Modifier.weight(1f))
        MetricCard("Offen", endpointCount(open), Modifier.weight(1f))
    }
    DownloadListCard(
        title = "Pain.001 XML",
        endpoint = batches ?: open,
        emptyText = "Keine Zahlungslaeufe fuer XML-Export.",
        session = session,
        pathFor = { item -> "/api/banking/pain001/${item.id}" },
        fileNameFor = { item -> "pain001-${item.id}.xml" },
    )
    NativeListCard("Bankkonten", accounts, "Keine Bankkonten.")
    NativeListCard("Zahlungslaeufe", batches, "Keine Zahlungslaeufe.")
    NativeListCard("Offene Laeufe", open, "Keine offenen Zahlungslaeufe.")
    NativeListCard("Kreditoren", payables, "Keine offenen Kreditoren.")
    NativeListCard("Debitoren", receivables, "Keine offenen Debitoren.")
    NativeListCard("Signaturen", signatures, "Keine Signaturen.")
    NativeListCard("Nachrichten", messages, "Keine Nachrichten.")
}

@Composable
private fun KnowledgeScreen(summary: ModuleSummary?) {
    val knowledge = endpoint(summary, "Knowledge") ?: endpoint(summary, "Wissen")
    HeroCard("Firmenwissen", "Interne Wissensbasis und KI-Inhalte.")
    NativeListCard("Wissen", knowledge, "Keine Wissenseintraege.")
}

@Composable
private fun CompanySettingsScreen(summary: ModuleSummary?) {
    val company = endpoint(summary, "Firma")
    val settings = endpoint(summary, "Einstellungen")
    val holidays = endpoint(summary, "Feiertage")
    HeroCard("Einstellungen", "Globale Firmen- und Systemkonfiguration.")
    NativeListCard("Firma", company, "Keine Firmendaten.")
    NativeListCard("Einstellungen", settings, "Keine Einstellungen.")
    NativeListCard("Feiertage", holidays, "Keine Feiertagsdaten.")
}

@Composable
private fun CompanyManagementScreen(session: AuthenticatedSession, summary: ModuleSummary?) {
    val companies = endpoint(summary, "Firmen")
    val analytics = endpoint(summary, "Analytics")
    val excludedIps = endpoint(summary, "Ausgeschlossene IPs")
    val changes = endpoint(summary, "Aenderungen")
    HeroCard("Firmen", "Mandantenverwaltung, Zahlungsstatus und Superadmin-Uebersicht.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Firmen", endpointCount(companies), Modifier.weight(1f))
        MetricCard("Analytics", endpointCount(analytics), Modifier.weight(1f))
    }
    DownloadListCard(
        title = "Firmenexport",
        endpoint = companies,
        emptyText = "Keine Firmen fuer Export.",
        session = session,
        pathFor = { item -> "/api/superadmin/companies/${item.id}/export" },
        fileNameFor = { item -> "company-${item.id}.csv" },
    )
    NativeListCard("Firmen", companies, "Keine Firmen.")
    NativeListCard("Analytics", analytics, "Keine Analytics-Daten.")
    NativeListCard("Ausgeschlossene IPs", excludedIps, "Keine ausgeschlossenen IPs.")
    NativeListCard("Aenderungen", changes, "Keine Aenderungen.")
}

@Composable
private fun ModuleOverviewScreen(section: AppSection, user: UserProfile, summary: ModuleSummary?) {
    HeroCard(section.title, section.subtitle)
    FeatureListCard("Funktionen", section.primaryItems)
    DetailRowsCard("Bereich", moduleRows(section, user))
    ModuleDataCard(summary)
}

@Composable
private fun TodayTrackingCard(user: UserProfile, summary: DailyTimeSummary?, now: LocalDateTime, isPunching: Boolean, onPunch: () -> Unit) {
    val isOpen = summary?.primaryTimes?.isOpen == true
    val worked = displayWorkedMinutes(summary, now)
    CardBox {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (isOpen) "Du bist eingestempelt" else "Bereit zum Start", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(user.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                color = if (isOpen) Color(0xFFDFF7E7) else MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    if (isOpen) "Laeuft" else "Offen",
                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isOpen) Color(0xFF146C2E) else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Text(formatMinutes(worked), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactFact("Start", summary?.primaryTimes?.firstStartTime.formatTimeOrDash(), Modifier.weight(1f))
            CompactFact("Ende", summary?.primaryTimes?.lastEndTime.formatTimeOrDash(), Modifier.weight(1f))
            CompactFact("Pause", formatMinutes(summary?.breakMinutes ?: 0), Modifier.weight(1f))
        }
        Button(onClick = onPunch, enabled = !isPunching, modifier = Modifier.fillMaxWidth()) {
            Text(if (isPunching) "Speichern..." else if (isOpen) "Ende stempeln" else "Start stempeln")
        }
    }
}

@Composable
private fun TimeQuickStats(user: UserProfile, summary: DailyTimeSummary?, now: LocalDateTime) {
    val worked = displayWorkedMinutes(summary, now)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard("Heute", formatMinutes(worked), Modifier.weight(1f))
        MetricCard("Saldo", formatSignedMinutes(user.trackingBalanceInMinutes), Modifier.weight(1f))
        MetricCard("Soll", user.dailyWorkHours?.let { "${it.formatHours()} h" } ?: "-", Modifier.weight(1f))
    }
}

@Composable
private fun TrackingSelectionCard(
    user: UserProfile,
    state: DashboardUiState,
    onSelectCustomer: (Long?) -> Unit,
    onSelectProject: (Long?) -> Unit,
    onSelectTask: (Long?) -> Unit,
) {
    if (!user.customerTrackingEnabled) return
    val customerProjects = state.projects.filter { state.selectedCustomerId == null || it.customerId == state.selectedCustomerId }
    val selectedCustomer = state.customers.firstOrNull { it.id == state.selectedCustomerId }?.name ?: "Kein Kunde"
    val selectedProject = state.projects.firstOrNull { it.id == state.selectedProjectId }?.name ?: "Kein Projekt"
    val selectedTask = state.tasks.firstOrNull { it.id == state.selectedTaskId }?.let(::taskLabel) ?: "Kein Task"
    CardBox {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionTitle("Zuordnung")
                Text("Wohin soll deine Zeit gebucht werden?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.isLoadingSelections) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        OptionDropdown("Kunde", selectedCustomer, listOf(null to "Kein Kunde") + state.customers.map { it.id to it.name }, onSelectCustomer)
        OptionDropdown("Projekt", selectedProject, listOf(null to "Kein Projekt") + customerProjects.map { it.id to it.name }, onSelectProject)
        OptionDropdown("Task", selectedTask, listOf(null to "Kein Task") + state.tasks.map { it.id to taskLabel(it) }, onSelectTask)
    }
}

@Composable
private fun OptionDropdown(label: String, selected: String, options: List<Pair<Long?, String>>, onSelect: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selected, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onSelect(id) })
            }
        }
    }
}

@Composable
private fun DailyNoteCard(note: String, isSaving: Boolean, onNoteChange: (String) -> Unit, onSaveNote: () -> Unit) {
    CardBox {
        SectionTitle("Tagesnotiz")
        OutlinedTextField(value = note, onValueChange = onNoteChange, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text("Notiz") })
        Button(onClick = onSaveNote, enabled = !isSaving, modifier = Modifier.fillMaxWidth()) { Text(if (isSaving) "Speichern..." else "Notiz speichern") }
    }
}

@Composable
private fun WeekOverview(weekSummaries: List<Pair<LocalDate, DailyTimeSummary?>>, now: LocalDateTime) {
    CardBox {
        SectionTitle("Woche")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            weekSummaries.forEach { (date, summary) ->
                Column(
                    Modifier.weight(1f).background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp)).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(dayShortLabel(date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text(formatMinutes(displayWorkedMinutes(summary, now)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TodayEntries(summary: DailyTimeSummary?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Stempel heute")
        val entries = summary?.entries.orEmpty().sortedBy { it.entryTimestamp }
        if (entries.isEmpty()) {
            EmptyState("Heute gibt es noch keine Stempel.")
        } else {
            entries.forEach { EntryRow(it) }
        }
    }
}

@Composable
private fun EntryRow(entry: TimeTrackingEntry) {
    CardBox {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(punchLabel(entry.punchType), fontWeight = FontWeight.SemiBold)
                Text(listOfNotNull(entry.customerName, entry.projectName, entry.taskName).joinToString(" / ").ifBlank { sourceLabel(entry.source) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(entry.entryTimestamp?.toLocalTime().formatTimeOrDash(), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ModuleDataCard(summary: ModuleSummary?) {
    CardBox {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Live Daten")
            if (summary == null || summary.isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("${summary.readyEndpoints}/${summary.endpoints.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            summary == null || summary.isLoading -> Text("Backend-Verbindungen werden geladen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            summary.endpoints.isEmpty() -> Text("Keine mobile Datenquelle.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InlineMetric("Quellen", summary.endpoints.size.toString(), Modifier.weight(1f))
                    InlineMetric("Eintraege", summary.totalCount.toString(), Modifier.weight(1f))
                }
                summary.endpoints.forEachIndexed { index, endpoint ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    EndpointResultRow(endpoint)
                }
            }
        }
    }
}

@Composable
private fun DataSourceStatusCard(summary: ModuleSummary?) {
    CardBox {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Datenstatus")
            if (summary == null || summary.isLoading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("${summary.readyEndpoints}/${summary.endpoints.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (summary == null || summary.isLoading) {
            Text("Daten werden geladen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InlineMetric("Quellen", summary.endpoints.size.toString(), Modifier.weight(1f))
                InlineMetric("Eintraege", summary.totalCount.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AdminInboxCard(
    corrections: ModuleEndpointResult?,
    vacation: ModuleEndpointResult?,
    sick: ModuleEndpointResult?,
    payroll: ModuleEndpointResult?,
) {
    CardBox {
        SectionTitle("Offene Arbeit")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InlineMetric("Korrekturen", endpointCount(corrections), Modifier.weight(1f))
            InlineMetric("Urlaub", endpointCount(vacation), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InlineMetric("Krankheit", endpointCount(sick), Modifier.weight(1f))
            InlineMetric("Payroll", endpointCount(payroll), Modifier.weight(1f))
        }
    }
}

private data class DashboardTab(val id: String, val label: String, val count: Int = 0)

@Composable
private fun DashboardTabBar(tabs: List<DashboardTab>, selectedId: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tabs.forEach { tab ->
            val label = if (tab.count > 0) "${tab.label} ${tab.count}" else tab.label
            if (tab.id == selectedId) {
                Button(
                    onClick = { onSelect(tab.id) },
                    modifier = Modifier.height(40.dp),
                    contentPadding = ButtonDefaults.ContentPadding,
                ) { Text(label, style = MaterialTheme.typography.labelLarge) }
            } else {
                OutlinedButton(
                    onClick = { onSelect(tab.id) },
                    modifier = Modifier.height(40.dp),
                    contentPadding = ButtonDefaults.ContentPadding,
                ) { Text(label, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
private fun AdminDashboardOverviewPanel(
    user: UserProfile,
    employees: ModuleEndpointResult?,
    corrections: ModuleEndpointResult?,
    vacation: ModuleEndpointResult?,
    sick: ModuleEndpointResult?,
    payroll: ModuleEndpointResult?,
    balances: ModuleEndpointResult?,
    onOpenTime: () -> Unit,
    onOpenRequests: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenModules: () -> Unit,
) {
    val pendingCorrections = pendingItemCount(corrections)
    val pendingVacation = pendingItemCount(vacation)
    val pendingTotal = pendingCorrections + pendingVacation
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Offene Antraege", pendingTotal.toString(), Modifier.weight(1f))
        MetricCard("Mitarbeiter", endpointCount(employees), Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Zeitkonten", endpointCount(balances), Modifier.weight(1f))
        MetricCard("Payroll", endpointCount(payroll), Modifier.weight(1f))
    }
    CardBox {
        SectionTitle("Was jetzt wichtig ist")
        Text("Hallo ${user.displayName}. Dieses Dashboard zeigt Team-Aufgaben statt deiner persoenlichen Stempelung.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InlineMetric("Urlaub", pendingVacation.toString(), Modifier.weight(1f))
            InlineMetric("Korrekturen", pendingCorrections.toString(), Modifier.weight(1f))
        }
        DashboardActionButton("Antraege pruefen", onOpenRequests)
        DashboardActionButton("Teamzeit ansehen", onOpenTime)
        DashboardActionButton("Abwesenheiten planen", onOpenCalendar)
        DashboardActionButton("Module oeffnen", onOpenModules)
    }
    AdminInboxCard(corrections, vacation, sick, payroll)
    NativeListCard("Mitarbeiter", employees, "Keine Mitarbeiter.")
}

@Composable
private fun DashboardActionButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun AdminModuleGrid(title: String, sections: List<AppSection>, onSelectSection: (AppSection) -> Unit) {
    CardBox {
        SectionTitle(title)
        sections.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { section ->
                    OutlinedButton(
                        onClick = { onSelectSection(section) },
                        modifier = Modifier.weight(1f).height(46.dp),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) {
                        Text(section.title, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NativeListCard(title: String, endpoint: ModuleEndpointResult?, emptyText: String) {
    var expanded by remember(title, endpoint?.path, endpoint?.count) { mutableStateOf(false) }
    var query by remember(title, endpoint?.path, endpoint?.count) { mutableStateOf("") }
    var selectedItemId by remember(title, endpoint?.path, endpoint?.count) { mutableStateOf<String?>(null) }
    CardBox {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(title)
            Text(endpointStatusText(endpoint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            endpoint == null -> Text("Daten werden geladen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            endpoint.status == ModuleRequestStatus.FORBIDDEN -> Text("Keine Berechtigung.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            endpoint.status == ModuleRequestStatus.ERROR -> Text(endpoint.message ?: "Konnte nicht geladen werden.", color = MaterialTheme.colorScheme.error)
            endpoint.items.isEmpty() && endpoint.preview.isNotEmpty() -> {
                endpoint.preview.forEachIndexed { index, line ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text(line, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            endpoint.items.isEmpty() -> Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                if (endpoint.items.size > 8) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Suchen") },
                        singleLine = true,
                    )
                }
                val filteredItems = endpoint.items.filter { item ->
                    query.isBlank() || item.searchText().contains(query, ignoreCase = true)
                }
                val visibleItems = if (expanded) filteredItems else filteredItems.take(5)
                visibleItems.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    val isSelected = selectedItemId == item.id
                    ModuleListItemRow(item, isSelected) {
                        selectedItemId = if (isSelected) null else item.id
                    }
                    if (isSelected) ModuleItemDetails(item)
                }
                if (filteredItems.isEmpty()) {
                    Text("Keine Treffer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (filteredItems.size > 5) {
                    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (expanded) "Weniger anzeigen" else "Alle ${filteredItems.size} anzeigen")
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarStripCard(
    title: String,
    endpoints: List<ModuleEndpointResult>,
    emptyText: String,
    days: Int = 14,
    start: LocalDate = LocalDate.now(),
) {
    val items = endpoints.flatMap { it.items }
    val visibleDays = (0 until days).map { start.plusDays(it.toLong()) }
    val itemsByDay = visibleDays.associateWith { day -> items.filter { it.occursOn(day) } }
    CardBox {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(title)
            Text("${items.size} Eintraege", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (items.isEmpty()) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            visibleDays.chunked(7).forEach { weekDays ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    weekDays.forEach { day ->
                        val count = itemsByDay[day].orEmpty().size
                        Column(
                            Modifier
                                .weight(1f)
                                .background(
                                    if (count > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(dayShortLabel(day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Text(if (count > 0) count.toString() else "-", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    repeat(7 - weekDays.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            val nextItems = items
                .mapNotNull { item -> item.startDateForDisplay()?.let { it to item } }
                .sortedBy { it.first }
                .take(3)
            nextItems.forEachIndexed { index, (date, item) ->
                if (index == 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(item.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        item.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    Text(date.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun ModuleListItem.occursOn(day: LocalDate): Boolean {
    val range = dateRange()
    val start = range.first ?: return false
    val end = range.second ?: start
    return !day.isBefore(start) && !day.isAfter(end)
}

private fun ModuleListItem.startDateForDisplay(): LocalDate? = dateRange().first

private fun ModuleListItem.dateRange(): Pair<LocalDate?, LocalDate?> {
    fun valueFor(vararg labels: String): LocalDate? =
        details.firstOrNull { detail -> labels.any { label -> detail.label.equals(label, ignoreCase = true) } }
            ?.value
            ?.take(10)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    val start = valueFor("Start", "Datum", "Antrag", "Zeitpunkt", "Periode Start", "Abschluss")
    val end = valueFor("Ende", "Periode Ende") ?: start
    return start to end
}

private data class DownloadSpec(
    val title: String,
    val path: String,
    val fileName: String,
)

@Composable
private fun DownloadsCard(
    title: String,
    session: AuthenticatedSession,
    downloads: List<DownloadSpec>,
) {
    if (downloads.isEmpty()) return
    val context = LocalContext.current
    CardBox {
        SectionTitle(title)
        downloads.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { download ->
                    OutlinedButton(
                        onClick = { startAuthenticatedDownload(context, session, download) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(download.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DownloadListCard(
    title: String,
    endpoint: ModuleEndpointResult?,
    emptyText: String,
    session: AuthenticatedSession,
    pathFor: (ModuleListItem) -> String,
    fileNameFor: (ModuleListItem) -> String,
) {
    val items = endpoint?.items.orEmpty()
    if (items.isEmpty()) return
    var expanded by remember(title, endpoint?.path, endpoint?.count) { mutableStateOf(false) }
    val visibleItems = if (expanded) items else items.take(4)
    val context = LocalContext.current
    CardBox {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(title)
            Text("${items.size} Dateien", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        visibleItems.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    item.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                OutlinedButton(
                    onClick = {
                        startAuthenticatedDownload(
                            context = context,
                            session = session,
                            download = DownloadSpec(title = "Download", path = pathFor(item), fileName = fileNameFor(item)),
                        )
                    },
                ) {
                    Text("Download")
                }
            }
        }
        if (items.size > 4) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Text(if (expanded) "Weniger anzeigen" else "Alle Downloads anzeigen")
            }
        }
    }
}

private fun startAuthenticatedDownload(context: Context, session: AuthenticatedSession, download: DownloadSpec) {
    val url = if (download.path.startsWith("http", ignoreCase = true)) {
        download.path
    } else {
        BuildConfig.API_BASE_URL.trimEnd('/') + download.path
    }
    val request = DownloadManager.Request(Uri.parse(url))
        .addRequestHeader("Authorization", "Bearer ${session.token}")
        .setTitle(download.fileName)
        .setDescription("Chrono Export")
        .setMimeType(download.fileName.mimeType())
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, download.fileName)

    runCatching {
        val manager = context.getSystemService(DownloadManager::class.java)
        manager.enqueue(request)
    }.onSuccess {
        Toast.makeText(context, "Download gestartet: ${download.fileName}", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "Download konnte nicht gestartet werden.", Toast.LENGTH_SHORT).show()
    }
}

private fun String.mimeType(): String =
    when (substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "csv" -> "text/csv"
        "ics" -> "text/calendar"
        "xml" -> "application/xml"
        else -> "application/octet-stream"
    }

private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")

@Composable
private fun ModuleListItemRow(item: ModuleListItem, selected: Boolean, onClick: () -> Unit) {
    val status = item.statusChip()
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.label, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            status?.let { StatusPill(it) }
            Text("#${item.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class ItemStatusChip(val label: String, val containerColor: Color, val contentColor: Color)

@Composable
private fun StatusPill(status: ItemStatusChip) {
    Surface(color = status.containerColor, shape = RoundedCornerShape(999.dp)) {
        Text(
            status.label,
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = status.contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun ModuleListItem.statusChip(): ItemStatusChip? {
    val explicitStatus = details.firstOrNull { detail ->
        detail.label in setOf("Status", "Genehmigt", "Abgelehnt", "Aktiv", "Bezahlt", "Gekuendigt", "Zeituebersicht")
    }?.value?.takeIf { it.isNotBlank() }
    explicitStatus?.let { return statusChipFor(it, allowUnknown = true) }
    val detailStatus = detail?.substringBefore("|")?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return statusChipFor(detailStatus, allowUnknown = false)
}

private fun statusChipFor(label: String, allowUnknown: Boolean): ItemStatusChip? {
    return when (label.lowercase()) {
        "genehmigt", "approved", "active", "aktiv", "bezahlt", "paid", "ja", "true" ->
            ItemStatusChip(label.toAppStatusLabel(), Color(0xFFDFF7E7), Color(0xFF146C2E))
        "offen", "open", "pending", "neu", "new" ->
            ItemStatusChip(label.toAppStatusLabel(), Color(0xFFE8F0FF), Color(0xFF1D4ED8))
        "abgelehnt", "denied", "rejected", "false", "nein", "inaktiv", "inactive" ->
            ItemStatusChip(label.toAppStatusLabel(), Color(0xFFFFE4E6), Color(0xFFBE123C))
        "gesendet", "transmitted", "completed", "done", "geschlossen", "closed" ->
            ItemStatusChip(label.toAppStatusLabel(), Color(0xFFE0F2FE), Color(0xFF0369A1))
        else -> if (allowUnknown) ItemStatusChip(label.toAppStatusLabel(), Color(0xFFE8EEF5), Color(0xFF334155)) else null
    }
}

private fun String.toAppStatusLabel(): String =
    when (lowercase()) {
        "true", "ja" -> "Ja"
        "false", "nein" -> "Nein"
        "approved" -> "Genehmigt"
        "pending", "open" -> "Offen"
        "denied", "rejected" -> "Abgelehnt"
        "active" -> "Aktiv"
        "inactive" -> "Inaktiv"
        "paid" -> "Bezahlt"
        "transmitted" -> "Gesendet"
        "completed", "done" -> "Erledigt"
        "closed" -> "Geschlossen"
        else -> replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

@Composable
private fun ModuleItemDetails(item: ModuleListItem) {
    val details = buildItemDetails(item)
    if (details.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        details.forEach { detail ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Text(detail.label, modifier = Modifier.weight(0.42f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(detail.value, modifier = Modifier.weight(0.58f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun buildItemDetails(item: ModuleListItem): List<ModuleItemDetail> {
    val rows = mutableListOf<ModuleItemDetail>()
    rows += ModuleItemDetail("ID", item.id)
    if (item.value != item.id) rows += ModuleItemDetail("Wert", item.value)
    item.detail?.let { rows += ModuleItemDetail("Details", it) }
    item.details.forEach { detail ->
        if (rows.none { it.label == detail.label && it.value == detail.value }) rows += detail
    }
    return rows.take(12)
}

private fun ModuleListItem.searchText(): String =
    buildString {
        append(id).append(' ')
        append(label).append(' ')
        detail?.let { append(it).append(' ') }
        details.forEach { append(it.label).append(' ').append(it.value).append(' ') }
    }

private fun endpoint(summary: ModuleSummary?, label: String): ModuleEndpointResult? =
    summary?.endpoints?.firstOrNull { it.label.contains(label, ignoreCase = true) }

private fun endpointCount(endpoint: ModuleEndpointResult?): String =
    when {
        endpoint == null -> "-"
        endpoint.status == ModuleRequestStatus.ERROR || endpoint.status == ModuleRequestStatus.FORBIDDEN -> "-"
        else -> (endpoint.count ?: 0).toString()
    }

private fun endpointNumericCount(endpoint: ModuleEndpointResult?): Int =
    when {
        endpoint == null -> 0
        endpoint.status == ModuleRequestStatus.ERROR || endpoint.status == ModuleRequestStatus.FORBIDDEN -> 0
        else -> endpoint.count ?: endpoint.items.size
    }

private fun pendingItemCount(endpoint: ModuleEndpointResult?): Int {
    if (endpoint == null || endpoint.status == ModuleRequestStatus.ERROR || endpoint.status == ModuleRequestStatus.FORBIDDEN) {
        return 0
    }
    if (endpoint.items.isEmpty()) {
        return endpoint.count ?: 0
    }
    return endpoint.items.count { it.isPendingApprovalItem() }
}

private fun ModuleListItem.isPendingApprovalItem(): Boolean {
    val approved = details.firstOrNull { it.label == "Genehmigt" }?.value?.isPositiveValue()
    val denied = details.firstOrNull { it.label == "Abgelehnt" }?.value?.isPositiveValue()
    val status = details.firstOrNull { it.label == "Status" }?.value ?: detail.orEmpty()
    return when {
        approved == true || denied == true -> false
        status.contains("abgelehnt", ignoreCase = true) -> false
        status.contains("genehmigt", ignoreCase = true) -> false
        status.contains("denied", ignoreCase = true) -> false
        status.contains("approved", ignoreCase = true) -> false
        status.contains("offen", ignoreCase = true) -> true
        status.contains("pending", ignoreCase = true) -> true
        approved == false && denied == false -> true
        else -> true
    }
}

private fun String.isPositiveValue(): Boolean =
    equals("true", ignoreCase = true) || equals("ja", ignoreCase = true) || equals("yes", ignoreCase = true)

private fun endpointStatusText(endpoint: ModuleEndpointResult?): String =
    when {
        endpoint == null || endpoint.status == ModuleRequestStatus.ERROR -> "Laedt"
        endpoint.status == ModuleRequestStatus.FORBIDDEN -> "Gesperrt"
        endpoint.status == ModuleRequestStatus.EMPTY -> "Leer"
        else -> "${endpoint.count ?: 0} Eintraege"
    }

@Composable
private fun EndpointResultRow(endpoint: ModuleEndpointResult) {
    val color = when (endpoint.status) {
        ModuleRequestStatus.READY -> Color(0xFF2E7D32)
        ModuleRequestStatus.EMPTY -> MaterialTheme.colorScheme.outline
        ModuleRequestStatus.FORBIDDEN -> Color(0xFFF59E0B)
        ModuleRequestStatus.ERROR -> MaterialTheme.colorScheme.error
    }
    val statusText = when (endpoint.status) {
        ModuleRequestStatus.READY -> "${endpoint.count ?: 0}"
        ModuleRequestStatus.EMPTY -> "Leer"
        ModuleRequestStatus.FORBIDDEN -> "Keine Rechte"
        ModuleRequestStatus.ERROR -> endpoint.message ?: "Fehler"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusDot(color, Modifier.padding(top = 5.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(endpoint.label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (endpoint.preview.isNotEmpty()) Text(endpoint.preview.joinToString(" | "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            endpoint.items.take(5).forEach { item ->
                Text(
                    text = "#${item.id} ${item.label}${item.detail?.let { " - $it" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ModuleActionsCard(summary: ModuleSummary?, isSubmitting: Boolean, onSubmit: (ModuleAction, Map<String, String>) -> Unit) {
    val actions = summary?.actions.orEmpty()
    if (actions.isEmpty()) return
    var selectedActionId by remember(summary?.section, actions.size) { mutableStateOf(actions.first().id) }
    val selectedAction = actions.firstOrNull { it.id == selectedActionId } ?: actions.first()
    var formOpen by remember(selectedAction.id) { mutableStateOf(false) }
    CardBox {
        SectionTitle("Aktionen")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEach { action ->
                if (action.id == selectedAction.id) {
                    Button(
                        onClick = { selectedActionId = action.id },
                        modifier = Modifier.height(38.dp),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) { Text(action.title, style = MaterialTheme.typography.labelMedium) }
                } else {
                    OutlinedButton(
                        onClick = { selectedActionId = action.id },
                        modifier = Modifier.height(38.dp),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) { Text(action.title, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        if (formOpen) {
            ModuleActionForm(selectedAction, summary, isSubmitting, onSubmit)
            if (selectedAction.fields.size > 4) {
                TextButton(onClick = { formOpen = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("Formular einklappen")
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(selectedAction.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("${selectedAction.fields.size} Felder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { formOpen = true }) {
                    Text("Oeffnen")
                }
            }
        }
    }
}

@Composable
private fun ModuleActionForm(
    action: ModuleAction,
    summary: ModuleSummary?,
    isSubmitting: Boolean,
    onSubmit: (ModuleAction, Map<String, String>) -> Unit,
) {
    var values by remember(action.id) { mutableStateOf(action.fields.associate { it.key to it.defaultValue }) }
    var pendingConfirmation by remember(action.id) { mutableStateOf<Map<String, String>?>(null) }
    pendingConfirmation?.let { pendingValues ->
        AlertDialog(
            onDismissRequest = { pendingConfirmation = null },
            title = { Text("Aktion bestaetigen") },
            text = { Text("${action.title} wirklich ausfuehren? Diese Aktion kann Daten freigeben, ablehnen, senden oder loeschen.") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingConfirmation = null
                        onSubmit(action, pendingValues)
                    },
                    enabled = !isSubmitting,
                ) {
                    Text("Bestaetigen")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmation = null }) {
                    Text("Abbrechen")
                }
            },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(action.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (action.description.isNotBlank()) Text(action.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action.fields.forEach { field ->
            ModuleActionFieldInput(
                field = field,
                value = values[field.key].orEmpty(),
                enabled = !isSubmitting,
                options = optionsForField(action, field, summary),
            ) { values = values + (field.key to it) }
        }
        Button(
            onClick = {
                if (action.requiresConfirmation()) {
                    pendingConfirmation = values
                } else {
                    onSubmit(action, values)
                }
            },
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isSubmitting) "Wird gespeichert" else action.title)
        }
    }
}

private fun ModuleAction.requiresConfirmation(): Boolean =
    method == ModuleActionMethod.DELETE ||
        id.contains("approve", ignoreCase = true) ||
        id.contains("deny", ignoreCase = true) ||
        id.contains("delete", ignoreCase = true) ||
        id.contains("apply", ignoreCase = true) ||
        id.contains("transmit", ignoreCase = true) ||
        id.contains("complete", ignoreCase = true) ||
        id.contains("copy", ignoreCase = true) ||
        id.contains("auto", ignoreCase = true) ||
        id.contains("reopen", ignoreCase = true) ||
        id.contains("fulfill", ignoreCase = true)

@Composable
private fun ModuleActionFieldInput(
    field: ModuleActionField,
    value: String,
    enabled: Boolean,
    options: List<ModuleListItem>,
    onValueChange: (String) -> Unit,
) {
    if (field.type == ModuleActionFieldType.BOOLEAN) {
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            Text(field.label, Modifier.weight(1f))
            OutlinedButton(onClick = { onValueChange(if (value.equals("true", true)) "false" else "true") }, enabled = enabled) {
                Text(if (value.equals("true", true)) "Ja" else "Nein")
            }
        }
    } else if (options.isNotEmpty()) {
        var expanded by remember(field.key) { mutableStateOf(false) }
        var query by remember(field.key, expanded) { mutableStateOf("") }
        val selected = options.firstOrNull { it.id == value }
        val filteredOptions = options
            .filter { option ->
                query.isBlank() ||
                    option.label.contains(query, ignoreCase = true) ||
                    option.detail.orEmpty().contains(query, ignoreCase = true) ||
                    option.id.contains(query, ignoreCase = true)
            }
            .take(40)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "${field.label}: ${selected?.let(::optionLabel) ?: value.ifBlank { "Auswaehlen" }}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                if (options.size > 8) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Suchen") },
                        singleLine = true,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                filteredOptions.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                optionLabel(option),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        onClick = {
                            expanded = false
                            onValueChange(option.id)
                        },
                    )
                }
                if (filteredOptions.isEmpty()) {
                    DropdownMenuItem(text = { Text("Keine Treffer") }, onClick = {})
                }
            }
        }
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(field.label) },
            enabled = enabled,
            singleLine = field.type != ModuleActionFieldType.MULTILINE,
            minLines = if (field.type == ModuleActionFieldType.MULTILINE) 3 else 1,
            visualTransformation = if (field.type == ModuleActionFieldType.PASSWORD) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = when (field.type) {
                    ModuleActionFieldType.NUMBER -> KeyboardType.Number
                    ModuleActionFieldType.PASSWORD -> KeyboardType.Password
                    else -> KeyboardType.Text
                },
                imeAction = if (field.type == ModuleActionFieldType.MULTILINE) ImeAction.Default else ImeAction.Next,
            ),
        )
    }
}

private fun optionsForField(
    action: ModuleAction,
    field: ModuleActionField,
    summary: ModuleSummary?,
): List<ModuleListItem> {
    if (summary == null) return emptyList()
    val labelHints = when {
        field.key == "targetUsername" -> listOf("Mitarbeiter", "Benutzer")
        field.key == "userId" || (field.key == "id" && action.id in setOf("user-delete", "user-visibility-update")) -> listOf("Mitarbeiter", "Benutzer")
        field.key == "shift" -> listOf("Schichten")
        field.key == "customerId" || (field.key == "id" && action.id in setOf("customer-update", "customer-delete")) -> listOf("Kunden")
        field.key in setOf("projectId", "parentId") || (field.key == "id" && action.id in setOf("project-update", "project-delete")) -> listOf("Projekte", "Projektbaum")
        field.key == "id" && action.id in setOf("task-update", "task-delete") -> listOf("Aufgaben")
        field.key == "productId" -> listOf("Artikel", "Produkte")
        field.key == "warehouseId" -> listOf("Lager")
        field.key == "purchaseOrderId" -> listOf("Einkauf")
        field.key == "locationId" -> listOf("Lagerorte")
        field.key in setOf("debitAccountId", "creditAccountId") -> listOf("Konten")
        field.key == "bankAccountId" -> listOf("Bankkonten")
        field.key == "id" && action.id in setOf("schedule-entry-update", "schedule-entry-delete") -> listOf("Dienstplan")
        field.key == "id" && action.id == "shift-definition-update" -> listOf("Schichten", "Alle Schichten")
        field.key == "id" && action.id in setOf("schedule-rule-update", "schedule-rule-delete") -> listOf("Planregeln")
        field.key == "id" && action.id in setOf("time-entry-approve", "time-entry-revoke", "time-entry-customer", "time-entry-project") -> listOf("Zeituebersicht")
        field.key == "id" && action.id in setOf("correction-approve", "correction-deny") -> listOf("Korrekturen")
        field.key == "id" && action.id in setOf("vacation-approve", "vacation-deny", "vacation-delete") -> listOf("Urlaub")
        field.key == "id" && action.id == "sick-delete" -> listOf("Krankheit")
        field.key == "id" && action.id in setOf("payslip-approve", "payslip-set-payout", "payslip-reopen", "payslip-delete") -> listOf("Offen", "Alle", "Freigegeben")
        field.key == "id" && action.id == "knowledge-delete" -> listOf("Knowledge", "Wissen")
        field.key == "id" && action.id in setOf("cycle-count-submit", "cycle-count-approve") -> listOf("Inventur")
        field.key == "id" && action.id == "purchase-order-receive" -> listOf("Einkauf")
        field.key == "id" && action.id == "sales-order-fulfill" -> listOf("Verkauf")
        field.key == "id" && action.id == "service-request-status" -> listOf("Services", "Service")
        field.key == "id" && action.id == "production-order-status" -> listOf("Produktion")
        field.key == "id" && action.id == "asset-depreciate" -> listOf("Anlagen")
        field.key == "id" && action.id == "receivable-payment" -> listOf("Debitoren")
        field.key == "id" && action.id == "payable-payment" -> listOf("Kreditoren")
        field.key == "id" && action.id in setOf("bank-account-update", "bank-account-delete") -> listOf("Bankkonten")
        field.key == "id" && action.id in setOf("payment-batch-approve", "payment-batch-transmit") -> listOf("Zahlungslaeufe", "Offene Laeufe")
        field.key == "id" && action.id in setOf("signature-refresh", "signature-complete") -> listOf("Signaturen")
        field.key == "id" && action.id == "lead-status" -> listOf("Leads")
        field.key == "id" && action.id == "opportunity-update" -> listOf("Opportunities")
        field.key == "id" && action.id == "campaign-update" -> listOf("Kampagnen")
        field.key == "companyId" || (field.key == "id" && action.id in setOf("company-update", "company-payment", "company-delete")) -> listOf("Firmen")
        field.key == "id" && action.id == "excluded-ip-delete" -> listOf("Ausgeschlossene IPs")
        else -> emptyList()
    }

    if (labelHints.isEmpty()) return emptyList()
    val options = summary.endpoints
        .filter { endpoint -> labelHints.any { hint -> endpoint.label.contains(hint, ignoreCase = true) } }
        .flatMap { it.items }
        .distinctBy { it.id }
    return if (field.key == "targetUsername" || field.key == "shift") {
        options.map { it.copy(id = it.value) }.distinctBy { it.id }
    } else {
        options
    }
}

private fun optionLabel(option: ModuleListItem): String =
    buildString {
        if (option.id.all { it.isDigit() }) append("#").append(option.id).append(" ")
        append(option.label)
        option.detail?.let { append(" - ").append(it) }
    }

@Composable
private fun HeroCard(title: String, subtitle: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.size(width = 4.dp, height = 44.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(999.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun FeatureListCard(title: String, items: List<String>) {
    CardBox {
        SectionTitle(title)
        items.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            item,
                            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DetailRowsCard(title: String, rows: List<Pair<String, String>>) {
    CardBox {
        SectionTitle(title)
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Text(row.first, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.9f))
                Text(row.second, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.1f))
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun InlineMetric(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CompactFact(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CardBox(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun EmptyState(text: String) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MessageCard(text: String, isError: Boolean) {
    Surface(color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun LoadingDataCard() {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Zeiterfassung wird geladen")
        }
    }
}

@Composable
private fun AppHeader(statusText: String) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChronoMark()
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text("Chrono", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Mobile App", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(999.dp)) {
            Text(
                statusText,
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(9.dp).background(color, CircleShape))
}

@Composable
private fun ChronoMark() {
    Surface(color = Color.White, shape = CircleShape, modifier = Modifier.size(46.dp)) {
        Image(
            painter = painterResource(id = R.drawable.chrono_logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(CircleShape),
        )
    }
}

private fun displayWorkedMinutes(summary: DailyTimeSummary?, now: LocalDateTime): Int {
    if (summary == null) return 0
    val openStart = summary.entries.filter { it.punchType == PunchType.START && it.entryTimestamp != null }.maxByOrNull { it.entryTimestamp!! }?.entryTimestamp
    val running = if (summary.primaryTimes.isOpen && openStart != null && openStart.toLocalDate() == now.toLocalDate()) {
        Duration.between(openStart, now).toMinutes().coerceAtLeast(0).toInt()
    } else 0
    return summary.workedMinutes + running
}

private fun startOfWeek(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())
private fun dayShortLabel(date: LocalDate) = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")[date.dayOfWeek.value - 1]
private fun LocalTime?.formatTimeOrDash(): String = this?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "-"
private fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes.coerceAtLeast(0) / 60, minutes.coerceAtLeast(0) % 60)
private fun formatSignedMinutes(minutes: Int): String = (if (minutes >= 0) "+" else "-") + formatMinutes(kotlin.math.abs(minutes))
private fun Double.formatHours(): String = if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this)
private fun punchLabel(type: PunchType): String = if (type == PunchType.START) "Start" else if (type == PunchType.ENDE) "Ende" else "Unbekannt"
private fun sourceLabel(source: String?): String = when (source) { "NFC_SCAN" -> "NFC"; "SYSTEM_AUTO_END" -> "Automatisch"; "MANUAL_PUNCH" -> "Manuell"; else -> "Stempel" }
private fun taskLabel(task: TaskOption): String = if (task.billable) "${task.name} (verrechenbar)" else task.name
private fun userModelLabel(user: UserProfile): String = when { user.isHourly -> "Stundenlohn"; user.isPercentage -> "Pensum ${user.workPercentage}%"; else -> "Mitarbeiter" }
private fun userInitials(user: UserProfile): String {
    val parts = listOfNotNull(user.firstName, user.lastName).filter { it.isNotBlank() }
    return parts.take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { user.username.take(2).uppercase() }
}

private fun moduleRows(section: AppSection, user: UserProfile): List<Pair<String, String>> =
    listOf("Bereich" to section.group, "Benutzer" to user.displayName, "Status" to "App Ansicht", "Rollen" to user.roles.joinToString(", ").ifBlank { "-" })
