@file:Suppress("SpellCheckingInspection")

package eu.kanade.tachiyomi.extension.pt.mangotoons

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
class FilterData(
    val formats: List<FilterEntry> = emptyList(),
    val statuses: List<FilterEntry> = emptyList(),
)

@Serializable
class FilterEntry(
    val name: String,
    val id: Int,
)

open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selectedValue get() = options[state].second
}

class FormatFilter(formats: List<FilterEntry>) :
    SelectFilter(
        "Formato",
        listOf("Todos" to "") + formats.map { it.name to it.id.toString() },
    )

class StatusFilter(statuses: List<FilterEntry>) :
    SelectFilter(
        "Status",
        listOf("Todos" to "") + statuses.map { it.name to it.id.toString() },
    )
