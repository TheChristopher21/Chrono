package ch.chronologisch.chrono.data

import ch.chronologisch.chrono.ui.dashboard.AppSection
import java.time.LocalDateTime

enum class ModuleRequestStatus {
    READY,
    EMPTY,
    FORBIDDEN,
    ERROR,
}

data class ModuleEndpointResult(
    val label: String,
    val path: String,
    val status: ModuleRequestStatus,
    val count: Int? = null,
    val preview: List<String> = emptyList(),
    val items: List<ModuleListItem> = emptyList(),
    val message: String? = null,
)

data class ModuleListItem(
    val id: String,
    val label: String,
    val detail: String? = null,
    val value: String = id,
    val details: List<ModuleItemDetail> = emptyList(),
)

data class ModuleItemDetail(
    val label: String,
    val value: String,
)

data class ModuleSummary(
    val section: AppSection,
    val endpoints: List<ModuleEndpointResult> = emptyList(),
    val actions: List<ModuleAction> = emptyList(),
    val isLoading: Boolean = false,
    val loadedAt: LocalDateTime? = null,
) {
    val readyEndpoints: Int
        get() = endpoints.count { it.status == ModuleRequestStatus.READY || it.status == ModuleRequestStatus.EMPTY }

    val totalCount: Int
        get() = endpoints.sumOf { it.count ?: 0 }
}

enum class ModuleActionMethod {
    POST,
    PATCH,
    PUT,
    DELETE,
}

enum class ModuleActionFieldType {
    TEXT,
    NUMBER,
    BOOLEAN,
    DATE,
    DATETIME,
    MULTILINE,
    PASSWORD,
}

data class ModuleActionField(
    val key: String,
    val label: String,
    val type: ModuleActionFieldType = ModuleActionFieldType.TEXT,
    val defaultValue: String = "",
    val required: Boolean = true,
)

data class ModuleAction(
    val id: String,
    val title: String,
    val description: String,
    val method: ModuleActionMethod,
    val pathTemplate: String,
    val bodyTemplate: String? = null,
    val fields: List<ModuleActionField> = emptyList(),
    val refreshHistory: Boolean = false,
)

data class ModuleActionSubmitResult(
    val success: Boolean,
    val message: String,
)
