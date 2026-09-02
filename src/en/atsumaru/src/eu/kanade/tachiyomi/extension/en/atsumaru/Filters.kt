package eu.kanade.tachiyomi.extension.en.atsumaru

import eu.kanade.tachiyomi.source.model.Filter

internal const val DEFAULT_SORT_BY = "dateAdded:desc"

internal val FilterListSeparator = Filter.Separator()

internal interface TypesenseFilter {
    fun toConditions(): List<String>
}

private class SortOption(
    val name: String,
    val value: String,
)

private val SORT_OPTIONS = listOf(
    SortOption("Title", "title"),
    SortOption("Views", "views"),
    SortOption("Trending", "trending"),
    SortOption("Date added", "dateAdded"),
    SortOption("Release date", "releaseDate"),
    SortOption("Rating", "mbRating"),
)

internal class SortFilter :
    Filter.Sort(
        "Sort by",
        SORT_OPTIONS.map(SortOption::name).toTypedArray(),
        Selection(3, false),
    ) {
    val sortBy: String
        get() {
            val selection = state ?: Selection(3, false)
            val direction = if (selection.ascending) "asc" else "desc"
            return "${SORT_OPTIONS[selection.index].value}:$direction"
        }
}

internal open class MultiSelectOption(
    name: String,
    val value: String,
) : Filter.CheckBox(name)

internal open class MultiSelectFilter(
    name: String,
    private val field: String,
    options: List<Pair<String, String>>,
) : Filter.Group<MultiSelectOption>(
    name,
    options.map { (name, value) -> MultiSelectOption(name, value) },
),
    TypesenseFilter {
    override fun toConditions(): List<String> {
        val selected = state.filter { it.state }.map { "`${it.value}`" }
        return if (selected.isEmpty()) {
            emptyList()
        } else {
            listOf("$field:=[${selected.joinToString()}]")
        }
    }
}

internal class TypeFilter(options: List<Pair<String, String>>) : MultiSelectFilter("Type", "type", options)

internal class StatusFilter(options: List<Pair<String, String>>) : MultiSelectFilter("Status", "status", options)

internal class ContentRatingFilter :
    MultiSelectFilter(
        "Content rating",
        "mbContentRating",
        listOf(
            "Safe" to "Safe",
            "Suggestive" to "Suggestive",
            "Erotica" to "Erotica",
        ),
    )

internal class IncludeExcludeOption(
    name: String,
    val value: String,
) : Filter.TriState(name)

internal open class IncludeExcludeFilter(
    name: String,
    private val field: String,
    options: List<Pair<String, String>>,
) : Filter.Group<IncludeExcludeOption>(
    name,
    options.map { (name, value) -> IncludeExcludeOption(name, value) },
),
    TypesenseFilter {
    override fun toConditions() = buildList {
        state.forEach { option ->
            when (option.state) {
                Filter.TriState.STATE_INCLUDE -> add("$field:=[`${option.value}`]")
                Filter.TriState.STATE_EXCLUDE -> add("$field:!=[`${option.value}`]")
            }
        }
    }
}

internal class GenreFilter(options: List<Pair<String, String>>) : IncludeExcludeFilter("Genres", "genreIds", options)

internal class TagFilter(options: List<Pair<String, String>>) : IncludeExcludeFilter("Tags", "tagIds", options)

internal class YearFilter :
    Filter.Text("Year"),
    TypesenseFilter {
    override fun toConditions(): List<String> {
        if (state.isEmpty()) return emptyList()

        val year = state.toIntOrNull()?.takeIf { it > 0 }
            ?: throw IllegalArgumentException("Year must be a positive integer")
        return listOf("releaseYear:=$year")
    }
}

internal class MinimumChapterFilter :
    Filter.Text("Minimum chapters"),
    TypesenseFilter {
    override fun toConditions(): List<String> {
        if (state.isEmpty()) return emptyList()

        val minimum = state.toIntOrNull()?.takeIf { it > 0 }
            ?: throw IllegalArgumentException("Minimum chapters must be a positive integer")
        return listOf("chapterCount:>=$minimum")
    }
}

internal class OfficialTranslationFilter :
    Filter.CheckBox("Official translation only"),
    TypesenseFilter {
    override fun toConditions() = if (state) {
        listOf("officialTranslation:=true")
    } else {
        emptyList()
    }
}
