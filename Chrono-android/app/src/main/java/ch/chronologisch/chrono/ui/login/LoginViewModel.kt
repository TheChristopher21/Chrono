package ch.chronologisch.chrono.ui.login

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ch.chronologisch.chrono.BuildConfig
import ch.chronologisch.chrono.data.AuthException
import ch.chronologisch.chrono.data.AuthenticatedSession
import ch.chronologisch.chrono.data.ChronoAuthRepository
import ch.chronologisch.chrono.data.RegistrationApplication
import ch.chronologisch.chrono.data.SessionStore
import kotlinx.coroutines.launch

data class ContactRequestForm(
    val companyName: String = "",
    val contactName: String = "",
    val email: String = "",
    val phone: String = "",
    val employeeCount: String = "10",
    val additionalInfo: String = "",
    val termsAccepted: Boolean = false,
    val contactAccepted: Boolean = false,
)

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val isRestoringSession: Boolean = true,
    val errorMessage: String? = null,
    val contactFormOpen: Boolean = false,
    val contactForm: ContactRequestForm = ContactRequestForm(),
    val isSubmittingContact: Boolean = false,
    val contactMessage: String? = null,
    val contactError: String? = null,
    val session: AuthenticatedSession? = null,
)

class LoginViewModel(
    private val repository: ChronoAuthRepository,
) : ViewModel() {
    var uiState by mutableStateOf(LoginUiState())
        private set

    init {
        restoreSession()
    }

    fun updateUsername(value: String) {
        uiState = uiState.copy(username = value, errorMessage = null)
    }

    fun updatePassword(value: String) {
        uiState = uiState.copy(password = value, errorMessage = null)
    }

    fun setContactFormOpen(open: Boolean) {
        uiState = uiState.copy(contactFormOpen = open, contactMessage = null, contactError = null)
    }

    fun updateContactCompanyName(value: String) {
        uiState = uiState.copy(contactForm = uiState.contactForm.copy(companyName = value), contactError = null)
    }

    fun updateContactName(value: String) {
        uiState = uiState.copy(contactForm = uiState.contactForm.copy(contactName = value), contactError = null)
    }

    fun updateContactEmail(value: String) {
        uiState = uiState.copy(contactForm = uiState.contactForm.copy(email = value), contactError = null)
    }

    fun updateContactPhone(value: String) {
        uiState = uiState.copy(contactForm = uiState.contactForm.copy(phone = value), contactError = null)
    }

    fun updateContactEmployeeCount(value: String) {
        uiState = uiState.copy(contactForm = uiState.contactForm.copy(employeeCount = value.filter(Char::isDigit).take(3)), contactError = null)
    }

    fun updateContactAdditionalInfo(value: String) {
        uiState = uiState.copy(contactForm = uiState.contactForm.copy(additionalInfo = value), contactError = null)
    }

    fun updateContactTermsAccepted(value: Boolean) {
        uiState = uiState.copy(contactForm = uiState.contactForm.copy(termsAccepted = value), contactError = null)
    }

    fun updateContactAccepted(value: Boolean) {
        uiState = uiState.copy(contactForm = uiState.contactForm.copy(contactAccepted = value), contactError = null)
    }

    fun submitContactRequest() {
        val form = uiState.contactForm
        val employeeCount = form.employeeCount.toIntOrNull()
        when {
            form.companyName.isBlank() -> {
                uiState = uiState.copy(contactError = "Bitte Firmennamen eingeben.")
                return
            }
            form.contactName.isBlank() -> {
                uiState = uiState.copy(contactError = "Bitte Ansprechperson eingeben.")
                return
            }
            form.email.isBlank() -> {
                uiState = uiState.copy(contactError = "Bitte E-Mail eingeben.")
                return
            }
            employeeCount == null || employeeCount !in 1..200 -> {
                uiState = uiState.copy(contactError = "Bitte Mitarbeiterzahl zwischen 1 und 200 eingeben.")
                return
            }
            !form.termsAccepted || !form.contactAccepted -> {
                uiState = uiState.copy(contactError = "Bitte beide Bestätigungen aktivieren.")
                return
            }
        }

        uiState = uiState.copy(isSubmittingContact = true, contactError = null, contactMessage = null)
        viewModelScope.launch {
            uiState = try {
                repository.submitApplication(
                    RegistrationApplication(
                        companyName = form.companyName.trim(),
                        contactName = form.contactName.trim(),
                        email = form.email.trim(),
                        phone = form.phone.trim(),
                        additionalInfo = form.additionalInfo.trim(),
                        employeeCount = employeeCount,
                    ),
                )
                uiState.copy(
                    contactForm = ContactRequestForm(),
                    contactFormOpen = false,
                    isSubmittingContact = false,
                    contactMessage = "Danke, deine Anfrage wurde gesendet.",
                )
            } catch (ex: AuthException) {
                uiState.copy(isSubmittingContact = false, contactError = ex.message)
            } catch (_: Exception) {
                uiState.copy(isSubmittingContact = false, contactError = "Anfrage konnte nicht gesendet werden.")
            }
        }
    }

    fun login() {
        val username = uiState.username.trim()
        val password = uiState.password

        if (username.isBlank() || password.isBlank()) {
            uiState = uiState.copy(errorMessage = "Benutzername und Passwort eingeben.")
            return
        }

        uiState = uiState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            uiState = try {
                val session = repository.login(username, password)
                uiState.copy(
                    password = "",
                    isLoading = false,
                    session = session,
                )
            } catch (ex: AuthException) {
                uiState.copy(isLoading = false, errorMessage = ex.message)
            } catch (_: Exception) {
                uiState.copy(
                    isLoading = false,
                    errorMessage = "Verbindung zum Chrono-Backend fehlgeschlagen.",
                )
            }
        }
    }

    fun demoLogin() {
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            uiState = try {
                val session = repository.demoLogin()
                uiState.copy(
                    username = "",
                    password = "",
                    isLoading = false,
                    session = session,
                )
            } catch (ex: AuthException) {
                uiState.copy(isLoading = false, errorMessage = ex.message)
            } catch (_: Exception) {
                uiState.copy(
                    isLoading = false,
                    errorMessage = "Demo-Verbindung zum Chrono-Backend fehlgeschlagen.",
                )
            }
        }
    }

    fun logout() {
        repository.logout()
        uiState = LoginUiState(isRestoringSession = false)
    }

    private fun restoreSession() {
        viewModelScope.launch {
            uiState = try {
                val session = repository.restoreSession()
                uiState.copy(isRestoringSession = false, session = session)
            } catch (_: Exception) {
                uiState.copy(isRestoringSession = false)
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appContext = context.applicationContext
                    val repository = ChronoAuthRepository(
                        baseUrl = BuildConfig.API_BASE_URL,
                        sessionStore = SessionStore(appContext),
                    )
                    return LoginViewModel(repository) as T
                }
            }
    }
}
