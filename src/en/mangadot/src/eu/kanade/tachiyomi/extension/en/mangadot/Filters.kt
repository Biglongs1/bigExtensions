package eu.kanade.tachiyomi.extension.en.mangadot

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

class MangaDotFilters(data: FilterDataDto?) {
    private val genres = data?.genres.orEmpty().ifEmpty { DEFAULT_GENRES }
    private val statuses = data?.statuses.orEmpty().ifEmpty { DEFAULT_STATUSES }
    private val origins = data?.origins.orEmpty().ifEmpty { DEFAULT_ORIGINS }
    private val contentRatings = data?.contentRatings.orEmpty().ifEmpty { DEFAULT_CONTENT_RATINGS }
    private val tags = data?.tags.orEmpty().ifEmpty { DEFAULT_TAGS }

    fun getFilterList() = FilterList(
        buildList {
            add(SortFilter())
            add(Filter.Separator())
            add(GenreFilter(genres))
            add(TagFilter(tags))
            add(Filter.Separator())
            add(SelectQueryFilter("Status", "status", statuses.toOptions()))
            add(SelectQueryFilter("Origin", "origin", origins.toOptions()))
            add(ContentRatingFilter(contentRatings.toOptions()))
            add(Filter.Separator())
            add(Filter.Header("Release year"))
            add(PositiveIntegerFilter("From", "year_min"))
            add(PositiveIntegerFilter("To", "year_max"))
            add(MinimumRatingFilter())
            add(PositiveIntegerFilter("Minimum chapters", "min_chapters"))
            add(Filter.Separator())
            add(CheckboxQueryFilter("Long strip", "format", "longstrip"))
            add(CheckboxQueryFilter("Has volumes", "has_volumes", "1"))
            add(CheckboxQueryFilter("Has scanlator", "has_scanlator", "1"))
            add(Filter.Separator())
            add(TextQueryFilter("Author", "author"))
            add(TextQueryFilter("Artist", "artist"))
        },
    )

    companion object {
        private val DEFAULT_GENRES = listOf(
            "Action",
            "Adventure",
            "Boys Love",
            "Comedy",
            "Drama",
            "Ecchi",
            "Fantasy",
            "Girls Love",
            "Harem",
            "Historical",
            "Horror",
            "Josei",
            "Mature",
            "Mecha",
            "Mystery",
            "Psychological",
            "Romance",
            "School Life",
            "Sci-Fi",
            "Seinen",
            "Shoujo",
            "Shounen",
            "Slice of Life",
            "Smut",
            "Sports",
            "Supernatural",
            "Tragedy",
        )
        private val DEFAULT_STATUSES = listOf("Ongoing", "Completed")
        private val DEFAULT_ORIGINS = listOf("JP", "KR", "CN", "TW", "EN")
        private val DEFAULT_CONTENT_RATINGS = listOf("safe", "suggestive", "erotica", "pornographic")
        private val DEFAULT_TAGS = listOf(
            "Adaptation",
            "Age Gap",
            "Based on a Novel",
            "Based on a Web Novel",
            "Demons",
            "Female Lead",
            "Full Color",
            "Isekai",
            "Magic",
            "Male Lead",
            "Reincarnation",
            "School",
            "Web Comic",
        )
    }
}

private fun List<String>.toOptions() = listOf("Any" to "") + map { it to it }

private class SortOption(val name: String, val value: String)

class SortFilter :
    Filter.Sort(
        "Sort by",
        SORT_OPTIONS.map(SortOption::name).toTypedArray(),
        Selection(0, false),
    ),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        state?.let {
            builder.addQueryParameter("sortBy", SORT_OPTIONS[it.index].value)
            builder.addQueryParameter("sortOrder", if (it.ascending) "asc" else "desc")
        }
    }

    companion object {
        private val SORT_OPTIONS = listOf(
            SortOption("Relevance", "relevance"),
            SortOption("Latest updates", "latest"),
            SortOption("Alphabetical", "alphabetical"),
            SortOption("Most chapters", "chapters"),
            SortOption("Most viewed", "views"),
            SortOption("Most tracked", "tracked"),
            SortOption("Highest rated", "rating"),
        )
    }
}

private class TriStateOption(name: String, val value: String) : Filter.TriState(name)

private open class TriStateCsvFilter(
    name: String,
    private val parameter: String,
    values: List<String>,
) : Filter.Group<TriStateOption>(name, values.map { TriStateOption(it, it) }),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.mapNotNull {
            when (it.state) {
                Filter.TriState.STATE_INCLUDE -> it.value
                Filter.TriState.STATE_EXCLUDE -> "-${it.value}"
                else -> null
            }
        }.takeIf(List<String>::isNotEmpty)?.let {
            builder.addQueryParameter(parameter, it.joinToString(separator = ","))
        }
    }
}

private class GenreFilter(genres: List<String>) : TriStateCsvFilter("Genres", "genres", genres)

private class TagFilter(tags: List<String>) : TriStateCsvFilter("Tags", "tags", tags)

open class SelectQueryFilter(
    name: String,
    private val parameter: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()),
    UriFilter {
    protected val selectedValue get() = options[state].second

    override fun addToUri(builder: HttpUrl.Builder) {
        selectedValue.takeIf(String::isNotEmpty)?.let {
            builder.addQueryParameter(parameter, it)
        }
    }
}

class ContentRatingFilter(options: List<Pair<String, String>>) : SelectQueryFilter("Content rating", "content_rating", options) {
    val includesAdult get() = selectedValue == "erotica" || selectedValue == "pornographic"
}

open class TextQueryFilter(
    name: String,
    private val parameter: String,
) : Filter.Text(name),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.trim().takeIf(String::isNotEmpty)?.let {
            builder.addQueryParameter(parameter, it)
        }
    }
}

class PositiveIntegerFilter(
    name: String,
    private val parameter: String,
) : Filter.Text(name),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        if (state.isEmpty()) return
        val value = state.toIntOrNull()?.takeIf { it > 0 }
            ?: throw IllegalArgumentException("$name must be a positive integer")
        builder.addQueryParameter(parameter, value.toString())
    }
}

class MinimumRatingFilter :
    Filter.Text("Minimum rating"),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        if (state.isEmpty()) return
        val value = state.toDoubleOrNull()?.takeIf { it in 0.0..10.0 }
            ?: throw IllegalArgumentException("Minimum rating must be between 0 and 10")
        builder.addQueryParameter("min_rating", value.toString().removeSuffix(".0"))
    }
}

class CheckboxQueryFilter(
    name: String,
    private val parameter: String,
    private val value: String,
) : Filter.CheckBox(name),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        if (state) builder.addQueryParameter(parameter, value)
    }
}
