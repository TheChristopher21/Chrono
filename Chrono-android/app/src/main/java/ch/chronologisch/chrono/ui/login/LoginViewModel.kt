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
import ch.chronologisch.chrono.data.SessionStore
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val isRestoringSession: Boolean = true,
    val errorMessage: String? = null,
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
