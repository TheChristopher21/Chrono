package ch.chronologisch.chrono.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class TimeTrackingEntry(
    val id: Long?,
    val entryTimestamp: LocalDateTime?,
    val punchType: PunchType,
    val source: String?,
    val customerName: String?,
    val projectName: String?,
    val taskName: String?,
    val durationMinutes: Int?,
    val description: String?,
    val correctedByUser: Boolean,
    val systemGeneratedNote: String?,
)

enum class PunchType {
    START,
    ENDE,
    UNKNOWN,
}

data class PrimaryTimes(
    val firstStartTime: LocalTime?,
    val lastEndTime: LocalTime?,
    val isOpen: Boolean,
)

data class DailyTimeSummary(
    val username: String,
    val date: LocalDate,
    val workedMinutes: Int,
    val breakMinutes: Int,
    val entries: List<TimeTrackingEntry>,
    val dailyNote: String?,
    val needsCorrection: Boolean,
    val primaryTimes: PrimaryTimes,
)

class TimeTrackingException(message: String) : Exception(message)
