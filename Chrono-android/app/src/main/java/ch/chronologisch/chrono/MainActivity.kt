package ch.chronologisch.chrono

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.rounded.Menu
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
                    onContactFormOpenChange = loginViewModel::setContactFormOpen,
                    onContactCompanyNameChange = loginViewModel::updateContactCompanyName,
                    onContactNameChange = loginViewModel::updateContactName,
                    onContactEmailChange = loginViewModel::updateContactEmail,
                    onContactPhoneChange = loginViewModel::updateContactPhone,
                    onContactEmployeeCountChange = loginViewModel::updateContactEmployeeCount,
                    onContactAdditionalInfoChange = loginViewModel::updateContactAdditionalInfo,
                    onContactTermsAcceptedChange = loginViewModel::updateContactTermsAccepted,
                    onContactAcceptedChange = loginViewModel::updateContactAccepted,
                    onSubmitContactRequest = loginViewModel::submitContactRequest,
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
    onContactFormOpenChange: (Boolean) -> Unit,
    onContactCompanyNameChange: (String) -> Unit,
    onContactNameChange: (String) -> Unit,
    onContactEmailChange: (String) -> Unit,
    onContactPhoneChange: (String) -> Unit,
    onContactEmployeeCountChange: (String) -> Unit,
    onContactAdditionalInfoChange: (String) -> Unit,
    onContactTermsAcceptedChange: (Boolean) -> Unit,
    onContactAcceptedChange: (Boolean) -> Unit,
    onSubmitContactRequest: () -> Unit,
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
        else -> LoginScreen(
            state = loginState,
            onUsernameChange = onUsernameChange,
            onPasswordChange = onPasswordChange,
            onLogin = onLogin,
            onDemoLogin = onDemoLogin,
            onContactFormOpenChange = onContactFormOpenChange,
            onContactCompanyNameChange = onContactCompanyNameChange,
            onContactNameChange = onContactNameChange,
            onContactEmailChange = onContactEmailChange,
            onContactPhoneChange = onContactPhoneChange,
            onContactEmployeeCountChange = onContactEmployeeCountChange,
            onContactAdditionalInfoChange = onContactAdditionalInfoChange,
            onContactTermsAcceptedChange = onContactTermsAcceptedChange,
            onContactAcceptedChange = onContactAcceptedChange,
            onSubmitContactRequest = onSubmitContactRequest,
        )
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
    onContactFormOpenChange: (Boolean) -> Unit,
    onContactCompanyNameChange: (String) -> Unit,
    onContactNameChange: (String) -> Unit,
    onContactEmailChange: (String) -> Unit,
    onContactPhoneChange: (String) -> Unit,
    onContactEmployeeCountChange: (String) -> Unit,
    onContactAdditionalInfoChange: (String) -> Unit,
    onContactTermsAcceptedChange: (Boolean) -> Unit,
    onContactAcceptedChange: (Boolean) -> Unit,
    onSubmitContactRequest: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    val loginFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.onSurface,
        unfocusedTextColor = colors.onSurface,
        disabledTextColor = colors.onSurfaceVariant,
        focusedLabelColor = colors.onSurfaceVariant,
        unfocusedLabelColor = colors.onSurfaceVariant,
        disabledLabelColor = colors.onSurfaceVariant,
        focusedContainerColor = colors.surfaceVariant,
        unfocusedContainerColor = colors.surfaceVariant,
        disabledContainerColor = colors.surfaceVariant,
        focusedBorderColor = colors.primary,
        unfocusedBorderColor = colors.outline,
        disabledBorderColor = colors.outline,
        cursorColor = colors.primary,
    )
    val contactForm = state.contactForm
    Scaffold(containerColor = colors.background, topBar = { WebLoginTopBar() }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, colors.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text("Chrono", color = colors.primary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Surface(color = colors.secondaryContainer, shape = RoundedCornerShape(999.dp), border = BorderStroke(1.dp, colors.outline)) {
                        Text("SICHERER ZUGANG", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = colors.onSecondaryContainer, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Surface(color = colors.primaryContainer, shape = RoundedCornerShape(999.dp), border = BorderStroke(1.dp, colors.outline)) {
                        Text("ALL-IN-ONE PLATTFORM FÜR TEAMS IN CH & DE", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = colors.onPrimaryContainer, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Text("Willkommen\nzurück!", color = colors.onSurface, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Melde dich an, um fortzufahren.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = state.username,
                    onValueChange = onUsernameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Benutzername") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    colors = loginFieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                )
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Passwort") },
                        singleLine = true,
                        enabled = !state.isLoading,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = loginFieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); onLogin() }),
                    )
                    state.errorMessage?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall) }
                    Button(
                        onClick = onLogin,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                    ) {
                        Text(if (state.isLoading) "Login..." else "Login", fontWeight = FontWeight.Bold)
                    }
                    if (BuildConfig.DEMO_LOGIN_ENABLED) {
                        OutlinedButton(onClick = onDemoLogin, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
                            Text("Demo starten")
                        }
                    }
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, colors.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Noch kein Konto?", color = colors.onSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Schick uns eine kurze Anfrage, dann melden wir uns zur Einrichtung.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    state.contactMessage?.let { Text(it, color = Color(0xFF34D399), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
                    TextButton(onClick = { onContactFormOpenChange(!state.contactFormOpen) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.contactFormOpen) "Kontaktformular schließen" else "Jetzt Kontaktanfrage schicken", fontWeight = FontWeight.Bold)
                    }
                    if (state.contactFormOpen) {
                        HorizontalDivider(color = colors.outline)
                        OutlinedTextField(
                            value = contactForm.companyName,
                            onValueChange = onContactCompanyNameChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Firmenname") },
                            singleLine = true,
                            enabled = !state.isSubmittingContact,
                            colors = loginFieldColors,
                        )
                        OutlinedTextField(
                            value = contactForm.contactName,
                            onValueChange = onContactNameChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Ansprechperson") },
                            singleLine = true,
                            enabled = !state.isSubmittingContact,
                            colors = loginFieldColors,
                        )
                        OutlinedTextField(
                            value = contactForm.email,
                            onValueChange = onContactEmailChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("E-Mail") },
                            singleLine = true,
                            enabled = !state.isSubmittingContact,
                            colors = loginFieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        )
                        OutlinedTextField(
                            value = contactForm.phone,
                            onValueChange = onContactPhoneChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Telefon (optional)") },
                            singleLine = true,
                            enabled = !state.isSubmittingContact,
                            colors = loginFieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                        )
                        OutlinedTextField(
                            value = contactForm.employeeCount,
                            onValueChange = onContactEmployeeCountChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Mitarbeiterzahl") },
                            singleLine = true,
                            enabled = !state.isSubmittingContact,
                            colors = loginFieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        )
                        OutlinedTextField(
                            value = contactForm.additionalInfo,
                            onValueChange = onContactAdditionalInfoChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Weitere Informationen oder Fragen") },
                            minLines = 3,
                            enabled = !state.isSubmittingContact,
                            colors = loginFieldColors,
                        )
                        LoginConsentRow(
                            checked = contactForm.termsAccepted,
                            enabled = !state.isSubmittingContact,
                            text = "Ich akzeptiere die Bedingungen und Datenschutz-Hinweise.",
                            onCheckedChange = onContactTermsAcceptedChange,
                        )
                        LoginConsentRow(
                            checked = contactForm.contactAccepted,
                            enabled = !state.isSubmittingContact,
                            text = "Chrono darf mich per E-Mail oder Telefon zur Einrichtung kontaktieren.",
                            onCheckedChange = onContactAcceptedChange,
                        )
                        state.contactError?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall) }
                        Button(
                            onClick = onSubmitContactRequest,
                            enabled = !state.isSubmittingContact,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                        ) {
                            Text(if (state.isSubmittingContact) "Senden..." else "Unverbindliche Anfrage senden", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginConsentRow(
    checked: Boolean,
    enabled: Boolean,
    text: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onCheckedChange(!checked) }, verticalAlignment = Alignment.Top) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(text, Modifier.padding(top = 12.dp), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun WebLoginTopBar() {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(colors.surface).statusBarsPadding().height(58.dp).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Chrono", color = colors.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Icon(Icons.Rounded.Menu, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(32.dp))
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
    val scrollState = rememberScrollState()
    var scrollResetKey by remember { mutableStateOf(0) }
    var feedbackDialogOpen by remember { mutableStateOf(false) }
    var platformMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(selectedSection, scrollResetKey) {
        scrollState.scrollTo(0)
    }

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
        topBar = {
            WebTopBar(
                platformMenuOpen = platformMenuOpen,
                onTogglePlatform = { platformMenuOpen = !platformMenuOpen },
                onFeedback = { feedbackDialogOpen = true },
                onLogout = onLogout,
            )
        },
        bottomBar = {
            WebBottomDock(
                sections = sections,
                selectedSection = selectedSection,
                onSelectSection = onSelectSection,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(scrollState).padding(horizontal = 8.dp, vertical = 10.dp).padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (platformMenuOpen) {
                PlatformMenuCard(
                    sections = sections,
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    onFeedback = { feedbackDialogOpen = true },
                    onLogout = onLogout,
                    onSelectSection = { section ->
                        platformMenuOpen = false
                        onSelectSection(section)
                    },
                )
            }
            state.errorMessage?.let { MessageCard(it, true) }
            state.infoMessage?.let { MessageCard(it, false) }
            if (selectedSection != AppSection.TIME && selectedSection != AppSection.ADMIN_HOME && selectedSection != AppSection.TIME_OVERVIEW && selectedSection != AppSection.TEAM_CALENDAR && selectedSection != AppSection.SUPERADMIN_HOME) {
                WebPageHeader(section = selectedSection)
            }
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
                    isSubmitting = state.isSubmittingModuleAction,
                    onSubmitModuleAction = onSubmitModuleAction,
                )
                AppSection.WORK_MODEL -> WorkModelScreen(session.user, weekSummaries, todaySummary, state.now, state.moduleSummaries[selectedSection])
                AppSection.ABSENCES -> AbsenceScreen(
                    user = session.user,
                    summary = state.moduleSummaries[selectedSection],
                    isSubmitting = state.isSubmittingModuleAction,
                    onSubmitModuleAction = onSubmitModuleAction,
                )
                AppSection.PAYSLIPS -> PayslipScreen(session, state.moduleSummaries[selectedSection])
                AppSection.REPORTS -> ReportsScreen(session, state.moduleSummaries[selectedSection])
                AppSection.PROFILE -> ProfileScreen(
                    user = session.user,
                    summary = state.moduleSummaries[selectedSection],
                    isSubmitting = state.isSubmittingModuleAction,
                    onSubmitModuleAction = onSubmitModuleAction,
                )
                AppSection.SUPPLY_CHAIN -> SupplyChainScreen(state.moduleSummaries[selectedSection])
                AppSection.INFO -> InfoScreen(state.moduleSummaries[selectedSection])
                AppSection.ADMIN_HOME -> AdminHomeScreen(
                    user = session.user,
                    summary = state.moduleSummaries[selectedSection],
                    isSubmitting = state.isSubmittingModuleAction,
                    onSubmitModuleAction = onSubmitModuleAction,
                    onSelectSection = onSelectSection,
                    onContentJump = { scrollResetKey++ },
                )
                AppSection.TIME_OVERVIEW -> TimeOverviewScreen(
                    summary = state.moduleSummaries[selectedSection],
                    isSubmitting = state.isSubmittingModuleAction,
                    onSubmitModuleAction = onSubmitModuleAction,
                )
                AppSection.TEAM_CALENDAR -> TeamCalendarScreen(
                    summary = state.moduleSummaries[selectedSection],
                    isSubmitting = state.isSubmittingModuleAction,
                    onSubmitModuleAction = onSubmitModuleAction,
                )
                AppSection.SUPERADMIN_HOME -> SuperAdminScreen(state.moduleSummaries[selectedSection], onSelectSection)
                AppSection.EMPLOYEES -> EmployeesScreen(
                    summary = state.moduleSummaries[selectedSection],
                    isSubmitting = state.isSubmittingModuleAction,
                    onSubmitModuleAction = onSubmitModuleAction,
                )
                AppSection.USERS -> UsersScreen(
                    summary = state.moduleSummaries[selectedSection],
                    isSubmitting = state.isSubmittingModuleAction,
                    onSubmitModuleAction = onSubmitModuleAction,
                )
                AppSection.ADMIN_PASSWORD -> AdminPasswordScreen(state.moduleSummaries[selectedSection])
                AppSection.CUSTOMERS_PROJECTS -> CustomersProjectsScreen(session.user, state.moduleSummaries[selectedSection])
                AppSection.CUSTOMERS -> CustomersScreen(state.moduleSummaries[selectedSection])
                AppSection.PROJECTS -> ProjectsScreen(state.moduleSummaries[selectedSection])
                AppSection.TASKS -> TasksScreen(state.moduleSummaries[selectedSection])
                AppSection.PROJECT_REPORT -> ProjectReportScreen(state.moduleSummaries[selectedSection])
                AppSection.ANALYTICS -> AnalyticsScreen(state.moduleSummaries[selectedSection])
                AppSection.TIME_IMPORT -> TimeImportScreen(state.moduleSummaries[selectedSection])
                AppSection.SCHEDULE -> ScheduleScreen(
                    summary = state.moduleSummaries[selectedSection],
                    isSubmitting = state.isSubmittingModuleAction,
                    onSubmitModuleAction = onSubmitModuleAction,
                )
                AppSection.PRINT_SCHEDULE -> PrintScheduleScreen(state.moduleSummaries[selectedSection])
                AppSection.SHIFT_RULES -> ShiftRulesScreen(state.moduleSummaries[selectedSection])
                AppSection.PAYROLL_ADMIN -> PayrollAdminScreen(
                    session = session,
                    summary = state.moduleSummaries[selectedSection],
                    isSubmitting = state.isSubmittingModuleAction,
                    onSubmitModuleAction = onSubmitModuleAction,
                )
                AppSection.ACCOUNTING -> AccountingScreen(state.moduleSummaries[selectedSection])
                AppSection.CHRONO_TWO -> ChronoTwoScreen(state.moduleSummaries[selectedSection])
                AppSection.CRM -> CrmScreen(state.moduleSummaries[selectedSection])
                AppSection.BANKING -> BankingScreen(session, state.moduleSummaries[selectedSection])
                AppSection.KNOWLEDGE -> KnowledgeScreen(state.moduleSummaries[selectedSection])
                AppSection.COMPANY_SETTINGS -> CompanySettingsScreen(state.moduleSummaries[selectedSection])
                AppSection.COMPANY -> CompanyManagementScreen(session, state.moduleSummaries[selectedSection])
            }

            if (!showActionsFirst &&
                selectedSection != AppSection.ADMIN_HOME &&
                selectedSection != AppSection.TIME_OVERVIEW &&
                selectedSection != AppSection.TEAM_CALENDAR &&
                selectedSection != AppSection.EMPLOYEES &&
                selectedSection != AppSection.USERS &&
                selectedSection != AppSection.SCHEDULE &&
                selectedSection != AppSection.PAYROLL_ADMIN &&
                selectedSection != AppSection.TIME &&
                selectedSection != AppSection.ABSENCES &&
                selectedSection != AppSection.PROFILE
            ) {
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
            AppSection.TEAM_CALENDAR.takeIf { it in sections },
            AppSection.EMPLOYEES.takeIf { it in sections },
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
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun WebTopBar(
    platformMenuOpen: Boolean,
    onTogglePlatform: () -> Unit,
    onFeedback: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF11131E))
            .statusBarsPadding(),
    ) {
        Box(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 14.dp),
        ) {
            Text(
                "Chrono",
                color = Color(0xFF5B8CFF),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Button(
                onClick = onTogglePlatform,
                shape = RoundedCornerShape(10.dp),
                contentPadding = ButtonDefaults.ContentPadding,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C2742), contentColor = MaterialTheme.colorScheme.onSurface),
                border = BorderStroke(1.dp, Color(0xFF5B8CFF)),
                modifier = Modifier.align(Alignment.Center).width(104.dp).height(40.dp),
            ) {
                Text(if (platformMenuOpen) "Schließen" else "Menü", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onFeedback, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = "Verbesserung senden", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = onLogout, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Abmelden", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

private data class WebDockItem(val code: String, val title: String, val subtitle: String, val section: AppSection)

@Composable
private fun WebBottomDock(
    sections: List<AppSection>,
    selectedSection: AppSection,
    onSelectSection: (AppSection) -> Unit,
) {
    val items = if (AppSection.ADMIN_HOME in sections) {
        listOfNotNull(
            AppSection.TEAM_CALENDAR.takeIf { it in sections }?.let { WebDockItem("Kal", "Kal", "Urlaub", it) },
            AppSection.ADMIN_HOME.takeIf { it in sections }?.let { WebDockItem("Admin", "Admin", "Admin-Start", it) },
            AppSection.EMPLOYEES.takeIf { it in sections }?.let { WebDockItem("Team", "Team", "Benutzerverwaltung", it) },
            AppSection.TIME_OVERVIEW.takeIf { it in sections }?.let { WebDockItem("Zeit", "Zeit", "Übersicht", it) },
        )
    } else {
        listOfNotNull(
            AppSection.TIME.takeIf { it in sections }?.let { WebDockItem("Zeit", "Zeit", "Stempeln", it) },
            AppSection.ABSENCES.takeIf { it in sections }?.let { WebDockItem("Abw", "Abw", "Absenzen", it) },
            AppSection.PAYSLIPS.takeIf { it in sections }?.let { WebDockItem("Lohn", "Lohn", "Abrechnungen", it) },
            AppSection.PROFILE.takeIf { it in sections }?.let { WebDockItem("Ich", "Ich", "Profil", it) },
        )
    }
    if (items.isEmpty()) return

    Surface(color = Color(0xFF111827), shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                WebBottomDockItem(
                    item = item,
                    selected = item.section == selectedSection,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectSection(item.section) },
                )
            }
        }
    }
}

@Composable
private fun WebBottomDockItem(item: WebDockItem, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val background = if (selected) Color(0xFF4B63F6) else Color.Transparent
    val content = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(item.title, color = content, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.subtitle, color = content.copy(alpha = 0.86f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlatformMenuCard(
    sections: List<AppSection>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onFeedback: () -> Unit,
    onLogout: () -> Unit,
    onSelectSection: (AppSection) -> Unit,
) {
    val groups = listOf(
        "Zeit & Team" to listOf(AppSection.TIME_OVERVIEW, AppSection.TEAM_CALENDAR, if (AppSection.USERS in sections) AppSection.USERS else AppSection.EMPLOYEES, AppSection.SCHEDULE),
        "Lohn & Finanzen" to listOf(AppSection.PAYROLL_ADMIN, AppSection.ACCOUNTING, AppSection.BANKING),
        "Wachstum" to listOf(AppSection.CUSTOMERS_PROJECTS, AppSection.CRM, AppSection.SUPPLY_CHAIN),
        "Setup" to listOf(AppSection.KNOWLEDGE, AppSection.CHRONO_TWO, AppSection.COMPANY_SETTINGS, AppSection.COMPANY),
    )
    CardBox {
        Text("CHRONO", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text("Arbeitsbereich wechseln", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        DashboardActionButton("Zum Dashboard") { onSelectSection(AppSection.ADMIN_HOME.takeIf { it in sections } ?: AppSection.TIME) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        groups.forEach { (title, groupSections) ->
            val visible = groupSections.filter { it in sections }
            if (visible.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    visible.forEach { section ->
                        PlatformMenuRow(section = section, onClick = { onSelectSection(section) })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh, enabled = !isRefreshing, modifier = Modifier.weight(1f)) {
                Text(if (isRefreshing) "Lädt" else "Neu laden", maxLines = 1)
            }
            OutlinedButton(onClick = onFeedback, modifier = Modifier.weight(1f)) {
                Text("Feedback", maxLines = 1)
            }
            OutlinedButton(onClick = onLogout, modifier = Modifier.weight(1f)) {
                Text("Logout", maxLines = 1)
            }
        }
    }
}

@Composable
private fun PlatformMenuRow(section: AppSection, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.size(width = 40.dp, height = 32.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    section.platformCode(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
        Text(section.platformLabel(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WebPageHeader(section: AppSection) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(section.group.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(section.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(section.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun AppSection.navIcon(): ImageVector = when (this) {
    AppSection.TIME -> Icons.Rounded.Home
    AppSection.WORK_MODEL -> Icons.Rounded.Tune
    AppSection.ABSENCES -> Icons.Rounded.BeachAccess
    AppSection.PAYSLIPS, AppSection.PAYROLL_ADMIN -> Icons.Rounded.Payments
    AppSection.TIME_OVERVIEW -> Icons.Rounded.AccessTime
    AppSection.TEAM_CALENDAR -> Icons.Rounded.CalendarMonth
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
    AppSection.TIME_OVERVIEW -> "Zeit"
    AppSection.TEAM_CALENDAR -> "Kalender"
    AppSection.PAYROLL_ADMIN -> "Lohn"
    AppSection.SCHEDULE -> "Plan"
    else -> title
}

private fun AppSection.platformCode(): String = when (this) {
    AppSection.TIME_OVERVIEW -> "ZE"
    AppSection.TEAM_CALENDAR -> "KA"
    AppSection.EMPLOYEES, AppSection.USERS -> "PE"
    AppSection.SCHEDULE, AppSection.PRINT_SCHEDULE, AppSection.SHIFT_RULES -> "DI"
    AppSection.PAYROLL_ADMIN, AppSection.PAYSLIPS -> "LO"
    AppSection.ACCOUNTING -> "FI"
    AppSection.BANKING -> "ZA"
    AppSection.CUSTOMERS_PROJECTS, AppSection.CUSTOMERS, AppSection.PROJECTS, AppSection.TASKS -> "PR"
    AppSection.CRM -> "CM"
    AppSection.SUPPLY_CHAIN -> "SC"
    AppSection.KNOWLEDGE -> "KI"
    AppSection.CHRONO_TWO -> "C2"
    AppSection.COMPANY_SETTINGS, AppSection.COMPANY -> "SE"
    AppSection.ANALYTICS, AppSection.PROJECT_REPORT -> "BI"
    else -> title.take(2).uppercase()
}

private fun AppSection.platformLabel(): String = when (this) {
    AppSection.TIME_OVERVIEW -> "Zeitübersicht"
    AppSection.TEAM_CALENDAR -> "Urlaubskalender"
    AppSection.EMPLOYEES, AppSection.USERS -> "Benutzerverwaltung"
    AppSection.SCHEDULE -> "Dienstplan"
    AppSection.PAYROLL_ADMIN, AppSection.PAYSLIPS -> "Abrechnungen"
    AppSection.ACCOUNTING -> "Finanzbuchhaltung"
    AppSection.BANKING -> "Zahlungsverkehr"
    AppSection.CUSTOMERS_PROJECTS -> "Kunden - Projekte - Aufgaben"
    AppSection.CRM -> "CRM & Marketing"
    AppSection.SUPPLY_CHAIN -> "Supply Chain"
    AppSection.KNOWLEDGE -> "Dokumente"
    AppSection.CHRONO_TWO -> "Chrono 2.0"
    AppSection.COMPANY_SETTINGS -> "Firmeneinstellungen"
    AppSection.COMPANY -> "Firmen"
    else -> title
}

private fun AppSection.adminHomeVisible(): Boolean =
    this != AppSection.TIME &&
        this != AppSection.TIME_OVERVIEW &&
        this != AppSection.TEAM_CALENDAR &&
        this != AppSection.ANALYTICS &&
        this != AppSection.WORK_MODEL &&
        this != AppSection.ABSENCES &&
        this != AppSection.PAYSLIPS &&
        this != AppSection.REPORTS &&
        this != AppSection.PROFILE &&
        this != AppSection.SUPPLY_CHAIN &&
        this != AppSection.INFO

private fun AppSection.prefersActionsFirst(): Boolean =
    this in setOf(
        AppSection.SUPPLY_CHAIN,
        AppSection.ADMIN_PASSWORD,
        AppSection.CUSTOMERS_PROJECTS,
        AppSection.CUSTOMERS,
        AppSection.PROJECTS,
        AppSection.TASKS,
        AppSection.TIME_IMPORT,
        AppSection.SHIFT_RULES,
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
    isSubmitting: Boolean,
    onSubmitModuleAction: (ModuleAction, Map<String, String>) -> Unit,
) {
    if (state.isLoading && state.history.isEmpty()) {
        LoadingDataCard()
        return
    }
    val today = state.now.toLocalDate()
    val currentWeekStart = startOfWeek(today)
    val initialWeekStart = weekSummaries.firstOrNull()?.first ?: currentWeekStart
    var visibleWeekStart by remember(initialWeekStart) { mutableStateOf(initialWeekStart) }
    val visibleWeekSummaries = remember(visibleWeekStart, state.history) {
        (0..6).map { offset ->
            val date = visibleWeekStart.plusDays(offset.toLong())
            date to state.history.firstOrNull { it.date == date }
        }
    }

    TrackingSelectionCard(user, state, onSelectCustomer, onSelectProject, onSelectTask)
    TodayTrackingCard(user, todaySummary, state.now, state.isPunching, onPunch)
    TimeQuickStats(user, todaySummary, state.now)
    UserWeekOverview(
        weekSummaries = visibleWeekSummaries,
        now = state.now,
        moduleSummary = state.moduleSummaries[state.selectedSection],
        isSubmitting = isSubmitting,
        canGoNext = visibleWeekStart < currentWeekStart,
        onPreviousWeek = { visibleWeekStart = visibleWeekStart.minusWeeks(1) },
        onCurrentWeek = { visibleWeekStart = currentWeekStart },
        onNextWeek = {
            if (visibleWeekStart < currentWeekStart) {
                visibleWeekStart = visibleWeekStart.plusWeeks(1)
            }
        },
        onSubmit = onSubmitModuleAction,
    )
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
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
                CompactFact("Überstunden", formatSignedMinutes(user.trackingBalanceInMinutes), Modifier.weight(1f))
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
    val quickSections = when {
        AppSection.ADMIN_HOME in sections -> listOfNotNull(
            AppSection.TIME.takeIf { it in sections },
            AppSection.ADMIN_HOME.takeIf { it in sections },
            AppSection.EMPLOYEES.takeIf { it in sections },
            AppSection.SCHEDULE.takeIf { it in sections },
            AppSection.PAYROLL_ADMIN.takeIf { it in sections },
        )
        else -> activeSections
    }.withSelectedFirst(selectedSection)

    CardBox {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionTitle("Arbeitsbereich")
                Text(
                    "${selectedSection.group} / ${selectedSection.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text(
                    "${sections.size}",
                    Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            quickSections.forEach { section ->
                SectionPill(section, selected = section == selectedSection) { onSelectSection(section) }
            }
        }
        if (expanded) {
            otherGroups.forEach { (group, groupSections) ->
                SectionGroupButtons(group, groupSections, selectedSection, onSelectSection)
            }
            SectionGroupButtons(activeGroup, activeSections, selectedSection, onSelectSection)
        }
        if (sections.size > quickSections.size || otherGroups.isNotEmpty()) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Text(if (expanded) "Weniger Bereiche anzeigen" else "Alle Bereiche anzeigen")
            }
        }
    }
}

private fun List<AppSection>.withSelectedFirst(selectedSection: AppSection): List<AppSection> =
    sortedWith(compareBy<AppSection> { it != selectedSection }.thenBy { it.ordinal })

@Composable
private fun SectionPill(section: AppSection, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier.height(38.dp),
            contentPadding = ButtonDefaults.ContentPadding,
        ) {
            Icon(section.navIcon(), contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text(section.title, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.height(38.dp),
            contentPadding = ButtonDefaults.ContentPadding,
        ) {
            Icon(section.navIcon(), contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text(section.title, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

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
    val dailyTargetMinutes = user.dailyWorkHours?.let { (it * 60).toInt() }
    val weeklyTargetMinutes = dailyTargetMinutes?.times(5)
    val targetLabel = if (user.isHourly) "nach Einsatz" else dailyTargetMinutes?.let(::formatMinutes) ?: "-"
    val weekTargetLabel = if (user.isHourly) "nach Einsatz" else weeklyTargetMinutes?.let(::formatMinutes) ?: "-"
    HeroCard("Arbeitsmodell", userModelLabel(user))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard("Modell", userModelLabel(user), Modifier.weight(1f))
        MetricCard("Pensum", if (user.isPercentage) "${user.workPercentage}%" else "100%", Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard("Soll / Tag", targetLabel, Modifier.weight(1f))
        MetricCard("Soll / Woche", weekTargetLabel, Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard("Heute", formatMinutes(workedToday), Modifier.weight(1f))
        MetricCard("Diese Woche", formatMinutes(workedWeek), Modifier.weight(1f))
    }
    MetricCard("Überstunden", formatSignedMinutes(user.trackingBalanceInMinutes), Modifier.fillMaxWidth())
    DetailRowsCard(
        "Stand",
        listOf(
            "Überstunden" to formatSignedMinutes(user.trackingBalanceInMinutes),
            "Stundenlohn" to if (user.isHourly) "Ja" else "Nein",
            "Prozentmodell" to if (user.isPercentage) "Ja" else "Nein",
        ),
    )
    DataSourceStatusCard(summary)
}

@Composable
private fun UserActionCenter(
    title: String,
    summary: ModuleSummary?,
    actionIds: List<String>,
    isSubmitting: Boolean,
    onSubmit: (ModuleAction, Map<String, String>) -> Unit,
    initialValuesFor: (ModuleAction) -> Map<String, String> = { emptyMap() },
) {
    val actions = summary?.actions.orEmpty().filter { it.id in actionIds }
        .sortedBy { actionIds.indexOf(it.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
    if (actions.isEmpty()) return
    var selectedActionId by remember(title, actions.size) { mutableStateOf(actions.first().id) }
    val selectedAction = actions.firstOrNull { it.id == selectedActionId } ?: actions.first()
    CardBox {
        SectionTitle(title)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEach { action ->
                val label = action.userActionLabel()
                if (action.id == selectedAction.id) {
                    Button(
                        onClick = { selectedActionId = action.id },
                        modifier = Modifier.height(38.dp),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                } else {
                    OutlinedButton(
                        onClick = { selectedActionId = action.id },
                        modifier = Modifier.height(38.dp),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        ModuleActionForm(
            action = selectedAction,
            summary = summary,
            isSubmitting = isSubmitting,
            onSubmit = onSubmit,
            initialValues = initialValuesFor(selectedAction),
        )
    }
}

private fun ModuleAction.userActionLabel(): String =
    when (id) {
        "correction-create" -> "Korrektur"
        "vacation-create" -> "Urlaub"
        "sick-report" -> "Krank"
        "user-day-customer" -> "Tag Kunde"
        "user-day-project" -> "Tag Projekt"
        "profile-update" -> "Daten"
        "change-password" -> "Passwort"
        else -> title
    }

@Composable
private fun ProfileScreen(
    user: UserProfile,
    summary: ModuleSummary?,
    isSubmitting: Boolean,
    onSubmitModuleAction: (ModuleAction, Map<String, String>) -> Unit,
) {
    val profile = endpoint(summary, "Profil")
    val profileItem = profile?.items?.firstOrNull()
    HeroCard("Meine Daten", "Profil, Benachrichtigungen, Bankdaten und Passwort.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InlineMetric("Modell", userModelLabel(user), Modifier.weight(1f))
        InlineMetric("Pensum", "${user.workPercentage}%", Modifier.weight(1f))
    }
    DetailRowsCard(
        "Stammdaten",
        listOf(
            "Name" to user.displayName,
            "Benutzername" to user.username,
            "E-Mail" to (profileItem.profileValue("E-Mail", "email") ?: "-"),
            "Telefon" to (profileItem.profileValue("Telefon", "mobilePhone") ?: "-"),
        ),
    )
    DetailRowsCard(
        "Arbeitszeit",
        listOf(
            "Tages-Soll" to (user.dailyWorkHours?.let { "${it.formatHours()} h" } ?: "-"),
            "Überstunden" to formatSignedMinutes(user.trackingBalanceInMinutes),
            "Stundenlohn" to if (user.isHourly) "Ja" else "Nein",
            "Rollen" to user.roles.joinToString(", ").ifBlank { "-" },
        ),
    )
    UserActionCenter(
        title = "Profil verwalten",
        summary = summary,
        actionIds = listOf("profile-update", "change-password"),
        isSubmitting = isSubmitting,
        onSubmit = onSubmitModuleAction,
        initialValuesFor = { action ->
            if (action.id == "profile-update") profileInitialValues(user, profileItem) else emptyMap()
        },
    )
    NativeListCard("Profil-Details", profile, "Keine Profildaten.")
}

private fun ModuleListItem?.profileValue(vararg labels: String): String? =
    this?.detailValue(*labels)?.takeIf { it.isNotBlank() }

private fun profileInitialValues(user: UserProfile, profileItem: ModuleListItem?): Map<String, String> =
    mapOf(
        "firstName" to (profileItem.profileValue("Vorname", "firstName") ?: user.firstName.orEmpty()),
        "lastName" to (profileItem.profileValue("Nachname", "lastName") ?: user.lastName.orEmpty()),
        "email" to (profileItem.profileValue("E-Mail", "email") ?: ""),
        "address" to (profileItem.profileValue("Adresse", "address") ?: ""),
        "mobilePhone" to (profileItem.profileValue("Telefon", "mobilePhone") ?: ""),
        "landlinePhone" to (profileItem.profileValue("Festnetz", "landlinePhone") ?: ""),
        "civilStatus" to (profileItem.profileValue("Zivilstand", "civilStatus") ?: ""),
        "children" to (profileItem.profileValue("Kinder", "children")?.substringBefore(' ')?.toIntOrNull()?.toString() ?: "0"),
        "bankAccount" to (profileItem.profileValue("Bankkonto", "bankAccount") ?: ""),
        "emailNotifications" to profileBooleanValue(profileItem.profileValue("E-Mail Benachrichtigungen", "emailNotifications"), default = true),
    )

private fun profileBooleanValue(value: String?, default: Boolean): String =
    when {
        value == null -> default.toString()
        value.isPositiveValue() -> "true"
        value.equals("false", ignoreCase = true) || value.equals("nein", ignoreCase = true) || value.equals("no", ignoreCase = true) -> "false"
        else -> default.toString()
    }

private fun remainingVacationLabel(endpoint: ModuleEndpointResult?): String {
    val direct = endpoint?.items?.firstOrNull()?.detailValue("Resturlaub", "Verbleibend", "Remaining", "remainingDays")
        ?: endpoint?.preview?.firstOrNull()?.takeIf { it.isNotBlank() }
    val value = direct ?: return "-"
    val number = Regex("-?\\d+(?:[\\.,]\\d+)?").find(value)?.value
    return number?.let { "$it Tage" } ?: value
}

@Composable
private fun AbsenceScreen(
    user: UserProfile,
    summary: ModuleSummary?,
    isSubmitting: Boolean,
    onSubmitModuleAction: (ModuleAction, Map<String, String>) -> Unit,
) {
    val vacation = endpoint(summary, "Urlaub")
    val sickLeave = endpoint(summary, "Krankheit")
    val corrections = endpoint(summary, "Korrekturen")
    val remaining = endpoint(summary, "Resturlaub")
    HeroCard("Urlaub & Abwesenheiten", "Urlaub beantragen, Krankheit melden und Korrekturen verfolgen.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InlineMetric("Resturlaub", remainingVacationLabel(remaining), Modifier.weight(1f))
        InlineMetric("Urlaub", endpointCount(vacation), Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InlineMetric("Krankheit", endpointCount(sickLeave), Modifier.weight(1f))
        InlineMetric("Korrekturen", endpointCount(corrections), Modifier.weight(1f))
    }
    UserActionCenter(
        title = "Anträge",
        summary = summary,
        actionIds = listOf("vacation-create", "sick-report", "correction-create"),
        isSubmitting = isSubmitting,
        onSubmit = onSubmitModuleAction,
    )
    CalendarStripCard("Abwesenheitskalender", listOfNotNull(vacation, sickLeave), "Keine geplanten Abwesenheiten.")
    NativeListCard("Urlaub", vacation, "Keine Urlaubsanträge.")
    NativeListCard("Krankheit", sickLeave, "Keine Krankmeldungen.")
    NativeListCard("Korrekturen", corrections, "Keine Korrekturanträge.")
}

@Composable
private fun PayslipScreen(session: AuthenticatedSession, summary: ModuleSummary?) {
    val user = session.user
    val payslips = endpoint(summary, "Lohnabrechnungen")
    HeroCard("Lohn", "Lohnabrechnungen und Archiv für ${user.displayName}.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard("Abrechnungen", endpointCount(payslips), Modifier.weight(1f))
        MetricCard("Überstunden", formatSignedMinutes(user.trackingBalanceInMinutes), Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard("Modell", userModelLabel(user), Modifier.weight(1f))
        MetricCard("Pensum", if (user.isPercentage) "${user.workPercentage}%" else "100%", Modifier.weight(1f))
    }
    PayrollListCard(
        title = "Meine Lohnabrechnungen",
        endpoint = payslips,
        emptyText = "Noch keine Lohnabrechnungen.",
        session = session,
        pdfPathFor = { item -> "/api/payslips/pdf/${item.id}?lang=de" },
        fileNamePrefix = "payslip",
    )
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
    HeroCard("Zeitbericht", "Monatsbericht, Woche und Überstunden für ${user.displayName}.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard("Überstunden", formatSignedMinutes(user.trackingBalanceInMinutes), Modifier.weight(1f))
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
    NativeListCard("Einkauf", purchases, "Keine Einkaufsaufträge.")
    NativeListCard("Verkauf", sales, "Keine Verkaufsaufträge.")
    NativeListCard("Produktion", production, "Keine Produktionsaufträge.")
    NativeListCard("Service", services, "Keine Servicefälle.")
    NativeListCard("Inventur", counts, "Keine Inventuren.")
    NativeListCard("Audit", audit, "Keine Audit-Einträge.")
}

@Composable
private fun InfoScreen(summary: ModuleSummary?) {
    val latest = endpoint(summary, "Letzte Änderung")
    val changes = endpoint(summary, "Änderungen")
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
    NativeListCard("Letzte Änderung", latest, "Keine aktuelle Änderung.")
    NativeListCard("Änderungen", changes, "Keine Änderungen.")
    NativeListCard("Chat", chat, "Kein Chat-Status.")
}

@Composable
private fun AdminHomeScreen(
    user: UserProfile,
    summary: ModuleSummary?,
    isSubmitting: Boolean,
    onSubmitModuleAction: (ModuleAction, Map<String, String>) -> Unit,
    onSelectSection: (AppSection) -> Unit,
    onContentJump: () -> Unit,
) {
    val adminSections = AppSection.visibleFor(user).filter { it.adminHomeVisible() }
    val time = endpoint(summary, "Zeitübersicht")
    val employees = endpoint(summary, "Mitarbeiter")
    val balances = endpoint(summary, "Zeitkonten")
    val weekly = endpoint(summary, "Wochensaldo")
    val corrections = endpoint(summary, "Korrekturen")
    val vacation = endpoint(summary, "Urlaub")
    val sick = endpoint(summary, "Krankheit")
    val payroll = endpoint(summary, "Abrechnungen offen")
    var activeTab by remember { mutableStateOf("overview") }

    if (activeTab != "overview") {
        OutlinedButton(
            onClick = {
                activeTab = "overview"
                onContentJump()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
        ) {
            Text("Zur Übersicht")
        }
    }
    when (activeTab) {
        "time" -> {
            TeamTimeOverviewCard(
                time = time,
                employees = employees,
                balances = balances,
                weekly = weekly,
                summary = summary,
                isSubmitting = isSubmitting,
                onSubmit = onSubmitModuleAction,
            )
        }
        "requests" -> {
            AdminRequestsWorkScreen(
                corrections = corrections,
                vacation = vacation,
                sick = sick,
                payroll = payroll,
                summary = summary,
                isSubmitting = isSubmitting,
                onSubmit = onSubmitModuleAction,
                onOpenPayroll = { onSelectSection(AppSection.PAYROLL_ADMIN) },
            )
        }
        "calendar" -> {
            TeamCalendarContent(
                summary = summary,
                vacation = vacation,
                sick = sick,
                isSubmitting = isSubmitting,
                onSubmitModuleAction = onSubmitModuleAction,
            )
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
                weekly = weekly,
                onOpenTime = {
                    activeTab = "time"
                    onContentJump()
                },
                onOpenRequests = {
                    activeTab = "requests"
                    onContentJump()
                },
                onOpenCalendar = {
                    activeTab = "calendar"
                    onContentJump()
                },
                onOpenModules = {
                    activeTab = "modules"
                    onContentJump()
                },
            )
        }
    }
}

@Composable
private fun TimeOverviewScreen(
    summary: ModuleSummary?,
    isSubmitting: Boolean,
    onSubmitModuleAction: (ModuleAction, Map<String, String>) -> Unit,
) {
    val time = endpoint(summary, "Zeitübersicht")
    val employees = endpoint(summary, "Mitarbeiter")
    val balances = endpoint(summary, "Zeitkonten")
    val weekly = endpoint(summary, "Wochensaldo")
    TeamTimeOverviewCard(
        time = time,
        employees = employees,
        balances = balances,
        weekly = weekly,
        summary = summary,
        isSubmitting = isSubmitting,
        onSubmit = onSubmitModuleAction,
    )
}

@Composable
private fun TeamCalendarScreen(
    summary: ModuleSummary?,
    isSubmitting: Boolean,
    onSubmitModuleAction: (ModuleAction, Map<String, String>) -> Unit,
) {
    TeamCalendarContent(
        summary = summary,
        vacation = endpoint(summary, "Urlaub"),
        sick = endpoint(summary, "Krankheit"),
        isSubmitting = isSubmitting,
        onSubmitModuleAction = onSubmitModuleAction,
    )
}

@Composable
private fun TeamCalendarContent(
    summary: ModuleSummary?,
    vacation: ModuleEndpointResult?,
    sick: ModuleEndpointResult?,
    isSubmitting: Boolean,
    onSubmitModuleAction: (ModuleAction, Map<String, String>) -> Unit,
) {
    val today = LocalDate.now()
    val actions = summary?.actions.orEmpty().associateBy { it.id }
    var monthStart by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var selectedDay by remember { mutableStateOf(today) }
    var kindFilter by remember { mutableStateOf("Alle") }
    var showDenied by remember { mutableStateOf(false) }
    var openActionId by remember { mutableStateOf<String?>(null) }
    val monthEnd = monthStart.plusMonths(1).minusDays(1)
    val allItems = remember(vacation, sick) { buildTeamAbsenceItems(vacation, sick) }
    val filteredItems = allItems
        .filter { kindFilter == "Alle" || it.kind == kindFilter }
        .filter { showDenied || !it.denied }
    val monthItems = filteredItems
        .filter { !it.end.isBefore(monthStart) && !it.start.isAfter(monthEnd) }
        .sortedWith(compareBy<TeamAbsenceItem> { it.start }.thenBy { it.employee })
    val selectedItems = filteredItems
        .filter { it.occursOn(selectedDay) }
        .sortedWith(compareBy<TeamAbsenceItem> { it.kind }.thenBy { it.employee })
    val gridStart = startOfWeek(monthStart)
    val gridDays = (0 until 42).map { gridStart.plusDays(it.toLong()) }
    val monthDaysWithAbsence = (0 until monthEnd.dayOfMonth)
        .map { monthStart.plusDays(it.toLong()) }
        .count { day -> monthItems.any { it.occursOn(day) } }
    val vacationCount = monthItems.count { it.kind == "Urlaub" }
    val sickCount = monthItems.count { it.kind == "Krankheit" }

    CardBox {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("TEAMKALENDER", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                SectionTitle("Urlaubskalender")
                Text(monthTitle(monthStart), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Text("${monthItems.size} Einträge", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val target = monthStart.minusMonths(1)
                    monthStart = target
                    selectedDay = target
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text("< Monat", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Button(
                onClick = {
                    monthStart = today.withDayOfMonth(1)
                    selectedDay = today
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text("Heute", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = {
                    val target = monthStart.plusMonths(1)
                    monthStart = target
                    selectedDay = target
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text("Monat >", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdminWorkMetric("Urlaub", vacationCount.toString(), Modifier.weight(1f))
            AdminWorkMetric("Krankheit", sickCount.toString(), Modifier.weight(1f))
            AdminWorkMetric("Tage", monthDaysWithAbsence.toString(), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalendarFilterButton("Alle", kindFilter == "Alle", Modifier.weight(1f)) { kindFilter = "Alle" }
            CalendarFilterButton("Urlaub", kindFilter == "Urlaub", Modifier.weight(1f)) { kindFilter = "Urlaub" }
            CalendarFilterButton("Krank", kindFilter == "Krankheit", Modifier.weight(1f)) { kindFilter = "Krankheit" }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { openActionId = "admin-vacation-create" },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text("Urlaub eintragen", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = { openActionId = "admin-sick-create" },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text("Krankheit melden", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        openActionId?.let { actionId ->
            actions[actionId]?.let { action ->
                CalendarActionPanel(
                    action = action,
                    summary = summary,
                    selectedDay = selectedDay,
                    isSubmitting = isSubmitting,
                    onSubmit = onSubmitModuleAction,
                    onClose = { openActionId = null },
                )
            } ?: Text("Aktion ist noch nicht geladen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(
            onClick = { showDenied = !showDenied },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(999.dp),
        ) {
            Text(if (showDenied) "Abgelehnte ausblenden" else "Abgelehnte anzeigen")
        }
        when {
            vacation == null || sick == null || vacation.isLoadingLike() || sick.isLoadingLike() -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Kalender wird geladen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            vacation.status == ModuleRequestStatus.FORBIDDEN || sick.status == ModuleRequestStatus.FORBIDDEN -> {
                Text("Keine Berechtigung für den Teamkalender.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            vacation.status == ModuleRequestStatus.ERROR || sick.status == ModuleRequestStatus.ERROR -> {
                Text(vacation.message ?: sick.message ?: "Kalender konnte nicht geladen werden.", color = MaterialTheme.colorScheme.error)
            }
            allItems.isEmpty() -> {
                Text("Keine Abwesenheiten vorhanden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                CalendarMonthGrid(
                    days = gridDays,
                    monthStart = monthStart,
                    selectedDay = selectedDay,
                    items = filteredItems,
                    onSelectDay = { selectedDay = it },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                CalendarSelectedDayList(selectedDay = selectedDay, items = selectedItems)
                if (selectedItems.isEmpty() && monthItems.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text("Nächste Abwesenheiten", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    monthItems.take(5).forEach { CalendarAbsenceRow(it) }
                }
            }
        }
    }
}

@Composable
private fun CalendarFilterButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(999.dp), contentPadding = ButtonDefaults.ContentPadding) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(999.dp), contentPadding = ButtonDefaults.ContentPadding) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CalendarActionPanel(
    action: ModuleAction,
    summary: ModuleSummary?,
    selectedDay: LocalDate,
    isSubmitting: Boolean,
    onSubmit: (ModuleAction, Map<String, String>) -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(action.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Datum: ${selectedDay.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onClose, enabled = !isSubmitting) {
                    Text("Schließen")
                }
            }
            ModuleActionForm(
                action = action,
                summary = summary,
                isSubmitting = isSubmitting,
                onSubmit = onSubmit,
                initialValues = mapOf(
                    "startDate" to selectedDay.toString(),
                    "endDate" to selectedDay.toString(),
                ),
            )
        }
    }
}

@Composable
private fun CalendarMonthGrid(
    days: List<LocalDate>,
    monthStart: LocalDate,
    selectedDay: LocalDate,
    items: List<TeamAbsenceItem>,
    onSelectDay: (LocalDate) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach { day ->
            Text(day, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
    days.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            week.forEach { day ->
                CalendarDayCell(
                    day = day,
                    inMonth = day.year == monthStart.year && day.monthValue == monthStart.monthValue,
                    selected = day == selectedDay,
                    items = items.filter { it.occursOn(day) },
                    onClick = { onSelectDay(day) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    items: List<TeamAbsenceItem>,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val hasVacation = items.any { it.kind == "Urlaub" }
    val hasSick = items.any { it.kind == "Krankheit" }
    val background = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        items.isNotEmpty() -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.background
    }
    Surface(
        color = background,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        modifier = modifier.height(62.dp).clickable(onClick = onClick),
    ) {
        Column(
            Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                day.dayOfMonth.toString(),
                color = if (inMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (hasVacation) Box(Modifier.size(6.dp).background(Color(0xFF60A5FA), CircleShape))
                if (hasSick) Box(Modifier.size(6.dp).background(Color(0xFFF87171), CircleShape))
            }
            Text(
                if (items.isNotEmpty()) items.size.toString() else "",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun CalendarSelectedDayList(selectedDay: LocalDate, items: List<TeamAbsenceItem>) {
    Text(
        "${dayShortLabel(selectedDay)} ${selectedDay.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
    if (items.isEmpty()) {
        Text("An diesem Tag ist niemand abwesend.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        items.forEach { CalendarAbsenceRow(it) }
    }
}

@Composable
private fun CalendarAbsenceRow(item: TeamAbsenceItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(width = 4.dp, height = 54.dp).background(item.color, RoundedCornerShape(999.dp)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.employee, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.kind, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Text(item.periodLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            item.note?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        StatusPill(item.status)
    }
}

private data class TeamAbsenceItem(
    val kind: String,
    val employee: String,
    val start: LocalDate,
    val end: LocalDate,
    val denied: Boolean,
    val note: String?,
    val color: Color,
    val status: ItemStatusChip,
)

private fun buildTeamAbsenceItems(vacation: ModuleEndpointResult?, sick: ModuleEndpointResult?): List<TeamAbsenceItem> =
    vacation?.items.orEmpty().mapNotNull { it.toTeamAbsenceItem("Urlaub", Color(0xFF60A5FA)) } +
        sick?.items.orEmpty().mapNotNull { it.toTeamAbsenceItem("Krankheit", Color(0xFFF87171)) }

private fun ModuleListItem.toTeamAbsenceItem(kind: String, color: Color): TeamAbsenceItem? {
    val range = dateRange()
    val start = range.first ?: return null
    val end = range.second ?: start
    val statusValue = detailValue("Status").orEmpty()
    val denied = detailBool("Abgelehnt") ||
        statusValue.contains("abgelehnt", ignoreCase = true) ||
        statusValue.contains("denied", ignoreCase = true)
    val approved = detailBool("Genehmigt") ||
        statusValue.contains("genehmigt", ignoreCase = true) ||
        statusValue.contains("approved", ignoreCase = true)
    val status = when {
        denied -> ItemStatusChip("Abgelehnt", Color(0xFF4A1F25), Color(0xFFFCA5A5))
        isPendingApprovalItem() -> ItemStatusChip("Offen", Color(0xFF26315F), Color(0xFFC7D2FE))
        approved -> ItemStatusChip("Genehmigt", Color(0xFF123D2F), Color(0xFF34D399))
        else -> statusChip() ?: ItemStatusChip("Geplant", Color(0xFF26315F), Color(0xFFC7D2FE))
    }
    val employee = detailValue("Benutzer", "Mitarbeiter", "Username", "Name")
        ?: label.substringBefore(" - ").takeIf { it.isNotBlank() }
        ?: label
    val note = detailValue("Grund", "Kommentar", "Reason", "Comment")
        ?: detail?.takeIf { it.isNotBlank() && !it.contains(employee, ignoreCase = true) }
    return TeamAbsenceItem(
        kind = kind,
        employee = employee,
        start = start,
        end = end,
        denied = denied,
        note = note,
        color = color,
        status = status,
    )
}

private fun TeamAbsenceItem.occursOn(day: LocalDate): Boolean =
    !day.isBefore(start) && !day.isAfter(end)

private fun TeamAbsenceItem.periodLabel(): String =
    if (start == end) start.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    else "${start.format(DateTimeFormatter.ofPattern("dd.MM."))} - ${end.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}"

private fun monthTitle(date: LocalDate): String {
    val names = listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")
    return "${names[date.monthValue - 1]} ${date.year}"
}

@Composable
private fun SuperAdminScreen(summary: ModuleSummary?, onSelectSection: (AppSection) -> Unit) {
    val feedback = endpoint(summary, "App Feedback")
    val companies = endpoint(summary, "Firmen")
    val analytics = endpoint(summary, "Analytics")
    val excludedIps = endpoint(summary, "Ausgeschlossene IPs")
    val changes = endpoint(summary, "Änderungen")
    var activeTab by remember { mutableStateOf("overview") }

    HeroCard("Superadmin Dashboard", "Mandanten, App-Feedback, Analytics und Systemstatus.")
    if (activeTab != "overview") {
        OutlinedButton(
            onClick = { activeTab = "overview" },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
        ) {
            Text("Zur Übersicht")
        }
    }
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
            NativeListCard("Änderungen", changes, "Keine Änderungen.")
        }
        else -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Feedback", endpointCount(feedback), Modifier.weight(1f))
                MetricCard("Firmen", endpointCount(companies), Modifier.weight(1f))
            }
            CardBox {
                SectionTitle("Was jetzt wichtig ist")
                DashboardActionButton("App Feedback prüfen") { activeTab = "feedback" }
                DashboardActionButton("Firmen verwalten") { onSelectSection(AppSection.COMPANY) }
                DashboardActionButton("Analytics ansehen") { activeTab = "analytics" }
                DashboardActionButton("Admin Dashboard öffnen") { onSelectSection(AppSection.ADMIN_HOME) }
            }
            NativeListCard("Neues App Feedback", feedback, "Noch kein App-Feedback.")
            NativeListCard("Firmen", companies, "Keine Firmen.")
        }
    }
}

@Composable
private fun EmployeesScreen(
    summary: ModuleSummary?,
    isSubmitting: Boolean,
    onSubmitModuleAction: (ModuleAction, Map<String, String>) -> Unit,
) {
    UserManagementScreen(
        title = "Benutzerverwaltung",
        subtitle = "Mitarbeitende verwalten, Abwesenheiten erfassen und Zeitübersicht steuern.",
        summary = summary,
        isSubmitting = isSubmitting,
        showAudit = false,
        onSubmitModuleAction = onSubmitModuleAction,
    )
}

@Composable
private fun UsersScreen(
    summary: ModuleSummary?,
    isSubmitting: Boolean,
    onSubmitModuleAction: (ModuleAction, Map<String, String>) -> Unit,
) {
    UserManagementScreen(
        title = "Benutzerverwaltung",
        subtitle = "Benutzer, Rollen, Zeitkonten und Audit übersichtlich bearbeiten.",
        summary = summary,
        isSubmitting = isSubmitting,
        showAudit = true,
        onSubmitModuleAction = onSubmitModuleAction,
    )
}

@Composable
private fun UserManagementScreen(
    title: String,
    subtitle: String,
    summary: ModuleSummary?,
    isSubmitting: Boolean,
    showAudit: Boolean,
    onSubmitModuleAction: (ModuleAction, Map<String, String>) -> Unit,
) {
    val usersEndpoint = endpoint(summary, "Mitarbeiter") ?: endpoint(summary, "Benutzer")
    val balances = endpoint(summary, "Zeitkonten")
    val weekly = endpoint(summary, "Wochensaldo")
    val audit = endpoint(summary, "Audit")
    val actions = summary?.actions.orEmpty().associateBy { it.id }
    val users = usersEndpoint?.items.orEmpty()
    val balanceByUsername = balances?.items.orEmpty()
        .mapNotNull { item -> item.usernameValue()?.let { it to item.detailInt("Saldo Minuten", "Tracking Balance", "trackingBalance") } }
        .toMap()
    val weeklyByUsername = weekly?.items.orEmpty()
        .mapNotNull { item -> item.usernameValue()?.let { it to item.detailInt("Wochensaldo Minuten", "Weekly Balance", "weeklyBalance") } }
        .toMap()
    var query by remember(usersEndpoint?.count) { mutableStateOf("") }
    var onlyTimeVisible by remember(usersEndpoint?.count) { mutableStateOf(false) }
    var selectedUserId by remember(usersEndpoint?.count) { mutableStateOf<String?>(null) }
    var openActionId by remember { mutableStateOf<String?>(null) }
    var pendingSubmit by remember { mutableStateOf<AdminWorkSubmission?>(null) }
    val filteredUsers = users
        .filter { item -> query.isBlank() || item.searchText().contains(query, ignoreCase = true) }
        .filter { item -> !onlyTimeVisible || item.isInTimeOverview() }
        .sortedWith(compareBy<ModuleListItem> { !it.isInTimeOverview() }.thenBy { it.employeeDisplayName().lowercase() })

    pendingSubmit?.let { submission ->
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) pendingSubmit = null },
            title = { Text(submission.action.title) },
            text = { Text("${submission.item.employeeDisplayName()} wirklich ${submission.action.directActionVerb()}?") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingSubmit = null
                        onSubmitModuleAction(submission.action, submission.values)
                    },
                    enabled = !isSubmitting,
                ) {
                    Text(if (isSubmitting) "Wird gespeichert" else "Bestätigen")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSubmit = null }, enabled = !isSubmitting) {
                    Text("Abbrechen")
                }
            },
        )
    }

    CardBox {
        Text("ADMIN", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        SectionTitle(title)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdminWorkMetric("Benutzer", endpointCount(usersEndpoint), Modifier.weight(1f))
            AdminWorkMetric("Sichtbar", users.count { it.isInTimeOverview() }.toString(), Modifier.weight(1f))
            AdminWorkMetric("Zeitkonten", endpointCount(balances), Modifier.weight(1f))
        }
        Button(
            onClick = { openActionId = "user-create" },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(999.dp),
            contentPadding = ButtonDefaults.ContentPadding,
        ) {
            Text("Mitarbeiter anlegen", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        openActionId?.let { actionId ->
            actions[actionId]?.let { action ->
                UserManagementActionPanel(
                    action = action,
                    summary = summary,
                    isSubmitting = isSubmitting,
                    onSubmit = onSubmitModuleAction,
                    onClose = { openActionId = null },
                )
            } ?: Text("Aktion ist noch nicht geladen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    CardBox {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Mitarbeiter")
            Text("${filteredUsers.size} von ${users.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Benutzer suchen") },
            singleLine = true,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onlyTimeVisible) {
                Button(onClick = { onlyTimeVisible = false }, modifier = Modifier.weight(1f), contentPadding = ButtonDefaults.ContentPadding) {
                    Text("Zeitübersicht aktiv", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                OutlinedButton(onClick = { onlyTimeVisible = true }, modifier = Modifier.weight(1f), contentPadding = ButtonDefaults.ContentPadding) {
                    Text("Nur Zeitübersicht", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            OutlinedButton(onClick = { query = "" }, modifier = Modifier.weight(1f), contentPadding = ButtonDefaults.ContentPadding) {
                Text("Suche löschen", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when {
            usersEndpoint == null || usersEndpoint.isLoadingLike() -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Benutzer werden geladen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            usersEndpoint.status == ModuleRequestStatus.FORBIDDEN -> Text("Keine Berechtigung für die Benutzerverwaltung.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            usersEndpoint.status == ModuleRequestStatus.ERROR -> Text(usersEndpoint.message ?: "Benutzer konnten nicht geladen werden.", color = MaterialTheme.colorScheme.error)
            users.isEmpty() -> Text("Keine Benutzer vorhanden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            filteredUsers.isEmpty() -> Text("Keine Treffer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                filteredUsers.take(40).forEach { user ->
                    val username = user.usernameValue().orEmpty()
                    UserManagementRow(
                        user = user,
                        selected = selectedUserId == user.id,
                        totalBalanceMinutes = balanceByUsername[username],
                        weekBalanceMinutes = weeklyByUsername[username],
                        actions = actions,
                        isSubmitting = isSubmitting,
                        onSelect = {
                            selectedUserId = if (selectedUserId == user.id) null else user.id
                        },
                        onDirectSubmit = { submission -> pendingSubmit = submission },
                    )
                }
                if (filteredUsers.size > 40) {
                    Text("${filteredUsers.size - 40} weitere Treffer. Bitte Suche verfeinern.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showAudit) {
        NativeListCard("Audit", audit, "Keine Audit-Einträge.")
    }
}

@Composable
private fun UserManagementActionPanel(
    action: ModuleAction,
    summary: ModuleSummary?,
    isSubmitting: Boolean,
    onSubmit: (ModuleAction, Map<String, String>) -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(action.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onClose, enabled = !isSubmitting) {
                    Text("Schließen")
                }
            }
            ModuleActionForm(
                action = action,
                summary = summary,
                isSubmitting = isSubmitting,
                onSubmit = onSubmit,
            )
        }
    }
}

@Composable
private fun UserManagementRow(
    user: ModuleListItem,
    selected: Boolean,
    totalBalanceMinutes: Int?,
    weekBalanceMinutes: Int?,
    actions: Map<String, ModuleAction>,
    isSubmitting: Boolean,
    onSelect: () -> Unit,
    onDirectSubmit: (AdminWorkSubmission) -> Unit,
) {
    val username = user.usernameValue().orEmpty()
    val visible = user.isInTimeOverview()
    val status = if (visible) ItemStatusChip("Zeitübersicht", Color(0xFF123D2F), Color(0xFF34D399)) else ItemStatusChip("Ausgeblendet", Color(0xFF252830), Color(0xFFA0A4B4))
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onSelect), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(user.employeeDisplayName(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(username.ifBlank { "#${user.id}" }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                StatusPill(status)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactFact("Saldo", totalBalanceMinutes?.let(::formatSignedHumanMinutes) ?: "-", Modifier.weight(1f))
                CompactFact("Woche", weekBalanceMinutes?.let(::formatSignedHumanMinutes) ?: "-", Modifier.weight(1f))
                CompactFact("Land", user.detailValue("Land", "Country", "country") ?: "-", Modifier.weight(1f))
            }
            if (selected) {
                UserManagementActions(
                    user = user,
                    visible = visible,
                    actions = actions,
                    isSubmitting = isSubmitting,
                    onDirectSubmit = onDirectSubmit,
                )
                ModuleItemDetails(user)
            } else {
                Text("Antippen für Details und Aktionen", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun UserManagementActions(
    user: ModuleListItem,
    visible: Boolean,
    actions: Map<String, ModuleAction>,
    isSubmitting: Boolean,
    onDirectSubmit: (AdminWorkSubmission) -> Unit,
) {
    val visibilityAction = actions["user-visibility-update"]
    val deleteAction = actions["user-delete"]
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        visibilityAction?.let { action ->
            Button(
                onClick = {
                    onDirectSubmit(
                        AdminWorkSubmission(
                            action = action,
                            item = user,
                            values = mapOf("id" to user.id, "includeInTimeTracking" to (!visible).toString()),
                        ),
                    )
                },
                enabled = !isSubmitting,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(6.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text(if (visible) "Ausblenden" else "Einblenden", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        deleteAction?.let { action ->
            OutlinedButton(
                onClick = { onDirectSubmit(AdminWorkSubmission(action, user, mapOf("id" to user.id))) },
                enabled = !isSubmitting,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(6.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text("Löschen", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AdminPasswordScreen(summary: ModuleSummary?) {
    HeroCard("Admin-Passwort", "Eigenes Admin-Passwort sicher ändern.")
    DetailRowsCard("Sicherheit", listOf("Aktion" to "Passwort ändern", "Kontext" to "Admin-Konto"))
    DataSourceStatusCard(summary)
}

@Composable
private fun CustomersProjectsScreen(user: UserProfile, summary: ModuleSummary?) {
    val customers = endpoint(summary, "Kunden")
    val projects = endpoint(summary, "Projekte")
    val hierarchy = endpoint(summary, "Projektbaum")
    HeroCard("Kunden & Projekte", "App-Arbeitsfläche für Kunden, Projekte und Auswertungen.")
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
    NativeListCard("Audit", audit, "Keine Audit-Einträge.")
}

@Composable
private fun AnalyticsScreen(summary: ModuleSummary?) {
    val time = endpoint(summary, "Zeitübersicht")
    val projects = endpoint(summary, "Projektanalyse")
    val audit = endpoint(summary, "Audit")
    HeroCard("Analytics", "Zeit, Projekte, Reports und Audit-Auswertungen.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Zeit", endpointCount(time), Modifier.weight(1f))
        MetricCard("Projekte", endpointCount(projects), Modifier.weight(1f))
    }
    NativeListCard("Zeitübersicht", time, "Keine Zeitdaten.")
    NativeListCard("Projektanalyse", projects, "Keine Projektdaten.")
    NativeListCard("Audit", audit, "Keine Audit-Einträge.")
}

@Composable
private fun TimeImportScreen(summary: ModuleSummary?) {
    val time = endpoint(summary, "Zeitübersicht")
    val balances = endpoint(summary, "Zeitkonten")
    val audit = endpoint(summary, "Audit")
    HeroCard("Zeitimport", "Zeitdaten importieren und Salden prüfen.")
    NativeListCard("Zeitübersicht", time, "Keine Zeitdaten.")
    NativeListCard("Zeitkonten", balances, "Keine Zeitkonten.")
    NativeListCard("Audit", audit, "Keine Audit-Einträge.")
}

@Composable
private fun ScheduleScreen(
    summary: ModuleSummary?,
    isSubmitting: Boolean,
    onSubmitModuleAction: (ModuleAction, Map<String, String>) -> Unit,
) {
    val context = LocalContext.current
    val entriesEndpoint = endpoint(summary, "Dienstplan")
    val usersEndpoint = endpoint(summary, "Mitarbeiter")
    val shiftsEndpoint = endpoint(summary, "Schichten")
    val rules = endpoint(summary, "Planregeln")
    val expected = endpoint(summary, "Sollzeit")
    val holidays = endpoint(summary, "Feiertagsoptionen")
    val actions = summary?.actions.orEmpty().associateBy { it.id }
    val currentWeekStart = startOfWeek(LocalDate.now())
    var weekStart by remember { mutableStateOf(currentWeekStart) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedShiftKey by remember { mutableStateOf<String?>(null) }
    var selectedEntryId by remember(entriesEndpoint?.count) { mutableStateOf<String?>(null) }
    var formActionId by remember { mutableStateOf<String?>(null) }
    var query by remember(usersEndpoint?.count) { mutableStateOf("") }
    var showWeekends by remember { mutableStateOf(true) }
    var pendingDelete by remember { mutableStateOf<AdminWorkSubmission?>(null) }
    var pendingAutofill by remember { mutableStateOf<ScheduleAutofillPlan?>(null) }
    var pendingWeekCopy by remember { mutableStateOf<ScheduleAutofillPlan?>(null) }
    val weekEnd = weekStart.plusDays(6)
    val employeesById = usersEndpoint?.items.orEmpty().associateBy { it.id }
    val shifts = buildScheduleShiftDefs(shiftsEndpoint, entriesEndpoint)
    val activeShifts = shifts.filter { it.active }.ifEmpty { shifts }
    val scheduleEntries = entriesEndpoint?.items.orEmpty()
        .mapNotNull { item -> item.toSchedulePlanEntry(employeesById) }
        .filter { !it.date.isBefore(weekStart) && !it.date.isAfter(weekEnd) }
        .filter { query.isBlank() || it.searchText.contains(query, ignoreCase = true) }
    val visibleDays = (0 until 7)
        .map { weekStart.plusDays(it.toLong()) }
        .filter { showWeekends || it.dayOfWeek.value <= 5 }
    val selectedEntry = scheduleEntries.firstOrNull { it.id == selectedEntryId }
    val formAction = formActionId?.let { actions[it] }

    pendingDelete?.let { submission ->
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) pendingDelete = null },
            title = { Text("Schicht löschen") },
            text = { Text("${submission.item.label} wirklich aus dem Dienstplan löschen?") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDelete = null
                        onSubmitModuleAction(submission.action, submission.values)
                    },
                    enabled = !isSubmitting,
                ) { Text(if (isSubmitting) "Wird gelöscht" else "Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }, enabled = !isSubmitting) { Text("Abbrechen") }
            },
        )
    }
    pendingAutofill?.let { plan ->
        val action = actions["schedule-autofill"]
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) pendingAutofill = null },
            title = { Text("Autofill ausführen?") },
            text = {
                Text("Es werden automatisch ${plan.count} freie Schichten in dieser Woche besetzt. Wirklich fortfahren?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingAutofill = null
                        if (action != null) {
                            onSubmitModuleAction(action, mapOf("entries" to plan.json))
                        }
                    },
                    enabled = !isSubmitting && action != null,
                ) {
                    Text(if (isSubmitting) "Wird gespeichert" else "Autofill starten")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAutofill = null }, enabled = !isSubmitting) {
                    Text("Abbrechen")
                }
            },
        )
    }
    pendingWeekCopy?.let { plan ->
        val action = actions["schedule-copy"]
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) pendingWeekCopy = null },
            title = { Text("Woche kopieren?") },
            text = { Text("${plan.count} Schichten werden in die nächste Woche kopiert. Wirklich fortfahren?") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingWeekCopy = null
                        if (action != null) {
                            onSubmitModuleAction(action, mapOf("entries" to plan.json))
                        }
                    },
                    enabled = !isSubmitting && action != null,
                ) {
                    Text(if (isSubmitting) "Wird kopiert" else "Kopieren")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingWeekCopy = null }, enabled = !isSubmitting) {
                    Text("Abbrechen")
                }
            },
        )
    }

    CardBox {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("DIENSTPLAN", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                SectionTitle("Wochenplanung")
                Text(
                    "${weekStart.format(DateTimeFormatter.ofPattern("dd.MM."))} - ${weekEnd.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("${scheduleEntries.size} Schichten", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val target = weekStart.minusDays(7)
                    weekStart = target
                    selectedDate = target
                    selectedEntryId = null
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) { Text("< Woche", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Button(
                onClick = {
                    weekStart = currentWeekStart
                    selectedDate = LocalDate.now()
                    selectedEntryId = null
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) { Text("Heute", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            OutlinedButton(
                onClick = {
                    val target = weekStart.plusDays(7)
                    weekStart = target
                    selectedDate = target
                    selectedEntryId = null
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) { Text("Woche >", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdminWorkMetric("Mitarbeiter", usersEndpoint?.items.orEmpty().size.toString(), Modifier.weight(1f))
            AdminWorkMetric("Schichten", activeShifts.size.toString(), Modifier.weight(1f))
            AdminWorkMetric("Regeln", endpointCount(rules), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    formActionId = "schedule-entry-create"
                    selectedEntryId = null
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) { Text("Eintragen", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            OutlinedButton(
                onClick = {
                    printSchedule(
                        context = context,
                        title = "Dienstplan ${weekStart}",
                        html = buildSchedulePrintHtml(weekStart, visibleDays, activeShifts, scheduleEntries),
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) { Text("Drucken", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val plan = buildScheduleAutofillPlan(
                        weekStart = weekStart,
                        shifts = activeShifts,
                        employees = usersEndpoint?.items.orEmpty(),
                        entries = scheduleEntries,
                    )
                    if (plan.count == 0) {
                        Toast.makeText(context, "Keine freien Schichten zum Auffüllen gefunden.", Toast.LENGTH_SHORT).show()
                    } else {
                        pendingAutofill = plan
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) { Text("Autofill", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            OutlinedButton(
                onClick = {
                    if (scheduleEntries.isEmpty()) {
                        Toast.makeText(context, "Diese Woche ist leer und kann nicht kopiert werden.", Toast.LENGTH_SHORT).show()
                    } else {
                        pendingWeekCopy = ScheduleAutofillPlan(
                            json = buildScheduleWeekJson(scheduleEntries, weekStart.plusDays(7)),
                            count = scheduleEntries.size,
                        )
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) { Text("Woche kopieren", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                label = { Text("Mitarbeiter suchen") },
                singleLine = true,
            )
            OutlinedButton(
                onClick = { showWeekends = !showWeekends },
                modifier = Modifier.width(112.dp).height(56.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) { Text(if (showWeekends) "Mo-Fr" else "7 Tage", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        formAction?.let { action ->
            ScheduleActionPanel(
                action = action,
                summary = summary,
                initialValues = scheduleInitialValues(
                    action = action,
                    selectedDate = selectedDate,
                    selectedShiftKey = selectedShiftKey,
                    selectedEntry = selectedEntry,
                    scheduleEntries = scheduleEntries,
                    targetWeekStart = weekStart.plusDays(7),
                ),
                isSubmitting = isSubmitting,
                onSubmit = onSubmitModuleAction,
                onClose = { formActionId = null },
            )
        }
        when {
            entriesEndpoint == null || entriesEndpoint.isLoadingLike() -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Dienstplan wird geladen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            entriesEndpoint.status == ModuleRequestStatus.FORBIDDEN -> Text("Keine Berechtigung für den Dienstplan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            entriesEndpoint.status == ModuleRequestStatus.ERROR -> Text(entriesEndpoint.message ?: "Dienstplan konnte nicht geladen werden.", color = MaterialTheme.colorScheme.error)
            else -> {
                visibleDays.forEach { day ->
                    ScheduleDayCard(
                        date = day,
                        shifts = activeShifts,
                        entries = scheduleEntries.filter { it.date == day },
                        selectedEntryId = selectedEntryId,
                        onSelectEmpty = { shiftKey ->
                            selectedDate = day
                            selectedShiftKey = shiftKey
                            selectedEntryId = null
                            formActionId = "schedule-entry-create"
                        },
                        onSelectEntry = { entry ->
                            selectedDate = entry.date
                            selectedShiftKey = entry.shiftKey
                            selectedEntryId = if (selectedEntryId == entry.id) null else entry.id
                        },
                    )
                }
                if (scheduleEntries.isEmpty()) {
                    Text("Keine Schichten in dieser Woche.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    selectedEntry?.let { entry ->
        ScheduleEntryDetails(
            entry = entry,
            rawItem = entriesEndpoint?.items.orEmpty().firstOrNull { it.id == entry.id },
            editAction = actions["schedule-entry-update"],
            deleteAction = actions["schedule-entry-delete"],
            isSubmitting = isSubmitting,
            onEdit = { formActionId = "schedule-entry-update" },
            onDelete = { action, item ->
                pendingDelete = AdminWorkSubmission(action, item, mapOf("id" to item.id))
            },
        )
    }

    CardBox {
        SectionTitle("Schichtarten")
        if (activeShifts.isEmpty()) {
            Text("Keine Schichtdefinitionen vorhanden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            activeShifts.forEach { shift ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(shift.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(shift.key, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(shift.timeLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    NativeListCard("Planregeln", rules, "Keine Planregeln.")
    NativeListCard("Sollzeit heute", expected, "Keine Sollzeitdaten.")
    NativeListCard("Feiertagsoptionen", holidays, "Keine Feiertagsoptionen.")
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
        MetricCard("Einträge", endpointCount(entries), Modifier.weight(1f))
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
    HeroCard("Plan drucken", "Dienstpläne für Woche und Team vorbereiten.")
    CalendarStripCard("Druckvorschau", listOfNotNull(entries), "Keine geplanten Schichten.", days = 7, start = startOfWeek(LocalDate.now()))
    NativeListCard("Dienstplan", entries, "Keine Schichten in dieser Woche.")
    NativeListCard("Mitarbeiter", users, "Keine Mitarbeiter.")
    NativeListCard("Schichten", shifts, "Keine Schichtdefinitionen.")
    NativeListCard("Planregeln", rules, "Keine Planregeln.")
}

private data class ScheduleShiftDef(
    val key: String,
    val label: String,
    val startTime: String?,
    val endTime: String?,
    val active: Boolean,
) {
    val timeLabel: String
        get() = listOfNotNull(startTime?.take(5), endTime?.take(5)).joinToString(" - ").ifBlank { "-" }
}

private data class SchedulePlanEntry(
    val id: String,
    val userId: String,
    val employee: String,
    val username: String?,
    val date: LocalDate,
    val shiftKey: String,
    val note: String?,
    val color: Color,
) {
    val searchText: String
        get() = "$employee $username $shiftKey $note"
}

private data class ScheduleAutofillPlan(val json: String, val count: Int)

@Composable
private fun ScheduleActionPanel(
    action: ModuleAction,
    summary: ModuleSummary?,
    initialValues: Map<String, String>,
    isSubmitting: Boolean,
    onSubmit: (ModuleAction, Map<String, String>) -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(action.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (action.id in setOf("schedule-entry-create", "schedule-entry-update")) "Mitarbeiter, Datum und Schicht" else "${action.fields.size} Felder",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onClose, enabled = !isSubmitting) {
                    Text("Schließen")
                }
            }
            if (action.id in setOf("schedule-entry-create", "schedule-entry-update")) {
                ScheduleEntryActionForm(
                    action = action,
                    summary = summary,
                    initialValues = initialValues,
                    isSubmitting = isSubmitting,
                    onSubmit = onSubmit,
                )
            } else {
                ModuleActionForm(
                    action = action,
                    summary = summary,
                    isSubmitting = isSubmitting,
                    onSubmit = onSubmit,
                    initialValues = initialValues,
                )
            }
        }
    }
}

@Composable
private fun ScheduleEntryActionForm(
    action: ModuleAction,
    summary: ModuleSummary?,
    initialValues: Map<String, String>,
    isSubmitting: Boolean,
    onSubmit: (ModuleAction, Map<String, String>) -> Unit,
) {
    var values by remember(action.id, initialValues) {
        mutableStateOf(action.fields.associate { it.key to (initialValues[it.key] ?: it.defaultValue) })
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        action.fields.filterNot { it.key == "id" }.forEach { field ->
            val label = when (field.key) {
                "userId" -> "Mitarbeiter"
                "shift" -> "Schicht"
                else -> field.label
            }
            ModuleActionFieldInput(
                field = field.copy(label = label),
                value = values[field.key].orEmpty(),
                enabled = !isSubmitting,
                options = optionsForField(action, field, summary),
            ) { values = values + (field.key to it) }
        }
        Button(
            onClick = { onSubmit(action, values) },
            enabled = !isSubmitting &&
                values["userId"].orEmpty().isNotBlank() &&
                values["date"].orEmpty().isNotBlank() &&
                values["shift"].orEmpty().isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(999.dp),
        ) {
            Text(if (isSubmitting) "Wird gespeichert" else action.title)
        }
    }
}

@Composable
private fun ScheduleDayCard(
    date: LocalDate,
    shifts: List<ScheduleShiftDef>,
    entries: List<SchedulePlanEntry>,
    selectedEntryId: String?,
    onSelectEmpty: (String) -> Unit,
    onSelectEntry: (SchedulePlanEntry) -> Unit,
) {
    val isToday = date == LocalDate.now()
    Surface(
        color = if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("${dayShortLabel(date)} ${date.format(DateTimeFormatter.ofPattern("dd.MM."))}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(if (isToday) "Heute" else "${entries.size} Einträge", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                StatusPill(ItemStatusChip(entries.size.toString(), Color(0xFF26315F), Color(0xFFC7D2FE)))
            }
            shifts.forEach { shift ->
                val shiftEntries = entries.filter { it.shiftKey == shift.key }
                ScheduleShiftSlot(
                    shift = shift,
                    entries = shiftEntries,
                    selectedEntryId = selectedEntryId,
                    onSelectEmpty = { onSelectEmpty(shift.key) },
                    onSelectEntry = onSelectEntry,
                )
            }
        }
    }
}

@Composable
private fun ScheduleShiftSlot(
    shift: ScheduleShiftDef,
    entries: List<SchedulePlanEntry>,
    selectedEntryId: String?,
    onSelectEmpty: () -> Unit,
    onSelectEntry: (SchedulePlanEntry) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(shift.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(shift.key, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            Text(shift.timeLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (entries.isEmpty()) {
            OutlinedButton(
                onClick = onSelectEmpty,
                modifier = Modifier.fillMaxWidth().height(34.dp),
                shape = RoundedCornerShape(6.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text("+ Mitarbeiter eintragen", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            entries.forEach { entry ->
                val selected = selectedEntryId == entry.id
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, entry.color.copy(alpha = 0.75f)),
                    modifier = Modifier.fillMaxWidth().clickable { onSelectEntry(entry) },
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(9.dp).background(entry.color, CircleShape))
                        Column(Modifier.weight(1f)) {
                            Text(entry.employee, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            entry.note?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleEntryDetails(
    entry: SchedulePlanEntry,
    rawItem: ModuleListItem?,
    editAction: ModuleAction?,
    deleteAction: ModuleAction?,
    isSubmitting: Boolean,
    onEdit: () -> Unit,
    onDelete: (ModuleAction, ModuleListItem) -> Unit,
) {
    CardBox {
        SectionTitle("Schicht bearbeiten")
        DetailRowsCard(
            "Auswahl",
            listOf(
                "Mitarbeiter" to entry.employee,
                "Datum" to entry.date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                "Schicht" to entry.shiftKey,
                "Notiz" to (entry.note ?: "-"),
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onEdit,
                enabled = editAction != null && !isSubmitting,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(6.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text("Bearbeiten", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = {
                    if (deleteAction != null && rawItem != null) onDelete(deleteAction, rawItem)
                },
                enabled = deleteAction != null && rawItem != null && !isSubmitting,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(6.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text("Löschen", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun buildScheduleShiftDefs(shifts: ModuleEndpointResult?, entries: ModuleEndpointResult?): List<ScheduleShiftDef> {
    val fromDefinitions = shifts?.items.orEmpty().mapNotNull { item ->
        val key = item.detailValue("Schicht-Key", "shiftKey") ?: item.value.takeIf { it.isNotBlank() } ?: item.id
        ScheduleShiftDef(
            key = key,
            label = item.detailValue("Bezeichnung", "Name", "label") ?: item.label.ifBlank { key },
            startTime = item.detailValue("Startzeit", "Start Time", "startTime")?.take(5),
            endTime = item.detailValue("Endzeit", "End Time", "endTime")?.take(5),
            active = item.detailValue("Aktiv", "isActive")?.isPositiveValue() ?: true,
        )
    }
    if (fromDefinitions.isNotEmpty()) return fromDefinitions.distinctBy { it.key }
    return entries?.items.orEmpty()
        .mapNotNull { it.detailValue("Schicht", "shift", "Schicht-Key", "shiftKey") }
        .distinct()
        .map { ScheduleShiftDef(it, it, null, null, true) }
}

private fun ModuleListItem.toSchedulePlanEntry(employeesById: Map<String, ModuleListItem>): SchedulePlanEntry? {
    val userId = detailValue("Benutzer-ID", "userId") ?: return null
    val date = detailValue("Datum", "date")?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    val shift = detailValue("Schicht", "shift", "Schicht-Key", "shiftKey") ?: return null
    val employee = employeesById[userId]
    val username = employee?.usernameValue()
    return SchedulePlanEntry(
        id = id,
        userId = userId,
        employee = employee?.employeeDisplayName() ?: label.ifBlank { "Benutzer #$userId" },
        username = username,
        date = date,
        shiftKey = shift,
        note = detailValue("Notiz", "note", "Kommentar", "comment")?.takeIf { it.isNotBlank() },
        color = colorFromHex(employee?.detailValue("Farbe", "Color", "color"), MaterialThemeFallbackColor(userId)),
    )
}

private fun scheduleInitialValues(
    action: ModuleAction?,
    selectedDate: LocalDate,
    selectedShiftKey: String?,
    selectedEntry: SchedulePlanEntry?,
    scheduleEntries: List<SchedulePlanEntry>,
    targetWeekStart: LocalDate,
): Map<String, String> {
    if (action == null) return emptyMap()
    return when (action.id) {
        "schedule-entry-create" -> mapOf(
            "date" to selectedDate.toString(),
            "shift" to selectedShiftKey.orEmpty(),
        )
        "schedule-entry-update" -> mapOf(
            "id" to selectedEntry?.id.orEmpty(),
            "userId" to selectedEntry?.userId.orEmpty(),
            "date" to (selectedEntry?.date ?: selectedDate).toString(),
            "shift" to (selectedEntry?.shiftKey ?: selectedShiftKey).orEmpty(),
            "note" to selectedEntry?.note.orEmpty(),
        )
        "schedule-copy" -> mapOf("entries" to buildScheduleWeekJson(scheduleEntries, targetWeekStart))
        else -> emptyMap()
    }
}

private fun buildScheduleAutofillPlan(
    weekStart: LocalDate,
    shifts: List<ScheduleShiftDef>,
    employees: List<ModuleListItem>,
    entries: List<SchedulePlanEntry>,
): ScheduleAutofillPlan {
    val candidates = employees
        .filter { it.id.all(Char::isDigit) && it.isInTimeOverview() }
        .ifEmpty { employees.filter { it.id.all(Char::isDigit) } }
    if (candidates.isEmpty() || shifts.isEmpty()) return ScheduleAutofillPlan("[]", 0)

    var userIndex = 0
    val generated = mutableListOf<Triple<String, LocalDate, String>>()
    (0 until 7).forEach { offset ->
        val date = weekStart.plusDays(offset.toLong())
        val assignedUsers = entries.filter { it.date == date }.map { it.userId }.toMutableSet()
        shifts.forEach { shift ->
            val alreadyAssignedToShift = entries.any { it.date == date && it.shiftKey == shift.key }
            if (!alreadyAssignedToShift) {
                var assigned = false
                var attempts = 0
                while (!assigned && attempts < candidates.size) {
                    val user = candidates[userIndex % candidates.size]
                    userIndex++
                    attempts++
                    if (user.id !in assignedUsers && user.canAutofillScheduleDate(date)) {
                        generated += Triple(user.id, date, shift.key)
                        assignedUsers += user.id
                        assigned = true
                    }
                }
            }
        }
    }
    val json = generated.joinToString(prefix = "[", postfix = "]") { (userId, date, shiftKey) ->
        """{"userId":$userId,"date":"$date","shift":"${shiftKey.jsonText()}"}"""
    }
    return ScheduleAutofillPlan(json, generated.size)
}

private fun ModuleListItem.canAutofillScheduleDate(date: LocalDate): Boolean {
    val expectedDays = detailInt("Arbeitstage", "Expected Work Days", "expectedWorkDays") ?: 5
    return expectedDays >= 6 || date.dayOfWeek.value <= 5
}

private fun buildScheduleWeekJson(entries: List<SchedulePlanEntry>, targetWeekStart: LocalDate): String {
    if (entries.isEmpty()) return "[]"
    val sourceWeekStart = startOfWeek(entries.minOf { it.date })
    val body = entries.joinToString(",") { entry ->
        val targetDate = targetWeekStart.plusDays((entry.date.toEpochDay() - sourceWeekStart.toEpochDay()).coerceIn(0, 6))
        """{"userId":${entry.userId},"date":"$targetDate","shift":"${entry.shiftKey.jsonText()}"}"""
    }
    return "[$body]"
}

private fun buildSchedulePrintHtml(
    weekStart: LocalDate,
    days: List<LocalDate>,
    shifts: List<ScheduleShiftDef>,
    entries: List<SchedulePlanEntry>,
): String {
    val title = "Dienstplan ${weekStart.format(DateTimeFormatter.ofPattern("dd.MM."))} - ${weekStart.plusDays(6).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}"
    val dayCards = days.joinToString("") { day ->
        val shiftBlocks = shifts.joinToString("") { shift ->
            val people = entries.filter { it.date == day && it.shiftKey == shift.key }
            val chips = people.joinToString("") { entry ->
                """<span class="chip">${entry.employee.escapeHtml()}</span>"""
            }.ifBlank { """<span class="empty">Keine Einträge</span>""" }
            """<div class="shift"><div class="shift-head"><b>${shift.label.escapeHtml()}</b><span>${shift.timeLabel.escapeHtml()}</span></div><div>$chips</div></div>"""
        }
        """<section class="day"><h2>${dayShortLabel(day)} ${day.format(DateTimeFormatter.ofPattern("dd.MM."))}</h2>$shiftBlocks</section>"""
    }
    return """
        <!doctype html>
        <html><head><meta charset="utf-8">
        <style>
        body{font-family:Arial,sans-serif;color:#111827;margin:20px}h1{font-size:22px;margin:0 0 14px}.grid{display:grid;grid-template-columns:repeat(2,1fr);gap:10px}
        .day{border:1px solid #cbd5e1;border-radius:10px;padding:10px;break-inside:avoid}.day h2{font-size:15px;margin:0 0 8px}.shift{border-top:1px solid #e5e7eb;padding:7px 0}.shift-head{display:flex;justify-content:space-between;font-size:12px;margin-bottom:5px}.chip{display:inline-block;background:#2563eb;color:#fff;border-radius:999px;padding:4px 8px;margin:2px;font-size:11px}.empty{color:#6b7280;font-size:11px}
        @media print{body{margin:8mm}.grid{grid-template-columns:repeat(2,1fr)}}
        </style></head><body><h1>${title.escapeHtml()}</h1><div class="grid">$dayCards</div></body></html>
    """.trimIndent()
}

private fun printSchedule(context: Context, title: String, html: String) {
    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val adapter = view.createPrintDocumentAdapter(title)
            val attributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()
            printManager.print(title, adapter, attributes)
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}

private fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun String.jsonText(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")

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

private enum class PayrollTab(val title: String, val emptyText: String) {
    OPEN("Offen", "Keine offenen Lohnabrechnungen."),
    APPROVED("Freigegeben", "Keine freigegebenen Lohnabrechnungen."),
    ALL("Alle", "Keine Lohnabrechnungen."),
}

private fun PayrollTab.listTitle(): String =
    when (this) {
        PayrollTab.OPEN -> "Offene Abrechnungen"
        PayrollTab.APPROVED -> "Freigegebene Abrechnungen"
        PayrollTab.ALL -> "Alle Abrechnungen"
    }

@Composable
private fun PayrollAdminScreen(
    session: AuthenticatedSession,
    summary: ModuleSummary?,
    isSubmitting: Boolean,
    onSubmitModuleAction: (ModuleAction, Map<String, String>) -> Unit,
) {
    val pending = endpoint(summary, "Offen")
    val approved = endpoint(summary, "Freigegeben")
    val all = endpoint(summary, "Alle")
    val employees = endpoint(summary, "Mitarbeiter")
    val actions = summary?.actions.orEmpty().associateBy { it.id }
    var selectedTab by remember(pending?.count, approved?.count, all?.count) { mutableStateOf(PayrollTab.OPEN) }
    val selectedEndpoint = when (selectedTab) {
        PayrollTab.OPEN -> pending
        PayrollTab.APPROVED -> approved
        PayrollTab.ALL -> all
    }

    HeroCard("Abrechnungen", "Lohnläufe vorbereiten, prüfen, freigeben und als PDF exportieren.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InlineMetric("Offen", endpointCount(pending), Modifier.weight(1f))
        InlineMetric("Freigegeben", endpointCount(approved), Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InlineMetric("Gesamt", endpointCount(all), Modifier.weight(1f))
        InlineMetric("Mitarbeiter", endpointCount(employees), Modifier.weight(1f))
    }

    PayrollActionPanel(
        summary = summary,
        isSubmitting = isSubmitting,
        onSubmit = onSubmitModuleAction,
    )
    PayrollTabSelector(
        selectedTab = selectedTab,
        onSelectTab = { selectedTab = it },
        pending = pending,
        approved = approved,
        all = all,
    )
    PayrollListCard(
        title = selectedTab.listTitle(),
        endpoint = selectedEndpoint,
        emptyText = selectedTab.emptyText,
        session = session,
        pdfPathFor = { item -> "/api/payslips/admin/pdf/${item.id}?lang=de" },
        fileNamePrefix = "admin-payslip",
        actions = actions,
        canManage = true,
        isSubmitting = isSubmitting,
        onSubmit = onSubmitModuleAction,
    )
    DownloadsCard(
        title = "Export",
        session = session,
        downloads = listOf(
            DownloadSpec("CSV", "/api/payslips/admin/export?lang=de", "payroll-export.csv"),
            DownloadSpec("Backup", "/api/payslips/admin/backup", "payroll-backup.csv"),
        ),
    )
}

@Composable
private fun PayrollActionPanel(
    summary: ModuleSummary?,
    isSubmitting: Boolean,
    onSubmit: (ModuleAction, Map<String, String>) -> Unit,
) {
    val actions = summary?.actions.orEmpty()
    val primaryActions = listOfNotNull(
        actions.firstOrNull { it.id == "payslip-generate" },
        actions.firstOrNull { it.id == "payslip-approve-all" },
        actions.firstOrNull { it.id == "payslip-schedule-all" },
        actions.firstOrNull { it.id == "payslip-schedule-user" },
        actions.firstOrNull { it.id == "payslip-set-payout" },
    )
    if (primaryActions.isEmpty()) return
    var selectedActionId by remember(actions.size) { mutableStateOf(primaryActions.first().id) }
    val selectedAction = primaryActions.firstOrNull { it.id == selectedActionId } ?: primaryActions.first()

    CardBox {
        SectionTitle("Aktionen")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            primaryActions.forEach { action ->
                if (action.id == selectedAction.id) {
                    Button(
                        onClick = { selectedActionId = action.id },
                        modifier = Modifier.height(38.dp),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) { Text(action.payrollShortTitle(), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                } else {
                    OutlinedButton(
                        onClick = { selectedActionId = action.id },
                        modifier = Modifier.height(38.dp),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) { Text(action.payrollShortTitle(), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        ModuleActionForm(selectedAction, summary, isSubmitting, onSubmit)
    }
}

private fun ModuleAction.payrollShortTitle(): String =
    when (id) {
        "payslip-generate" -> "Erzeugen"
        "payslip-approve-all" -> "Alle freigeben"
        "payslip-schedule-all" -> "Auszahlung alle"
        "payslip-schedule-user" -> "Auszahlung Mitarbeiter"
        "payslip-set-payout" -> "Auszahlung setzen"
        else -> title
    }

@Composable
private fun PayrollTabSelector(
    selectedTab: PayrollTab,
    onSelectTab: (PayrollTab) -> Unit,
    pending: ModuleEndpointResult?,
    approved: ModuleEndpointResult?,
    all: ModuleEndpointResult?,
) {
    val counts = mapOf(
        PayrollTab.OPEN to endpointCount(pending),
        PayrollTab.APPROVED to endpointCount(approved),
        PayrollTab.ALL to endpointCount(all),
    )
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PayrollTab.values().forEach { tab ->
            val label = "${tab.title} ${counts[tab].orEmpty()}"
            if (tab == selectedTab) {
                Button(
                    onClick = { onSelectTab(tab) },
                    modifier = Modifier.height(40.dp),
                    contentPadding = ButtonDefaults.ContentPadding,
                ) { Text(label, maxLines = 1) }
            } else {
                OutlinedButton(
                    onClick = { onSelectTab(tab) },
                    modifier = Modifier.height(40.dp),
                    contentPadding = ButtonDefaults.ContentPadding,
                ) { Text(label, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun PayrollListCard(
    title: String,
    endpoint: ModuleEndpointResult?,
    emptyText: String,
    session: AuthenticatedSession,
    pdfPathFor: (ModuleListItem) -> String,
    fileNamePrefix: String,
    actions: Map<String, ModuleAction> = emptyMap(),
    canManage: Boolean = false,
    isSubmitting: Boolean = false,
    onSubmit: (ModuleAction, Map<String, String>) -> Unit = { _, _ -> },
) {
    var query by remember(title, endpoint?.path, endpoint?.count) { mutableStateOf("") }
    var selectedItemId by remember(title, endpoint?.path, endpoint?.count) { mutableStateOf<String?>(null) }
    var pendingSubmit by remember { mutableStateOf<AdminWorkSubmission?>(null) }

    pendingSubmit?.let { submission ->
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) pendingSubmit = null },
            title = { Text(submission.action.title) },
            text = { Text("${submission.item.payrollEmployeeName()} wirklich ${submission.action.directActionVerb()}?") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingSubmit = null
                        onSubmit(submission.action, submission.values)
                    },
                    enabled = !isSubmitting,
                ) {
                    Text(if (isSubmitting) "Wird gespeichert" else "Bestätigen")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSubmit = null }, enabled = !isSubmitting) {
                    Text("Abbrechen")
                }
            },
        )
    }

    CardBox {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(title)
            Text(endpointStatusText(endpoint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            endpoint == null -> Text("Daten werden geladen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            endpoint.status == ModuleRequestStatus.FORBIDDEN -> Text("Keine Berechtigung.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            endpoint.status == ModuleRequestStatus.ERROR -> Text(endpoint.message ?: "Konnte nicht geladen werden.", color = MaterialTheme.colorScheme.error)
            endpoint.items.isEmpty() -> Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Abrechnung suchen") },
                    singleLine = true,
                )
                val filtered = endpoint.items
                    .filter { item -> query.isBlank() || item.searchText().contains(query, ignoreCase = true) }
                    .sortedWith(compareByDescending<ModuleListItem> { it.payrollSortDate() }.thenBy { it.payrollEmployeeName().lowercase() })
                if (filtered.isEmpty()) {
                    Text("Keine Treffer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                filtered.take(60).forEach { item ->
                    val selected = selectedItemId == item.id
                    PayrollItemCard(
                        item = item,
                        selected = selected,
                        session = session,
                        pdfPath = pdfPathFor(item),
                        pdfFileName = "$fileNamePrefix-${item.id}.pdf",
                        actions = actions,
                        canManage = canManage,
                        isSubmitting = isSubmitting,
                        onSelect = { selectedItemId = if (selected) null else item.id },
                        onDirectSubmit = { pendingSubmit = it },
                    )
                }
                if (filtered.size > 60) {
                    Text("${filtered.size - 60} weitere Treffer. Bitte Suche verfeinern.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PayrollItemCard(
    item: ModuleListItem,
    selected: Boolean,
    session: AuthenticatedSession,
    pdfPath: String,
    pdfFileName: String,
    actions: Map<String, ModuleAction>,
    canManage: Boolean,
    isSubmitting: Boolean,
    onSelect: () -> Unit,
    onDirectSubmit: (AdminWorkSubmission) -> Unit,
) {
    val context = LocalContext.current
    val status = item.payrollStatusChip()
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(92.dp)
                    .background(status.contentColor, RoundedCornerShape(999.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().clickable(onClick = onSelect), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.payrollEmployeeName(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.payrollPeriodLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatusPill(status)
                        Text("#${item.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactFact("Brutto", item.payrollAmount("Bruttolohn", "Gross Salary", "grossSalary"), Modifier.weight(1f))
                    CompactFact("Netto", item.payrollAmount("Nettolohn", "Net Salary", "netSalary"), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactFact("Auszahlung", item.payrollDateLabel("Auszahlung", "Payout Date", "payoutDate"), Modifier.weight(1f))
                    CompactFact("Überstunden", item.payrollHoursLabel("Überstunden", "Overtime Hours", "overtimeHours"), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            startAuthenticatedDownload(
                                context = context,
                                session = session,
                                download = DownloadSpec("PDF", pdfPath, pdfFileName),
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) {
                        Text("PDF", maxLines = 1)
                    }
                    if (canManage) {
                        PayrollDirectActionButtons(
                            item = item,
                            actions = actions,
                            isSubmitting = isSubmitting,
                            onDirectSubmit = onDirectSubmit,
                            modifier = Modifier.weight(2f),
                        )
                    }
                }
                if (selected) {
                    ModuleItemDetails(item)
                } else {
                    Text("Antippen für Details.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PayrollDirectActionButtons(
    item: ModuleListItem,
    actions: Map<String, ModuleAction>,
    isSubmitting: Boolean,
    onDirectSubmit: (AdminWorkSubmission) -> Unit,
    modifier: Modifier = Modifier,
) {
    val approved = item.payrollIsApproved()
    val firstAction = if (approved) actions["payslip-reopen"] else actions["payslip-approve"]
    val secondAction = if (approved) actions["payslip-set-payout"] else actions["payslip-set-payout"]
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            firstAction?.let { action ->
                Button(
                    onClick = { onDirectSubmit(AdminWorkSubmission(action, item, directActionValues(action, item))) },
                    enabled = !isSubmitting,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = ButtonDefaults.ContentPadding,
                ) {
                    Text(if (approved) "Öffnen" else "Freigeben", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            secondAction?.let { action ->
                OutlinedButton(
                    onClick = { onDirectSubmit(AdminWorkSubmission(action, item, directActionValues(action, item))) },
                    enabled = !isSubmitting,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = ButtonDefaults.ContentPadding,
                ) {
                    Text("Heute", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (!approved) {
            actions["payslip-delete"]?.let { action ->
                OutlinedButton(
                    onClick = { onDirectSubmit(AdminWorkSubmission(action, item, directActionValues(action, item))) },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = ButtonDefaults.ContentPadding,
                ) {
                    Text("Löschen", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun ModuleListItem.payrollEmployeeName(): String {
    val fullName = listOfNotNull(detailValue("Vorname", "First Name", "firstName"), detailValue("Nachname", "Last Name", "lastName"))
        .joinToString(" ")
        .trim()
        .takeIf { it.isNotBlank() }
    return fullName
        ?: detailValue("Mitarbeiter", "Benutzer", "Name", "Username", "userName")
        ?: label.substringBefore(" - ").takeIf { it.isNotBlank() }
        ?: "Mitarbeiter #$id"
}

private fun ModuleListItem.payrollPeriodLabel(): String {
    val start = payrollDateLabel("Periode Start", "Start", "periodStart")
    val end = payrollDateLabel("Periode Ende", "Ende", "periodEnd")
    return when {
        start != "-" && end != "-" -> "$start - $end"
        start != "-" -> start
        else -> detail ?: "Kein Zeitraum"
    }
}

private fun ModuleListItem.payrollStatusChip(): ItemStatusChip {
    val approved = payrollIsApproved()
    val payout = detailValue("Auszahlung", "Payout Date", "payoutDate").orEmpty().isNotBlank()
    val warning = payrollNumber("Bruttolohn", "Gross Salary", "grossSalary")?.let { it < 0 } == true ||
        payrollNumber("Nettolohn", "Net Salary", "netSalary")?.let { it < 0 } == true
    return when {
        approved && payout -> ItemStatusChip("Freigegeben", Color(0xFF123D2F), Color(0xFF34D399))
        approved -> ItemStatusChip("Freigegeben", Color(0xFF123D2F), Color(0xFF34D399))
        warning -> ItemStatusChip("Prüfen", Color(0xFF4A3210), Color(0xFFFBBF24))
        else -> ItemStatusChip("Offen", Color(0xFF26315F), Color(0xFFC7D2FE))
    }
}

private fun ModuleListItem.payrollIsApproved(): Boolean =
    detailBool("Genehmigt", "Approved", "approved")

private fun ModuleListItem.payrollSortDate(): String =
    detailValue("Auszahlung", "Payout Date", "payoutDate", "Periode Ende", "Ende", "periodEnd", "Periode Start", "Start", "periodStart").orEmpty()

private fun ModuleListItem.payrollDateLabel(vararg labels: String): String {
    val raw = detailValue(*labels)?.takeIf { it.isNotBlank() } ?: return "-"
    return raw.take(10).let { value ->
        runCatching { LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) }.getOrElse { raw }
    }
}

private fun ModuleListItem.payrollAmount(vararg labels: String): String {
    val amount = payrollNumber(*labels) ?: return "-"
    val currency = detailValue("Währung", "Currency", "currency") ?: "CHF"
    return "${"%.2f".format(java.util.Locale.US, amount)} $currency"
}

private fun ModuleListItem.payrollHoursLabel(vararg labels: String): String {
    val hours = payrollNumber(*labels) ?: return "-"
    return "${"%.2f".format(java.util.Locale.US, hours)} h"
}

private fun ModuleListItem.payrollNumber(vararg labels: String): Double? =
    detailValue(*labels)
        ?.trim()
        ?.replace("'", "")
        ?.replace(",", ".")
        ?.substringBefore(' ')
        ?.toDoubleOrNull()

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
    val batches = endpoint(summary, "Zahlungsläufe")
    val open = endpoint(summary, "Offene Läufe")
    val payables = endpoint(summary, "Kreditoren")
    val receivables = endpoint(summary, "Debitoren")
    val signatures = endpoint(summary, "Signaturen")
    val messages = endpoint(summary, "Nachrichten")
    HeroCard("Banking", "Bankkonten, Zahlungsläufe, Signaturen und Nachrichten.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Konten", endpointCount(accounts), Modifier.weight(1f))
        MetricCard("Offen", endpointCount(open), Modifier.weight(1f))
    }
    DownloadListCard(
        title = "Pain.001 XML",
        endpoint = batches ?: open,
        emptyText = "Keine Zahlungsläufe für XML-Export.",
        session = session,
        pathFor = { item -> "/api/banking/pain001/${item.id}" },
        fileNameFor = { item -> "pain001-${item.id}.xml" },
    )
    NativeListCard("Bankkonten", accounts, "Keine Bankkonten.")
    NativeListCard("Zahlungsläufe", batches, "Keine Zahlungsläufe.")
    NativeListCard("Offene Läufe", open, "Keine offenen Zahlungsläufe.")
    NativeListCard("Kreditoren", payables, "Keine offenen Kreditoren.")
    NativeListCard("Debitoren", receivables, "Keine offenen Debitoren.")
    NativeListCard("Signaturen", signatures, "Keine Signaturen.")
    NativeListCard("Nachrichten", messages, "Keine Nachrichten.")
}

@Composable
private fun KnowledgeScreen(summary: ModuleSummary?) {
    val knowledge = endpoint(summary, "Knowledge") ?: endpoint(summary, "Wissen")
    HeroCard("Firmenwissen", "Interne Wissensbasis und KI-Inhalte.")
    NativeListCard("Wissen", knowledge, "Keine Wissenseinträge.")
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
    val changes = endpoint(summary, "Änderungen")
    HeroCard("Firmen", "Mandantenverwaltung, Zahlungsstatus und Superadmin-Übersicht.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Firmen", endpointCount(companies), Modifier.weight(1f))
        MetricCard("Analytics", endpointCount(analytics), Modifier.weight(1f))
    }
    DownloadListCard(
        title = "Firmenexport",
        endpoint = companies,
        emptyText = "Keine Firmen für Export.",
        session = session,
        pathFor = { item -> "/api/superadmin/companies/${item.id}/export" },
        fileNameFor = { item -> "company-${item.id}.csv" },
    )
    NativeListCard("Firmen", companies, "Keine Firmen.")
    NativeListCard("Analytics", analytics, "Keine Analytics-Daten.")
    NativeListCard("Ausgeschlossene IPs", excludedIps, "Keine ausgeschlossenen IPs.")
    NativeListCard("Änderungen", changes, "Keine Änderungen.")
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
                color = if (isOpen) Color(0xFF123D2F) else MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text(
                    if (isOpen) "Läuft" else "Offen",
                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isOpen) Color(0xFF34D399) else MaterialTheme.colorScheme.onPrimaryContainer,
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
        MetricCard("Überstunden", formatSignedMinutes(user.trackingBalanceInMinutes), Modifier.weight(1f))
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
                SectionTitle("Kunde & Projekt")
                Text(
                    listOf(selectedCustomer, selectedProject, selectedTask)
                        .filterNot { it.startsWith("Kein ") }
                        .joinToString(" / ")
                        .ifBlank { user.lastCustomerName?.let { "Zuletzt: $it" } ?: "Keine Zuordnung gewählt" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
private fun UserWeekOverview(
    weekSummaries: List<Pair<LocalDate, DailyTimeSummary?>>,
    now: LocalDateTime,
    moduleSummary: ModuleSummary?,
    isSubmitting: Boolean,
    canGoNext: Boolean,
    onPreviousWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSubmit: (ModuleAction, Map<String, String>) -> Unit,
) {
    if (weekSummaries.isEmpty()) return
    val today = now.toLocalDate()
    var selectedDate by remember(weekSummaries.map { it.first }) {
        mutableStateOf(weekSummaries.firstOrNull { it.first == today }?.first ?: weekSummaries.first().first)
    }
    val selectedSummary = weekSummaries.firstOrNull { it.first == selectedDate }?.second
    val correctionAction = moduleSummary?.actions.orEmpty().firstOrNull { it.id == "correction-create" }
    CardBox {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionTitle("Wochenübersicht")
                Text(
                    "${weekSummaries.first().first.format(DateTimeFormatter.ofPattern("dd.MM."))} - ${weekSummaries.last().first.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(ItemStatusChip("${weekSummaries.sumOf { (_, summary) -> summary?.entries.orEmpty().size }} Stempel", Color(0xFF26315F), Color(0xFFC7D2FE)))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onPreviousWeek,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text("Zurück", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = onCurrentWeek,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text("Aktuell", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = onNextWeek,
                enabled = canGoNext,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text("Weiter", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            weekSummaries.forEach { (date, summary) ->
                val selected = selectedDate == date
                val entries = summary?.entries.orEmpty().sortedBy { it.entryTimestamp }
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background)
                        .clickable { selectedDate = date }
                        .padding(vertical = 8.dp, horizontal = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(dayShortLabel(date), style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text(formatMinutes(displayWorkedMinutes(summary, now)), style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Text("${entries.size}", style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        UserSelectedDayOverview(selectedDate, selectedSummary, now)
        correctionAction?.let { action ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            ModuleActionForm(
                action = action,
                summary = moduleSummary,
                isSubmitting = isSubmitting,
                onSubmit = onSubmit,
                initialValues = correctionInitialValues(selectedDate, selectedSummary, now),
            )
        }
    }
}

@Composable
private fun UserSelectedDayOverview(date: LocalDate, summary: DailyTimeSummary?, now: LocalDateTime) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(date.format(DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy")), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactFact("Ist", formatMinutes(displayWorkedMinutes(summary, now)), Modifier.weight(1f))
            CompactFact("Pause", formatMinutes(summary?.breakMinutes ?: 0), Modifier.weight(1f))
            CompactFact("Stempel", summary?.entries.orEmpty().size.toString(), Modifier.weight(1f))
        }
        val entries = summary?.entries.orEmpty().sortedBy { it.entryTimestamp }
        if (entries.isEmpty()) {
            Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                Text("Keine Stempel.", Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            entries.forEach { entry ->
                UserWeekEntryRow(entry)
            }
        }
    }
}

@Composable
private fun UserWeekEntryRow(entry: TimeTrackingEntry) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = if (entry.punchType == PunchType.START) Color(0xFF123D2F) else Color(0xFF26315F), shape = RoundedCornerShape(999.dp)) {
                    Text(punchLabel(entry.punchType), Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = if (entry.punchType == PunchType.START) Color(0xFF34D399) else Color(0xFFC7D2FE), style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    listOfNotNull(entry.customerName, entry.projectName, entry.taskName).joinToString(" / ").ifBlank { sourceLabel(entry.source) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(entry.entryTimestamp?.toLocalTime().formatTimeOrDash(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun correctionInitialValues(date: LocalDate, summary: DailyTimeSummary?, now: LocalDateTime): Map<String, String> {
    val lastEntry = summary?.entries.orEmpty().maxByOrNull { it.entryTimestamp ?: LocalDateTime.MIN }
    val timestamp = lastEntry?.entryTimestamp ?: LocalDateTime.of(date, now.toLocalTime().withSecond(0).withNano(0))
    val nextType = if (lastEntry?.punchType == PunchType.START) "ENDE" else "START"
    return mapOf(
        "requestDate" to date.toString(),
        "desiredTimestamp" to timestamp.withSecond(0).withNano(0).toString(),
        "desiredPunchType" to nextType,
        "reason" to "",
    )
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
                    InlineMetric("Einträge", summary.totalCount.toString(), Modifier.weight(1f))
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
                InlineMetric("Einträge", summary.totalCount.toString(), Modifier.weight(1f))
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
            InlineMetric("Abrechnungen", endpointCount(payroll), Modifier.weight(1f))
        }
    }
}

private data class AdminWorkCategory(
    val id: String,
    val title: String,
    val endpoint: ModuleEndpointResult?,
    val emptyText: String,
    val primaryActionId: String? = null,
    val primaryLabel: String? = null,
    val secondaryActionId: String? = null,
    val secondaryLabel: String? = null,
    val tertiaryActionId: String? = null,
    val tertiaryLabel: String? = null,
)

private data class AdminWorkSubmission(
    val action: ModuleAction,
    val item: ModuleListItem,
    val values: Map<String, String>,
)

@Composable
private fun AdminRequestsWorkScreen(
    corrections: ModuleEndpointResult?,
    vacation: ModuleEndpointResult?,
    sick: ModuleEndpointResult?,
    payroll: ModuleEndpointResult?,
    summary: ModuleSummary?,
    isSubmitting: Boolean,
    onSubmit: (ModuleAction, Map<String, String>) -> Unit,
    onOpenPayroll: () -> Unit,
) {
    val actions = summary?.actions.orEmpty().associateBy { it.id }
    val categories = listOf(
        AdminWorkCategory(
            id = "corrections",
            title = "Korrekturen",
            endpoint = corrections,
            emptyText = "Keine offenen Korrekturen.",
            primaryActionId = "correction-approve",
            primaryLabel = "Genehmigen",
            secondaryActionId = "correction-deny",
            secondaryLabel = "Ablehnen",
        ),
        AdminWorkCategory(
            id = "vacation",
            title = "Urlaub",
            endpoint = vacation,
            emptyText = "Keine offenen Urlaubsanträge.",
            primaryActionId = "vacation-approve",
            primaryLabel = "Genehmigen",
            secondaryActionId = "vacation-deny",
            secondaryLabel = "Ablehnen",
            tertiaryActionId = "vacation-delete",
            tertiaryLabel = "Löschen",
        ),
        AdminWorkCategory(
            id = "sick",
            title = "Krankheit",
            endpoint = sick,
            emptyText = "Keine offenen Krankmeldungen.",
            tertiaryActionId = "sick-delete",
            tertiaryLabel = "Löschen",
        ),
        AdminWorkCategory(
            id = "payroll",
            title = "Abrechnungen",
            endpoint = payroll,
            emptyText = "Keine offenen Lohnabrechnungen.",
            primaryActionId = "payslip-approve",
            primaryLabel = "Freigeben",
            secondaryActionId = "payslip-set-payout",
            secondaryLabel = "Auszahlung heute",
        ),
    )
    val defaultCategory = categories.firstOrNull { pendingItemCount(it.endpoint) > 0 }?.id ?: categories.first().id
    var selectedCategoryId by remember(corrections?.count, vacation?.count, sick?.count, payroll?.count) { mutableStateOf(defaultCategory) }
    var query by remember(selectedCategoryId) { mutableStateOf("") }
    var showAll by remember(selectedCategoryId) { mutableStateOf(false) }
    var selectedItemId by remember(selectedCategoryId) { mutableStateOf<String?>(null) }
    var pendingSubmit by remember { mutableStateOf<AdminWorkSubmission?>(null) }
    val selectedCategory = categories.firstOrNull { it.id == selectedCategoryId } ?: categories.first()

    pendingSubmit?.let { submission ->
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) pendingSubmit = null },
            title = { Text(submission.action.title) },
            text = {
                Text("${submission.item.label} wirklich ${submission.action.directActionVerb()}?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingSubmit = null
                        onSubmit(submission.action, submission.values)
                    },
                    enabled = !isSubmitting,
                ) {
                    Text(if (isSubmitting) "Wird gespeichert" else "Bestätigen")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSubmit = null }, enabled = !isSubmitting) {
                    Text("Abbrechen")
                }
            },
        )
    }

    CardBox {
        SectionTitle("Offene Arbeit")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminWorkMetric("Offene Korr.", pendingItemCount(corrections).toString(), Modifier.weight(1f))
            AdminWorkMetric("Offener Urlaub", pendingItemCount(vacation).toString(), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminWorkMetric("Krankheit", pendingItemCount(sick).toString(), Modifier.weight(1f))
            AdminWorkMetric("Abrechnungen", endpointNumericCount(payroll).toString(), Modifier.weight(1f))
        }
    }

    CardBox {
        SectionTitle("Arbeitsliste")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { category ->
                val count = if (category.id == "payroll") endpointNumericCount(category.endpoint) else pendingItemCount(category.endpoint)
                val label = "${category.title} $count"
                if (category.id == selectedCategory.id) {
                    Button(
                        onClick = { selectedCategoryId = category.id },
                        modifier = Modifier.height(38.dp),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                } else {
                    OutlinedButton(
                        onClick = { selectedCategoryId = category.id },
                        modifier = Modifier.height(38.dp),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("${selectedCategory.title} suchen") },
            singleLine = true,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showAll) {
                Button(onClick = { showAll = false }, modifier = Modifier.weight(1f), contentPadding = ButtonDefaults.ContentPadding) {
                    Text("Nur offene", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                OutlinedButton(onClick = { showAll = true }, modifier = Modifier.weight(1f), contentPadding = ButtonDefaults.ContentPadding) {
                    Text("Alle anzeigen", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            OutlinedButton(onClick = { query = "" }, modifier = Modifier.weight(1f), contentPadding = ButtonDefaults.ContentPadding) {
                Text("Suche löschen", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (selectedCategory.id == "payroll") {
            OutlinedButton(onClick = onOpenPayroll, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp)) {
                Text("Abrechnungsseite öffnen")
            }
        }
        AdminWorkItems(
            category = selectedCategory,
            actions = actions,
            query = query,
            showAll = showAll,
            selectedItemId = selectedItemId,
            onSelectItem = { itemId -> selectedItemId = if (selectedItemId == itemId) null else itemId },
            isSubmitting = isSubmitting,
            onSubmit = { submission -> pendingSubmit = submission },
        )
    }
}

@Composable
private fun AdminWorkMetric(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AdminWorkItems(
    category: AdminWorkCategory,
    actions: Map<String, ModuleAction>,
    query: String,
    showAll: Boolean,
    selectedItemId: String?,
    onSelectItem: (String) -> Unit,
    isSubmitting: Boolean,
    onSubmit: (AdminWorkSubmission) -> Unit,
) {
    val endpoint = category.endpoint
    when {
        endpoint == null -> Text("Daten werden geladen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        endpoint.status == ModuleRequestStatus.FORBIDDEN -> Text("Keine Berechtigung für ${category.title}.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        endpoint.status == ModuleRequestStatus.ERROR -> Text(endpoint.message ?: "${category.title} konnte nicht geladen werden.", color = MaterialTheme.colorScheme.error)
        endpoint.items.isEmpty() -> Text(category.emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> {
            val baseItems = if (showAll) endpoint.items else endpoint.items.filter { it.isPendingApprovalItem() }
            val filteredItems = baseItems.filter { query.isBlank() || it.searchText().contains(query, ignoreCase = true) }
            if (filteredItems.isEmpty()) {
                Text(
                    if (showAll) "Keine Treffer." else "${category.emptyText} Tippe auf Alle anzeigen für erledigte Einträge.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            filteredItems.take(30).forEach { item ->
                AdminWorkItemCard(
                    category = category,
                    item = item,
                    actions = actions,
                    selected = selectedItemId == item.id,
                    showAll = showAll,
                    isSubmitting = isSubmitting,
                    onSelect = { onSelectItem(item.id) },
                    onSubmit = onSubmit,
                )
            }
            if (filteredItems.size > 30) {
                Text("${filteredItems.size - 30} weitere Treffer. Bitte Suche verfeinern.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AdminWorkItemCard(
    category: AdminWorkCategory,
    item: ModuleListItem,
    actions: Map<String, ModuleAction>,
    selected: Boolean,
    showAll: Boolean,
    isSubmitting: Boolean,
    onSelect: () -> Unit,
    onSubmit: (AdminWorkSubmission) -> Unit,
) {
    val isOpen = item.isPendingApprovalItem()
    val status = if (isOpen) statusChipFor("offen", allowUnknown = true) else item.statusChip()
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onSelect), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(item.adminWorkSubtitle(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    status?.let { StatusPill(it) }
                    Text("#${item.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isOpen || category.id == "payroll") {
                AdminWorkActionButtons(category, item, actions, isSubmitting, onSubmit)
            } else if (showAll) {
                Text("Bereits erledigt. Details antippen für Verlauf.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                ModuleItemDetails(item)
            }
        }
    }
}

@Composable
private fun AdminWorkActionButtons(
    category: AdminWorkCategory,
    item: ModuleListItem,
    actions: Map<String, ModuleAction>,
    isSubmitting: Boolean,
    onSubmit: (AdminWorkSubmission) -> Unit,
) {
    val primary = category.primaryActionId?.let { actions[it] }
    val secondary = category.secondaryActionId?.let { actions[it] }
    val tertiary = category.tertiaryActionId?.let { actions[it] }
    if (primary == null && secondary == null && tertiary == null) {
        Text("Keine direkte Aktion für diesen Eintrag geladen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        primary?.let { action ->
            Button(
                onClick = { onSubmit(AdminWorkSubmission(action, item, directActionValues(action, item))) },
                enabled = !isSubmitting,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(6.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text(category.primaryLabel ?: action.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        secondary?.let { action ->
            OutlinedButton(
                onClick = { onSubmit(AdminWorkSubmission(action, item, directActionValues(action, item))) },
                enabled = !isSubmitting,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(6.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text(category.secondaryLabel ?: action.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    tertiary?.let { action ->
        OutlinedButton(
            onClick = { onSubmit(AdminWorkSubmission(action, item, directActionValues(action, item))) },
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            contentPadding = ButtonDefaults.ContentPadding,
        ) {
            Text(category.tertiaryLabel ?: action.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun directActionValues(action: ModuleAction, item: ModuleListItem): Map<String, String> =
    action.fields.associate { field ->
        val value = when (field.key) {
            "id" -> item.id
            "comment" -> ""
            "payoutDate" -> LocalDate.now().toString()
            else -> field.defaultValue
        }
        field.key to value
    }

private fun ModuleAction.directActionVerb(): String =
    when {
        id.contains("approve", ignoreCase = true) -> "genehmigen"
        id.contains("deny", ignoreCase = true) -> "ablehnen"
        id.contains("delete", ignoreCase = true) -> "löschen"
        id.contains("payout", ignoreCase = true) -> "zur Auszahlung setzen"
        else -> "ausführen"
    }

private fun ModuleListItem.adminWorkSubtitle(): String {
    val pieces = listOfNotNull(
        detailValue("Benutzer", "Name", "Username"),
        detailValue("Datum", "Antrag", "Start", "Zeitpunkt")?.take(10),
        detailValue("Stempel", "Desired Punch Type", "Typ"),
        detailValue("Grund", "Kommentar", "Feedback"),
    )
    return pieces.distinct().joinToString(" | ").ifBlank { detail ?: "Details antippen" }
}

private data class TeamDaySummary(
    val username: String,
    val date: LocalDate,
    val workedMinutes: Int,
    val breakMinutes: Int,
    val needsCorrection: Boolean,
    val entryCount: Int,
    val firstStart: String?,
    val lastEnd: String?,
    val isOpen: Boolean,
    val punchLines: List<String>,
    val editEntriesJson: String,
)

private data class TeamTimeRow(
    val username: String,
    val displayName: String,
    val color: Color,
    val weekWorkedMinutes: Int,
    val weekExpectedMinutes: Int,
    val weekBalanceMinutes: Int,
    val totalBalanceMinutes: Int,
    val problemCount: Int,
    val days: List<TeamDaySummary>,
) {
    val searchText: String
        get() = "$displayName $username"
}

@Composable
private fun TeamTimeOverviewCard(
    time: ModuleEndpointResult?,
    employees: ModuleEndpointResult?,
    balances: ModuleEndpointResult?,
    weekly: ModuleEndpointResult?,
    summary: ModuleSummary?,
    isSubmitting: Boolean,
    onSubmit: (ModuleAction, Map<String, String>) -> Unit,
) {
    val currentWeekStart = startOfWeek(LocalDate.now())
    var weekStart by remember { mutableStateOf(currentWeekStart) }
    val weekEnd = weekStart.plusDays(6)
    val rows = remember(time, employees, balances, weekly, weekStart) {
        buildTeamTimeRows(time, employees, balances, weekly, weekStart)
    }
    var query by remember(rows.size) { mutableStateOf("") }
    var onlyProblems by remember(rows.size) { mutableStateOf(false) }
    var expandedUsername by remember(rows.size, weekStart) { mutableStateOf<String?>(null) }
    val filteredRows = rows
        .filter { row -> query.isBlank() || row.searchText.contains(query, ignoreCase = true) }
        .filter { row -> !onlyProblems || row.problemCount > 0 || row.weekBalanceMinutes < 0 }
    val visibleRows = filteredRows.take(25)
    val problemRows = rows.count { it.problemCount > 0 || it.weekBalanceMinutes < 0 }
    val editDayAction = summary?.actions?.firstOrNull { it.id == "admin-edit-day" }

    CardBox {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionTitle(if (weekStart == currentWeekStart) "Teamzeit aktuelle Woche" else "Teamzeit Woche")
                Text(
                    "${weekStart.format(DateTimeFormatter.ofPattern("dd.MM."))} - ${weekEnd.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(endpointStatusText(time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { weekStart = weekStart.minusDays(7) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text("< Woche", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = { weekStart = currentWeekStart },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text("Heute", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = { weekStart = weekStart.plusDays(7) },
                enabled = weekStart < currentWeekStart,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text("Woche >", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when {
            time == null || time.isLoadingLike() -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Teamzeiten werden geladen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            time.status == ModuleRequestStatus.FORBIDDEN -> Text("Keine Berechtigung für die Teamzeit.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            time.status == ModuleRequestStatus.ERROR -> Text(time.message ?: "Teamzeit konnte nicht geladen werden.", color = MaterialTheme.colorScheme.error)
            rows.isEmpty() -> Text("Keine Mitarbeiter mit Zeitdaten in dieser Woche.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InlineMetric("Mitarbeiter", rows.size.toString(), Modifier.weight(1f))
                    InlineMetric("Ist gesamt", formatHumanMinutes(rows.sumOf { it.weekWorkedMinutes }), Modifier.weight(1f))
                    InlineMetric("Probleme", problemRows.toString(), Modifier.weight(1f))
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mitarbeiter suchen") },
                    singleLine = true,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onlyProblems) {
                        Button(onClick = { onlyProblems = false }, modifier = Modifier.weight(1f)) {
                            Text("Problemfilter aktiv", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        OutlinedButton(onClick = { onlyProblems = true }, modifier = Modifier.weight(1f)) {
                            Text("Nur Problemfälle", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    OutlinedButton(onClick = { query = "" }, modifier = Modifier.weight(1f)) {
                        Text("Filter löschen", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (visibleRows.isEmpty()) {
                    Text("Keine Treffer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    visibleRows.forEach { row ->
                        TeamTimeRowCard(
                            row = row,
                            expanded = expandedUsername == row.username,
                            onToggle = { expandedUsername = if (expandedUsername == row.username) null else row.username },
                            editDayAction = editDayAction,
                            isSubmitting = isSubmitting,
                            onSubmit = onSubmit,
                        )
                    }
                    if (filteredRows.size > visibleRows.size) {
                        Text(
                            "${filteredRows.size - visibleRows.size} weitere Treffer. Bitte Suche verfeinern.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamTimeRowCard(
    row: TeamTimeRow,
    expanded: Boolean,
    onToggle: () -> Unit,
    editDayAction: ModuleAction?,
    isSubmitting: Boolean,
    onSubmit: (ModuleAction, Map<String, String>) -> Unit,
) {
    val status = when {
        row.problemCount > 0 -> ItemStatusChip("${row.problemCount} Problem", Color(0xFF4A1F25), Color(0xFFFCA5A5))
        row.weekBalanceMinutes < 0 -> ItemStatusChip("Minus", Color(0xFF4B3413), Color(0xFFFBBF24))
        else -> ItemStatusChip("OK", Color(0xFF123D2F), Color(0xFF34D399))
    }
    val cardModifier = if (expanded) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().clickable(onClick = onToggle)
    val headerModifier = if (expanded) Modifier.fillMaxWidth().clickable(onClick = onToggle) else Modifier.fillMaxWidth()
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = cardModifier,
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(width = 4.dp, height = if (expanded) 430.dp else 74.dp).background(row.color, RoundedCornerShape(999.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(headerModifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(row.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(row.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    StatusPill(status)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactFact("Ist", formatHumanMinutes(row.weekWorkedMinutes), Modifier.weight(1f))
                    CompactFact("Soll", formatHumanMinutes(row.weekExpectedMinutes), Modifier.weight(1f))
                    CompactFact("Saldo", formatSignedHumanMinutes(row.weekBalanceMinutes), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactFact("Gesamt", formatSignedHumanMinutes(row.totalBalanceMinutes), Modifier.weight(1f))
                    CompactFact("Stempel", row.days.sumOf { it.entryCount }.toString(), Modifier.weight(1f))
                    CompactFact("Tage", row.days.count { it.entryCount > 0 || it.workedMinutes > 0 }.toString(), Modifier.weight(1f))
                }
                if (expanded) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    TeamDayDetails(row, editDayAction, isSubmitting, onSubmit)
                    TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
                        Text("Woche schliessen")
                    }
                } else {
                    Text("Antippen für Wochenansicht und Korrekturen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TeamDayDetails(
    row: TeamTimeRow,
    editDayAction: ModuleAction?,
    isSubmitting: Boolean,
    onSubmit: (ModuleAction, Map<String, String>) -> Unit,
) {
    var editingDate by remember(row.username, row.days.firstOrNull()?.date) { mutableStateOf<LocalDate?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        row.days.sortedBy { it.date }.forEach { day ->
            Column(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${dayShortLabel(day.date)} ${day.date.format(DateTimeFormatter.ofPattern("dd.MM."))}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (day.needsCorrection) {
                        StatusPill(ItemStatusChip("Korrektur", Color(0xFF4A1F25), Color(0xFFFCA5A5)))
                    } else if (day.isOpen) {
                        StatusPill(ItemStatusChip("Offen", Color(0xFF26315F), Color(0xFFC7D2FE)))
                    }
                }
                Text(
                    "Ist ${formatHumanMinutes(day.workedMinutes)} | Pause ${formatHumanMinutes(day.breakMinutes)} | ${day.entryCount} Stempel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Start ${day.firstStart ?: "-"} | Ende ${day.lastEnd ?: "-"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (day.punchLines.isEmpty()) {
                    Text("Keine Stempel an diesem Tag.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    day.punchLines.take(4).forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                OutlinedButton(
                    onClick = { editingDate = if (editingDate == day.date) null else day.date },
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = ButtonDefaults.ContentPadding,
                ) {
                    Text(if (editingDate == day.date) "Korrektur schliessen" else "Tag korrigieren", style = MaterialTheme.typography.labelMedium)
                }
                if (editingDate == day.date) {
                    TeamDayCorrectionPanel(
                        day = day,
                        action = editDayAction,
                        isSubmitting = isSubmitting,
                        onSubmit = onSubmit,
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamDayCorrectionPanel(
    day: TeamDaySummary,
    action: ModuleAction?,
    isSubmitting: Boolean,
    onSubmit: (ModuleAction, Map<String, String>) -> Unit,
) {
    val initialTimes = remember(day.username, day.date, day.punchLines) { day.initialPunchTimes() }
    var startOne by remember(day.username, day.date, day.punchLines) { mutableStateOf(initialTimes.getOrNull(0)?.first.orEmpty()) }
    var endOne by remember(day.username, day.date, day.punchLines) { mutableStateOf(initialTimes.getOrNull(0)?.second.orEmpty()) }
    var startTwo by remember(day.username, day.date, day.punchLines) { mutableStateOf(initialTimes.getOrNull(1)?.first.orEmpty()) }
    var endTwo by remember(day.username, day.date, day.punchLines) { mutableStateOf(initialTimes.getOrNull(1)?.second.orEmpty()) }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactFact("Mitarbeiter", day.username, Modifier.weight(1f))
                CompactFact("Datum", day.date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), Modifier.weight(1f))
            }
            Text(
                "START und ENDE müssen sich abwechseln. Bestehende Einträge können hier angepasst oder ersetzt werden.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        startOne = ""
                        endOne = ""
                        startTwo = ""
                        endTwo = ""
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = ButtonDefaults.ContentPadding,
                ) {
                    Text("Leerer Tag", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = {
                        startOne = "08:00"
                        endOne = "17:00"
                        startTwo = ""
                        endTwo = ""
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = ButtonDefaults.ContentPadding,
                ) {
                    Text("08-17", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CorrectionTimeField("Start 1", startOne, !isSubmitting, Modifier.weight(1f)) { startOne = it }
                CorrectionTimeField("Ende 1", endOne, !isSubmitting, Modifier.weight(1f)) { endOne = it }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CorrectionTimeField("Start 2", startTwo, !isSubmitting, Modifier.weight(1f)) { startTwo = it }
                CorrectionTimeField("Ende 2", endTwo, !isSubmitting, Modifier.weight(1f)) { endTwo = it }
            }
            Button(
                onClick = {
                    action?.let {
                        onSubmit(
                            it,
                            mapOf(
                                "targetUsername" to day.username,
                                "date" to day.date.toString(),
                                "entries" to buildAdminEditEntriesJson(day.date, startOne, endOne, startTwo, endTwo),
                            ),
                        )
                    }
                },
                enabled = action != null && !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(if (isSubmitting) "Speichert..." else "Tag speichern")
            }
            if (action == null) {
                Text("Korrektur-Aktion ist für diesen Bereich nicht geladen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun CorrectionTimeField(
    label: String,
    value: String,
    enabled: Boolean,
    modifier: Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(5)) },
        enabled = enabled,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        placeholder = { Text("HH:mm") },
    )
}

private fun buildTeamTimeRows(
    time: ModuleEndpointResult?,
    employees: ModuleEndpointResult?,
    balances: ModuleEndpointResult?,
    weekly: ModuleEndpointResult?,
    weekStart: LocalDate,
): List<TeamTimeRow> {
    val currentWeekStart = startOfWeek(LocalDate.now())
    val weekEnd = weekStart.plusDays(6)
    val daySummaries = time?.items.orEmpty()
        .mapNotNull { it.toTeamDaySummary() }
        .filter { !it.date.isBefore(weekStart) && !it.date.isAfter(weekEnd) }
    val employeeByUsername = employees?.items.orEmpty()
        .mapNotNull { item -> item.usernameValue()?.let { it to item } }
        .toMap()
    val balanceByUsername = balances?.items.orEmpty()
        .mapNotNull { item -> item.usernameValue()?.let { it to item.detailInt("Saldo Minuten", "Tracking Balance", "trackingBalance") } }
        .toMap()
    val weeklyByUsername = weekly?.items.orEmpty()
        .mapNotNull { item -> item.usernameValue()?.let { it to item.detailInt("Wochensaldo Minuten", "Weekly Balance", "weeklyBalance") } }
        .toMap()
    val usernames = (employeeByUsername.keys + daySummaries.map { it.username } + balanceByUsername.keys + weeklyByUsername.keys)
        .filter { it.isNotBlank() }
        .distinct()

    return usernames.map { username ->
        val employee = employeeByUsername[username]
        val dayByDate = daySummaries
            .filter { it.username == username }
            .associateBy { it.date }
        val userDays = (0..6).map { offset ->
            val date = weekStart.plusDays(offset.toLong())
            dayByDate[date] ?: emptyTeamDaySummary(username, date)
        }
        val actual = userDays.sumOf { it.workedMinutes }
        val weeklyBalance = if (weekStart == currentWeekStart) weeklyByUsername[username] else null
        val expected = weeklyBalance?.let { (actual - it).coerceAtLeast(0) } ?: employee.estimatedWeeklyExpectedMinutes()
        val totalBalance = balanceByUsername[username] ?: employee?.detailInt("Saldo Minuten", "Tracking Balance", "trackingBalance") ?: 0
        val problemCount = userDays.count { it.needsCorrection }
        TeamTimeRow(
            username = username,
            displayName = employee.displayNameFor(username),
            color = colorFromHex(employee?.detailValue("Farbe", "Color", "color"), MaterialThemeFallbackColor(username)),
            weekWorkedMinutes = actual,
            weekExpectedMinutes = expected,
            weekBalanceMinutes = weeklyBalance ?: (actual - expected),
            totalBalanceMinutes = totalBalance,
            problemCount = problemCount,
            days = userDays,
        )
    }.sortedWith(compareByDescending<TeamTimeRow> { it.problemCount > 0 || it.weekBalanceMinutes < 0 }.thenBy { it.displayName.lowercase() })
}

private fun emptyTeamDaySummary(username: String, date: LocalDate) = TeamDaySummary(
    username = username,
    date = date,
    workedMinutes = 0,
    breakMinutes = 0,
    needsCorrection = false,
    entryCount = 0,
    firstStart = null,
    lastEnd = null,
    isOpen = false,
    punchLines = emptyList(),
    editEntriesJson = "[]",
)

private fun ModuleEndpointResult.isLoadingLike(): Boolean =
    status == ModuleRequestStatus.EMPTY && count == null

private fun ModuleListItem.toTeamDaySummary(): TeamDaySummary? {
    val username = detailValue("Benutzer", "Username", "username") ?: value.takeIf { it.isNotBlank() } ?: return null
    val date = detailValue("Datum", "Date", "date")?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    val punchLines = details.filter { it.label.startsWith("Stempel ") }.map { it.value }
    return TeamDaySummary(
        username = username,
        date = date,
        workedMinutes = detailInt("Ist Minuten", "Worked Minutes", "workedMinutes") ?: 0,
        breakMinutes = detailInt("Pause Minuten", "Break Minutes", "breakMinutes") ?: 0,
        needsCorrection = detailBool("Korrektur nötig", "Needs Correction", "needsCorrection"),
        entryCount = detailValue("Stempel", "Entries", "entries")?.substringBefore(' ')?.toIntOrNull() ?: 0,
        firstStart = detailValue("Start")?.toShortTime(),
        lastEnd = detailValue("Ende")?.toShortTime(),
        isOpen = detailBool("Offen", "Open", "isOpen"),
        punchLines = punchLines,
        editEntriesJson = detailValue("Einträge JSON", "Entries JSON", "entriesJson")
            ?: punchLinesToAdminEditJson(date, punchLines),
    )
}

private fun punchLinesToAdminEditJson(date: LocalDate, punchLines: List<String>): String {
    val entries = punchLines.mapNotNull { line ->
        val parts = line.split(" - ")
        val time = parts.getOrNull(0)?.takeIf { Regex("\\d{2}:\\d{2}").matches(it) } ?: return@mapNotNull null
        val type = parts.getOrNull(1)?.uppercase()?.takeIf { it == "START" || it == "ENDE" } ?: return@mapNotNull null
        """  {"entryTimestamp":"${date}T$time:00","punchType":"$type","source":"ADMIN_CORRECTION","correctedByUser":true}"""
    }
    return if (entries.isEmpty()) "[]" else entries.joinToString(separator = ",\n", prefix = "[\n", postfix = "\n]")
}

private fun TeamDaySummary.initialPunchTimes(): List<Pair<String, String>> {
    val pairs = mutableListOf<Pair<String, String>>()
    var pendingStart: String? = null
    punchLines.mapNotNull(::punchLineTimeAndType).forEach { (time, type) ->
        if (type == "START") {
            pendingStart = time
        } else if (type == "ENDE") {
            pairs += (pendingStart.orEmpty() to time)
            pendingStart = null
        }
    }
    pendingStart?.let { pairs += (it to "") }
    return pairs.take(2)
}

private fun punchLineTimeAndType(line: String): Pair<String, String>? {
    val parts = line.split(" - ")
    val time = parts.getOrNull(0)?.takeIf { Regex("\\d{2}:\\d{2}").matches(it) } ?: return null
    val type = parts.getOrNull(1)?.uppercase()?.takeIf { it == "START" || it == "ENDE" } ?: return null
    return time to type
}

private fun buildAdminEditEntriesJson(
    date: LocalDate,
    startOne: String,
    endOne: String,
    startTwo: String,
    endTwo: String,
): String {
    val entries = listOf(
        "START" to normalizeClockTime(startOne),
        "ENDE" to normalizeClockTime(endOne),
        "START" to normalizeClockTime(startTwo),
        "ENDE" to normalizeClockTime(endTwo),
    ).mapNotNull { (type, time) ->
        time?.let {
            """  {"entryTimestamp":"${date}T$it:00","punchType":"$type","source":"ADMIN_CORRECTION","correctedByUser":true}"""
        }
    }
    return if (entries.isEmpty()) "[]" else entries.joinToString(separator = ",\n", prefix = "[\n", postfix = "\n]")
}

private fun normalizeClockTime(value: String): String? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return null
    val match = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(trimmed) ?: return null
    val hour = match.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
    val minute = match.groupValues[2].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    return "%02d:%02d".format(hour, minute)
}

private fun ModuleListItem.usernameValue(): String? =
    detailValue("Benutzer", "Username", "username") ?: value.takeIf { it.isNotBlank() && !it.all(Char::isDigit) }

private fun ModuleListItem.employeeDisplayName(): String =
    label.takeIf { it.isNotBlank() && it != id && it != value }
        ?: listOfNotNull(detailValue("Vorname", "First Name", "firstName"), detailValue("Nachname", "Last Name", "lastName"))
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
        ?: usernameValue()
        ?: "#$id"

private fun ModuleListItem.isInTimeOverview(): Boolean =
    detailValue("Zeitübersicht", "In Zeitübersicht", "includeInTimeTracking")
        ?.let { value -> value.isPositiveValue() || value.equals("aktiv", ignoreCase = true) }
        ?: true

private fun ModuleListItem?.displayNameFor(username: String): String =
    this?.label?.takeIf { it.isNotBlank() && it != id && it != value } ?: username

private fun ModuleListItem?.estimatedWeeklyExpectedMinutes(): Int {
    if (this == null || detailBool("Stundenlohn", "Is Hourly", "isHourly")) return 0
    val dailyHours = detailValue("Sollstunden", "Daily Work Hours", "dailyWorkHours")?.toDoubleOrNull() ?: 0.0
    val expectedDays = detailInt("Arbeitstage", "Expected Work Days", "expectedWorkDays") ?: 5
    return Math.round(dailyHours * 60.0 * expectedDays.coerceIn(0, 7)).toInt()
}

private fun ModuleListItem.detailValue(vararg labels: String): String? =
    labels.firstNotNullOfOrNull { label ->
        details.firstOrNull { it.label.equals(label, ignoreCase = true) }?.value
    }

private fun ModuleListItem.detailInt(vararg labels: String): Int? =
    detailValue(*labels)?.trim()?.substringBefore(' ')?.toDoubleOrNull()?.let { Math.round(it).toInt() }

private fun ModuleListItem.detailBool(vararg labels: String): Boolean =
    detailValue(*labels)?.let { value ->
        value.equals("ja", ignoreCase = true) ||
            value.equals("true", ignoreCase = true) ||
            value.equals("yes", ignoreCase = true)
    } == true

private fun String.toShortTime(): String? =
    takeIf { it.isNotBlank() && it != "null" }?.take(5)

private fun MaterialThemeFallbackColor(seed: String): Color {
    val colors = listOf(
        Color(0xFF2563EB),
        Color(0xFF0F766E),
        Color(0xFFB45309),
        Color(0xFF7C3AED),
        Color(0xFFDB2777),
        Color(0xFF0891B2),
    )
    val index = kotlin.math.abs(seed.hashCode()) % colors.size
    return colors[index]
}

private fun colorFromHex(value: String?, fallback: Color): Color {
    val hex = value?.trim()?.removePrefix("#") ?: return fallback
    if (!Regex("^[0-9A-Fa-f]{6}$").matches(hex)) return fallback
    val rgb = hex.toLongOrNull(16) ?: return fallback
    return Color(0xFF000000L or rgb)
}

private data class DashboardTab(val id: String, val label: String, val count: Int = 0)

@Composable
private fun DashboardTabBar(tabs: List<DashboardTab>, selectedId: String, onSelect: (String) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tabs.forEach { tab ->
                val label = if (tab.count > 0) "${tab.label} ${tab.count}" else tab.label
                if (tab.id == selectedId) {
                    Button(
                        onClick = { onSelect(tab.id) },
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = ButtonDefaults.ContentPadding,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475BFF), contentColor = Color.White),
                    ) { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                } else {
                    OutlinedButton(
                        onClick = { onSelect(tab.id) },
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                }
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
    weekly: ModuleEndpointResult?,
    onOpenTime: () -> Unit,
    onOpenRequests: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenModules: () -> Unit,
) {
    val pendingCorrections = pendingItemCount(corrections)
    val pendingVacation = pendingItemCount(vacation)
    val pendingTotal = pendingCorrections + pendingVacation
    val balanceRows = remember(balances, weekly) { webBalanceRows(balances, weekly) }
    val problemRows = balanceRows.filter { it.minutes < 0 }
    val averageBalance = if (balanceRows.isNotEmpty()) balanceRows.sumOf { it.minutes } / balanceRows.size else 0
    val topBalance = balanceRows.maxByOrNull { it.minutes }
    val openCorrectionRequests = corrections?.items.orEmpty().filter { it.isPendingApprovalItem() }
    val openVacationRequests = vacation?.items.orEmpty().filter { it.isPendingApprovalItem() }
    val requests = (
        openCorrectionRequests.map { "Korrektur" to it } +
            openVacationRequests.map { "Urlaub" to it }
        ).take(4)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("ÜBERSICHT", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Guten Morgen, ${user.displayName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Diese Woche gibt es $pendingTotal offene Anträge und ${problemRows.size} Zeitprobleme.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Heute", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    Text(LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        WebCockpitPanel("Was jetzt wichtig ist", "Action Center") {
            WebActionCard("Offene Aufgaben", pendingTotal.toString(), "Anträge prüfen", "$pendingVacation Urlaub - $pendingCorrections Korrekturen", Color(0xFFF59E0B), onOpenRequests)
            WebActionCard("Zeitprobleme", problemRows.size.toString(), "Problemfälle", "${problemRows.size} negative Salden", Color(0xFFF87171), onOpenTime)
            WebActionCard("Team-Saldo", formatSignedHumanMinutes(averageBalance), "Durchschnitt", "${problemRows.size} negative Salden", Color(0xFFF87171), onOpenTime)
            WebActionCard("Top-Saldo", topBalance?.let { formatSignedHumanMinutes(it.minutes) } ?: "0h 00m", topBalance?.displayName ?: "Unbekannt", "Höchster aktueller Saldo", Color(0xFF60A5FA), onOpenTime)
        }

        WebCockpitPanel("Offene Anträge", "Antragscenter") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminWorkMetric("Korrekturen", pendingCorrections.toString(), Modifier.weight(1f))
                AdminWorkMetric("Urlaub", pendingVacation.toString(), Modifier.weight(1f))
            }
            Button(
                onClick = onOpenRequests,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(6.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text("Anträge bearbeiten", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (requests.isEmpty()) {
                Text("Keine offenen Anträge.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            } else {
                requests.forEach { (kind, item) -> WebOpenRequestRow(kind = kind, item = item, onOpen = onOpenRequests) }
            }
        }

        WebCockpitPanel("Admin-Module", "Schnellzugriff", actionLabel = "Alle Module", onAction = onOpenModules) {
            listOf(
                "Team" to "Benutzerverwaltung",
                "HR" to "Abrechnung",
                "Plan" to "Dienstplan",
                "Zeit" to "Zeitübersicht",
                "Import" to "Zeiten importieren",
                "Setup" to "Firmeneinstellungen",
            ).forEach { (code, title) ->
                WebModuleShortcut(code, title, if (title == "Zeitübersicht") onOpenTime else onOpenModules)
            }
        }
    }
}

private data class WebBalanceRow(val displayName: String, val username: String, val minutes: Int)

private fun webBalanceRows(balances: ModuleEndpointResult?, weekly: ModuleEndpointResult?): List<WebBalanceRow> {
    val source = balances?.items?.takeIf { it.isNotEmpty() } ?: weekly?.items.orEmpty()
    return source.mapNotNull { item ->
        val username = item.usernameValue() ?: item.value.takeIf { it.isNotBlank() } ?: item.label
        val minutes = item.detailInt("Saldo Minuten", "Tracking Balance", "trackingBalance", "Wochensaldo Minuten", "Weekly Balance", "weeklyBalance")
            ?: item.detail?.substringAfterLast(' ')?.toIntOrNull()
            ?: return@mapNotNull null
        WebBalanceRow(displayName = item.label.ifBlank { username }, username = username, minutes = minutes)
    }
}

@Composable
private fun WebCockpitPanel(
    title: String,
    eyebrow: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(eyebrow.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                if (actionLabel != null && onAction != null) {
                    SmallWebButton(actionLabel, onAction)
                }
            }
            content()
        }
    }
}

@Composable
private fun WebActionCard(
    eyebrow: String,
    value: String,
    title: String,
    meta: String,
    tone: Color,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(4.dp).height(124.dp).background(tone))
            Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onClick, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().height(34.dp), contentPadding = ButtonDefaults.ContentPadding) {
                    Text(if (title.contains("Saldo")) "Analyse öffnen" else title, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun WebCriticalRow(row: WebBalanceRow) {
    val tone = when {
        row.minutes <= -480 -> Color(0xFFF87171)
        row.minutes < 0 -> Color(0xFFF59E0B)
        row.minutes >= 480 -> Color(0xFF60A5FA)
        else -> Color(0xFF4ADE80)
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(4.dp).height(70.dp).background(tone))
            Column(Modifier.weight(1f).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.displayName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatSignedHumanMinutes(row.minutes), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                StatusPill(
                    when {
                        row.minutes <= -480 -> ItemStatusChip("Kritisch", Color(0xFF4A1F25), Color(0xFFFCA5A5))
                        row.minutes < 0 -> ItemStatusChip("Negativ", Color(0xFF4B3413), Color(0xFFFBBF24))
                        row.minutes >= 480 -> ItemStatusChip("Überstunden hoch", Color(0xFF103544), Color(0xFF67E8F9))
                        else -> ItemStatusChip("OK", Color(0xFF123D2F), Color(0xFF34D399))
                    },
                )
            }
        }
    }
}

@Composable
private fun WebPreviewRow(item: ModuleListItem, tone: Color) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(4.dp).height(66.dp).background(tone))
            Column(Modifier.weight(1f).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                item.detail?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun WebOpenRequestRow(kind: String, item: ModuleListItem, onOpen: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(4.dp).height(82.dp).background(Color(0xFFF59E0B)))
            Column(Modifier.weight(1f).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("$kind: ${item.label}", fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(item.adminWorkSubtitle(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    StatusPill(ItemStatusChip("Offen", Color(0xFF26315F), Color(0xFFC7D2FE)))
                }
                Text("Antippen zum Bearbeiten", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WebModuleShortcut(code: String, title: String, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(code, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
            Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SmallWebButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(6.dp), contentPadding = ButtonDefaults.ContentPadding, modifier = Modifier.height(32.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
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
            Text("${items.size} Einträge", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(8.dp))
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
    Surface(color = status.containerColor, shape = RoundedCornerShape(999.dp), border = BorderStroke(1.dp, status.contentColor.copy(alpha = 0.24f))) {
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
        detail.label in setOf("Status", "Genehmigt", "Abgelehnt", "Aktiv", "Bezahlt", "Gekündigt", "Zeitübersicht")
    }?.value?.takeIf { it.isNotBlank() }
    explicitStatus?.let { return statusChipFor(it, allowUnknown = true) }
    val detailStatus = detail?.substringBefore("|")?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return statusChipFor(detailStatus, allowUnknown = false)
}

private fun statusChipFor(label: String, allowUnknown: Boolean): ItemStatusChip? {
    return when (label.lowercase()) {
        "genehmigt", "approved", "active", "aktiv", "bezahlt", "paid", "ja", "true" ->
            ItemStatusChip(label.toAppStatusLabel(), Color(0xFF123D2F), Color(0xFF34D399))
        "offen", "open", "pending", "neu", "new" ->
            ItemStatusChip(label.toAppStatusLabel(), Color(0xFF26315F), Color(0xFFC7D2FE))
        "abgelehnt", "denied", "rejected", "false", "nein", "inaktiv", "inactive" ->
            ItemStatusChip(label.toAppStatusLabel(), Color(0xFF4A1F25), Color(0xFFFCA5A5))
        "gesendet", "transmitted", "completed", "done", "geschlossen", "closed" ->
            ItemStatusChip(label.toAppStatusLabel(), Color(0xFF103544), Color(0xFF67E8F9))
        else -> if (allowUnknown) ItemStatusChip(label.toAppStatusLabel(), Color(0xFF252830), Color(0xFFA0A4B4)) else null
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
        endpoint == null || endpoint.status == ModuleRequestStatus.ERROR -> "Lädt"
        endpoint.status == ModuleRequestStatus.FORBIDDEN -> "Gesperrt"
        endpoint.status == ModuleRequestStatus.EMPTY -> "Leer"
        else -> "${endpoint.count ?: 0} Einträge"
    }

@Composable
private fun EndpointResultRow(endpoint: ModuleEndpointResult) {
    val color = when (endpoint.status) {
        ModuleRequestStatus.READY -> Color(0xFF34D399)
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
                    Text("Öffnen")
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
    initialValues: Map<String, String> = emptyMap(),
) {
    var values by remember(action.id, initialValues) {
        mutableStateOf(action.fields.associate { it.key to (initialValues[it.key] ?: it.defaultValue) })
    }
    var pendingConfirmation by remember(action.id) { mutableStateOf<Map<String, String>?>(null) }
    pendingConfirmation?.let { pendingValues ->
        AlertDialog(
            onDismissRequest = { pendingConfirmation = null },
            title = { Text("Aktion bestätigen") },
            text = { Text("${action.title} wirklich ausführen? Diese Aktion kann Daten freigeben, ablehnen, senden oder löschen.") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingConfirmation = null
                        onSubmit(action, pendingValues)
                    },
                    enabled = !isSubmitting,
                ) {
                    Text("Bestätigen")
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
        id.contains("payout", ignoreCase = true) ||
        id in setOf("payslip-schedule-all", "payslip-schedule-user") ||
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
                    "${field.label}: ${selected?.let(::optionLabel) ?: value.ifBlank { "Auswählen" }}",
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
        field.key == "id" && action.id in setOf("time-entry-approve", "time-entry-revoke", "time-entry-customer", "time-entry-project") -> listOf("Zeitübersicht")
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
        field.key == "id" && action.id in setOf("payment-batch-approve", "payment-batch-transmit") -> listOf("Zahlungsläufe", "Offene Läufe")
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.size(width = 4.dp, height = 44.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(999.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            item,
                            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun InlineMetric(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CompactFact(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CardBox(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun EmptyState(text: String) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MessageCard(text: String, isError: Boolean) {
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
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
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                ChronoMark()
                Column(verticalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.weight(1f)) {
                    Text("Chrono", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("Mobile App", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text(
                    statusText,
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(9.dp).background(color, CircleShape))
}

@Composable
private fun ChronoMark() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.size(46.dp),
    ) {
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
private fun formatHumanMinutes(minutes: Int): String {
    val absMinutes = kotlin.math.abs(minutes)
    return "${absMinutes / 60}h ${"%02d".format(absMinutes % 60)}m"
}
private fun formatSignedHumanMinutes(minutes: Int): String =
    (if (minutes >= 0) "+" else "-") + formatHumanMinutes(minutes)
private fun Double.formatHours(): String = if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this)
private fun punchLabel(type: PunchType): String = if (type == PunchType.START) "Start" else if (type == PunchType.ENDE) "Ende" else "Unbekannt"
private fun sourceLabel(source: String?): String = when (source) { "NFC_SCAN" -> "NFC"; "SYSTEM_AUTO_END" -> "Automatisch"; "MANUAL_PUNCH" -> "Manuell"; else -> "Stempel" }
private fun taskLabel(task: TaskOption): String = if (task.billable) "${task.name} (verrechenbar)" else task.name
private fun userModelLabel(user: UserProfile): String = when { user.isHourly -> "Stundenlohn"; user.isPercentage -> "Pensum ${user.workPercentage}%"; else -> "Normal" }
private fun userInitials(user: UserProfile): String {
    val parts = listOfNotNull(user.firstName, user.lastName).filter { it.isNotBlank() }
    return parts.take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { user.username.take(2).uppercase() }
}

private fun moduleRows(section: AppSection, user: UserProfile): List<Pair<String, String>> =
    listOf("Bereich" to section.group, "Benutzer" to user.displayName, "Status" to "App Ansicht", "Rollen" to user.roles.joinToString(", ").ifBlank { "-" })
