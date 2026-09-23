package ch.chronologisch.chrono.data

data class AuthenticatedSession(
    val token: String,
    val user: UserProfile,
)

data class UserProfile(
    val username: String,
    val firstName: String?,
    val lastName: String?,
    val roles: List<String>,
    val dailyWorkHours: Double?,
    val trackingBalanceInMinutes: Int,
    val isHourly: Boolean,
    val isPercentage: Boolean,
    val workPercentage: Int,
    val customerTrackingEnabled: Boolean,
    val lastCustomerId: Long?,
    val lastCustomerName: String?,
    val companyFeatureKeys: Set<String>,
    val pagePermissions: Map<String, String>,
) {
    val displayName: String
        get() = listOfNotNull(firstName?.takeIf { it.isNotBlank() }, lastName?.takeIf { it.isNotBlank() })
            .joinToString(" ")
            .ifBlank { username }
}

class AuthException(message: String) : Exception(message)
