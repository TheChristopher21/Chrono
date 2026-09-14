package ch.chronologisch.chrono.data

data class CustomerOption(
    val id: Long,
    val name: String,
)

data class ProjectOption(
    val id: Long,
    val name: String,
    val customerId: Long?,
)

data class TaskOption(
    val id: Long,
    val name: String,
    val billable: Boolean,
)
