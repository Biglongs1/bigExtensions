@file:Suppress("SpellCheckingInspection")

package eu.kanade.tachiyomi.extension.pt.mangalivreblog

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
class FilterData(
    val genres: List<GenreEntry> = emptyList(),
)

@Serializable
class GenreEntry(
    val name: String,
    val slug: String,
)

open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selectedValue get() = options[state].second
}

class OrderFilter :
    SelectFilter(
        "Ordenar por",
        listOf(
            "Recentes" to "recentes",
            "Populares" to "popular",
        ),
    )

class GenreFilter(genres: List<GenreEntry>) :
    SelectFilter(
        "Gênero",
        listOf("Todos" to "") + genres.map { it.name to it.slug },
    )
