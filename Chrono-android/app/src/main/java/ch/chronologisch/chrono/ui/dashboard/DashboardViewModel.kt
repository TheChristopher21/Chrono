package ch.chronologisch.chrono.ui.dashboard

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ch.chronologisch.chrono.BuildConfig
import ch.chronologisch.chrono.data.AuthenticatedSession
import ch.chronologisch.chrono.data.CustomerOption
import ch.chronologisch.chrono.data.DailyTimeSummary
import ch.chronologisch.chrono.data.ModuleAction
import ch.chronologisch.chrono.data.ModuleRepository
import ch.chronologisch.chrono.data.ModuleSummary
import ch.chronologisch.chrono.data.ProjectOption
import ch.chronologisch.chrono.data.TaskOption
import ch.chronologisch.chrono.data.TimeTrackingException
import ch.chronologisch.chrono.data.TimeTrackingRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

data class DashboardUiState(
    val session: AuthenticatedSession? = null,
    val selectedSection: AppSection = AppSection.TIME,
    val history: List<DailyTimeSummary> = emptyList(),
    val moduleSummaries: Map<AppSection, ModuleSummary> = emptyMap(),
    val customers: List<CustomerOption> = emptyList(),
    val projects: List<ProjectOption> = emptyList(),
    val tasks: List<TaskOption> = emptyList(),
    val selectedCustomerId: Long? = null,
    val selectedProjectId: Long? = null,
    val selectedTaskId: Long? = null,
    val noteDraft: String = "",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingSelections: Boolean = false,
    val isPunching: Boolean = false,
    val isSavingNote: Boolean = false,
    val isSubmittingModuleAction: Boolean = false,
    val isSubmittingFeedback: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val now: LocalDateTime = LocalDateTime.now(),
)

class DashboardViewModel(
    private val repository: TimeTrackingRepository,
    private val moduleRepository: ModuleRepository,
) : ViewModel() {
    var uiState by mutableStateOf(DashboardUiState())
        private set

    init {
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                uiState = uiState.copy(now = LocalDateTime.now())
            }
        }
    }

    fun bindSession(session: AuthenticatedSession) {
        val currentSession = uiState.session
        if (currentSession?.token == session.token && currentSession.user.username == session.user.username) {
            return
        }

        val startSection = defaultSectionFor(session.user)
        uiState = DashboardUiState(
            session = session,
            selectedSection = startSection,
            selectedCustomerId = session.user.lastCustomerId,
            isLoading = startSection.needsPersonalTimeData(),
        )
        loadPersonalDataIfNeeded(startSection, silent = false)
        loadModule(startSection, force = true)
    }

    fun clear() {
        uiState = DashboardUiState()
    }

    fun refresh() {
        loadPersonalDataIfNeeded(uiState.selectedSection, silent = true)
        loadModule(uiState.selectedSection, force = true)
    }

    fun selectSection(section: AppSection) {
        val session = uiState.session
        val nextSection = if (session == null || section in AppSection.visibleFor(session.user)) {
            section
        } else {
            AppSection.TIME
        }
        uiState = uiState.copy(selectedSection = nextSection, errorMessage = null, infoMessage = null)
        loadPersonalDataIfNeeded(nextSection, silent = false)
        loadModule(nextSection)
    }

    fun selectCustomer(customerId: Long?) {
        val projectBelongsToCustomer = uiState.projects
            .firstOrNull { it.id == uiState.selectedProjectId }
            ?.customerId == customerId
        uiState = uiState.copy(
            selectedCustomerId = customerId,
            selectedProjectId = if (customerId == null || projectBelongsToCustomer) uiState.selectedProjectId else null,
            selectedTaskId = if (customerId == null || projectBelongsToCustomer) uiState.selectedTaskId else null,
            errorMessage = null,
        )
    }

    fun selectProject(projectId: Long?) {
        uiState = uiState.copy(
            selectedProjectId = projectId,
            selectedTaskId = null,
            tasks = emptyList(),
            errorMessage = null,
        )
        if (projectId != null) {
            loadTasks(projectId)
        }
    }

    fun selectTask(taskId: Long?) {
        uiState = uiState.copy(selectedTaskId = taskId, errorMessage = null)
    }

    fun updateNoteDraft(value: String) {
        uiState = uiState.copy(noteDraft = value, errorMessage = null)
    }

    fun saveDailyNote(date: LocalDate = LocalDate.now()) {
        val session = uiState.session ?: return
        uiState = uiState.copy(isSavingNote = true, errorMessage = null, infoMessage = null)
        viewModelScope.launch {
            try {
                repository.saveDailyNote(
                    token = session.token,
                    username = session.user.username,
                    date = date,
                    note = uiState.noteDraft,
                )
                uiState = uiState.copy(
                    isSavingNote = false,
                    infoMessage = "Tagesnotiz gespeichert.",
                )
                loadHistory(silent = true)
            } catch (ex: TimeTrackingException) {
                uiState = uiState.copy(isSavingNote = false, errorMessage = ex.message)
            } catch (_: Exception) {
                uiState = uiState.copy(
                    isSavingNote = false,
                    errorMessage = "Tagesnotiz konnte nicht gespeichert werden.",
                )
            }
        }
    }

    fun submitModuleAction(action: ModuleAction, values: Map<String, String>) {
        val session = uiState.session ?: return
        if (uiState.isSubmittingModuleAction) return

        uiState = uiState.copy(
            isSubmittingModuleAction = true,
            errorMessage = null,
            infoMessage = null,
        )

        viewModelScope.launch {
            val result = try {
                moduleRepository.submitModuleAction(action = action, values = values, session = session)
            } catch (_: Exception) {
                null
            }

            if (result == null) {
                uiState = uiState.copy(
                    isSubmittingModuleAction = false,
                    errorMessage = "Aktion konnte nicht ausgefuehrt werden.",
                )
                return@launch
            }

            uiState = uiState.copy(
                isSubmittingModuleAction = false,
                errorMessage = if (result.success) null else result.message,
                infoMessage = if (result.success) result.message else null,
            )

            if (result.success) {
                loadModule(uiState.selectedSection, force = true)
                loadSelections()
                if (action.refreshHistory) {
                    loadHistory(silent = true)
                }
            }
        }
    }

    fun submitAppFeedback(section: AppSection, message: String) {
        val session = uiState.session ?: return
        if (uiState.isSubmittingFeedback) return
        val trimmedMessage = message.trim()
        if (trimmedMessage.isBlank()) {
            uiState = uiState.copy(errorMessage = "Feedback fehlt.")
            return
        }

        uiState = uiState.copy(
            isSubmittingFeedback = true,
            errorMessage = null,
            infoMessage = null,
        )

        viewModelScope.launch {
            val result = try {
                moduleRepository.submitMobileFeedback(
                    section = section,
                    message = trimmedMessage,
                    session = session,
                    appVersionName = BuildConfig.VERSION_NAME,
                    appVersionCode = BuildConfig.VERSION_CODE,
                    deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}",
                )
            } catch (_: Exception) {
                null
            }

            if (result == null) {
                uiState = uiState.copy(
                    isSubmittingFeedback = false,
                    errorMessage = "Feedback konnte nicht gesendet werden.",
                )
                return@launch
            }

            uiState = uiState.copy(
                isSubmittingFeedback = false,
                errorMessage = if (result.success) null else result.message,
                infoMessage = if (result.success) result.message else null,
            )

            if (result.success && uiState.selectedSection == AppSection.SUPERADMIN_HOME) {
                loadModule(AppSection.SUPERADMIN_HOME, force = true)
            }
        }
    }

    private fun loadModule(section: AppSection, force: Boolean = false) {
        val session = uiState.session ?: return
        val visibleSections = AppSection.visibleFor(session.user)
        if (section !in visibleSections) {
            return
        }

        val existing = uiState.moduleSummaries[section]
        if (!force && existing != null && !existing.isLoading) {
            return
        }

        uiState = uiState.copy(
            moduleSummaries = uiState.moduleSummaries + (
                section to ModuleSummary(section = section, isLoading = true)
            ),
        )

        viewModelScope.launch {
            val tokenAtStart = session.token
            val summary = moduleRepository.loadModuleSummary(section = section, session = session)
            if (uiState.session?.token == tokenAtStart) {
                uiState = uiState.copy(
                    moduleSummaries = uiState.moduleSummaries + (section to summary),
                )
            }
        }
    }

    fun punch() {
        val session = uiState.session ?: return
        uiState = uiState.copy(isPunching = true, errorMessage = null, infoMessage = null)
        viewModelScope.launch {
            try {
                val entry = repository.punch(
                    token = session.token,
                    username = session.user.username,
                    customerId = uiState.selectedCustomerId,
                    projectId = uiState.selectedProjectId,
                    taskId = uiState.selectedTaskId,
                )
                uiState = uiState.copy(
                    isPunching = false,
                    infoMessage = when (entry.punchType.name) {
                        "START" -> "Eingestempelt."
                        "ENDE" -> "Ausgestempelt."
                        else -> "Stempelung gespeichert."
                    },
                )
                loadHistory(silent = true)
            } catch (ex: TimeTrackingException) {
                uiState = uiState.copy(isPunching = false, errorMessage = ex.message)
            } catch (_: Exception) {
                uiState = uiState.copy(
                    isPunching = false,
                    errorMessage = "Zeiterfassung konnte nicht geladen werden.",
                )
            }
        }
    }

    private fun defaultSectionFor(user: ch.chronologisch.chrono.data.UserProfile): AppSection {
        val visible = AppSection.visibleFor(user)
        return when {
            AppSection.SUPERADMIN_HOME in visible -> AppSection.SUPERADMIN_HOME
            AppSection.ADMIN_HOME in visible -> AppSection.ADMIN_HOME
            AppSection.TIME in visible -> AppSection.TIME
            else -> visible.firstOrNull() ?: AppSection.TIME
        }
    }

    private fun loadPersonalDataIfNeeded(section: AppSection, silent: Boolean) {
        if (section.needsPersonalTimeData()) {
            loadHistory(silent = silent)
        }
        if (section == AppSection.TIME) {
            loadSelections()
        }
    }

    private fun AppSection.needsPersonalTimeData(): Boolean =
        this == AppSection.TIME || this == AppSection.WORK_MODEL

    private fun loadSelections() {
        val session = uiState.session ?: return
        if (!session.user.customerTrackingEnabled) {
            uiState = uiState.copy(
                customers = emptyList(),
                projects = emptyList(),
                tasks = emptyList(),
                selectedCustomerId = null,
                selectedProjectId = null,
                selectedTaskId = null,
                isLoadingSelections = false,
            )
            return
        }

        uiState = uiState.copy(isLoadingSelections = true)
        viewModelScope.launch {
            try {
                val customers = repository.loadCustomers(session.token)
                val projects = repository.loadProjects(session.token)
                val selectedCustomerId = uiState.selectedCustomerId
                    ?.takeIf { id -> customers.any { it.id == id } }
                uiState = uiState.copy(
                    customers = customers,
                    projects = projects,
                    selectedCustomerId = selectedCustomerId,
                    isLoadingSelections = false,
                )
                uiState.selectedProjectId?.let { loadTasks(it) }
            } catch (_: Exception) {
                uiState = uiState.copy(
                    isLoadingSelections = false,
                    customers = emptyList(),
                    projects = emptyList(),
                    tasks = emptyList(),
                )
            }
        }
    }

    private fun loadTasks(projectId: Long) {
        val session = uiState.session ?: return
        viewModelScope.launch {
            uiState = try {
                uiState.copy(
                    tasks = repository.loadTasks(session.token, projectId),
                    selectedTaskId = null,
                )
            } catch (_: Exception) {
                uiState.copy(tasks = emptyList(), selectedTaskId = null)
            }
        }
    }

    private fun loadHistory(silent: Boolean) {
        val session = uiState.session ?: return
        uiState = uiState.copy(
            isLoading = !silent && uiState.history.isEmpty(),
            isRefreshing = silent,
            errorMessage = null,
        )
        viewModelScope.launch {
            uiState = try {
                val history = repository.loadHistory(
                    token = session.token,
                    username = session.user.username,
                )
                uiState.copy(
                    history = history,
                    noteDraft = history.firstOrNull { it.date == LocalDate.now() }?.dailyNote.orEmpty(),
                    isLoading = false,
                    isRefreshing = false,
                    now = LocalDateTime.now(),
                )
            } catch (ex: TimeTrackingException) {
                uiState.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = ex.message,
                )
            } catch (_: Exception) {
                uiState.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = "Zeiterfassung konnte nicht geladen werden.",
                )
            }
        }
    }

    companion object {
        fun factory(@Suppress("UNUSED_PARAMETER") context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DashboardViewModel(
                        repository = TimeTrackingRepository(BuildConfig.API_BASE_URL),
                        moduleRepository = ModuleRepository(BuildConfig.API_BASE_URL),
                    ) as T
                }
            }
    }
}
