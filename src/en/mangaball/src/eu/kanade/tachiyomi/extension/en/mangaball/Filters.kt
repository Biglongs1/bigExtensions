package eu.kanade.tachiyomi.extension.en.mangaball

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.FormBody

internal interface FormFilter {
    fun addToForm(builder: FormBody.Builder)
}

internal class MangaBallFilters(private val taxonomy: TaxonomyDto?) {
    fun getFilterList() = FilterList(
        SortFilter(),
        Filter.Separator(),
        IncludedTagsModeFilter(),
        ExcludedTagsModeFilter(),
        TagFilter("Content", taxonomy?.content.orEmpty()),
        TagFilter("Format", taxonomy?.format.orEmpty()),
        TagFilter("Genres", taxonomy?.genre.orEmpty()),
        TagFilter("Origin", taxonomy?.origin.orEmpty()),
        TagFilter("Themes", taxonomy?.theme.orEmpty()),
        Filter.Separator(),
        DemographicFilter(),
        OriginalLanguageFilter(),
        StatusFilter(),
    )
}

private class FilterOption(val name: String, val value: String)

private open class FormSelectFilter(
    name: String,
    private val parameter: String,
    private val options: List<FilterOption>,
    defaultValue: Int = 0,
) : Filter.Select<String>(name, options.map(FilterOption::name).toTypedArray(), defaultValue),
    FormFilter {
    override fun addToForm(builder: FormBody.Builder) {
        builder.add(parameter, options[state].value)
    }
}

private class IncludedTagsModeFilter :
    FormSelectFilter(
        "Included tags match",
        "filters[tag_included_mode]",
        TAG_MATCH_OPTIONS,
    )

private class ExcludedTagsModeFilter :
    FormSelectFilter(
        "Excluded tags match",
        "filters[tag_excluded_mode]",
        TAG_MATCH_OPTIONS,
    )

private class TagOption(name: String, val id: String) : Filter.TriState(name)

private class TagFilter(name: String, tags: List<TaxonomyItemDto>) :
    Filter.Group<TagOption>(name, tags.map { TagOption(it.name, it.id) }),
    FormFilter {
    override fun addToForm(builder: FormBody.Builder) {
        for (tag in state) {
            when (tag.state) {
                Filter.TriState.STATE_INCLUDE -> builder.add("filters[tag_included_ids][]", tag.id)
                Filter.TriState.STATE_EXCLUDE -> builder.add("filters[tag_excluded_ids][]", tag.id)
            }
        }
    }
}

private class DemographicFilter :
    FormSelectFilter(
        "Magazine demographic",
        "filters[demographic]",
        listOf(
            FilterOption("Any", "any"),
            FilterOption("Shounen", "shounen"),
            FilterOption("Shoujo", "shoujo"),
            FilterOption("Seinen", "seinen"),
            FilterOption("Josei", "josei"),
            FilterOption("Yuri", "yuri"),
            FilterOption("Yaoi", "yaoi"),
        ),
    )

private class OriginalLanguageFilter :
    FormSelectFilter(
        "Original language",
        "filters[originalLanguages]",
        listOf(
            FilterOption("Any", "any"),
            FilterOption("Comics", "en"),
            FilterOption("Manga", "jp"),
            FilterOption("Manhwa", "kr"),
            FilterOption("Manhua", "zh"),
        ),
    )

private class StatusFilter :
    FormSelectFilter(
        "Publication status",
        "filters[publicationStatus]",
        listOf(
            FilterOption("Any", "any"),
            FilterOption("Ongoing", "ongoing"),
            FilterOption("Completed", "completed"),
            FilterOption("On-Hold", "on_hold"),
            FilterOption("Cancelled", "cancelled"),
            FilterOption("Hiatus", "hiatus"),
        ),
    )

private class SortFilter :
    Filter.Sort(
        "Sort by",
        SORT_OPTIONS.map(FilterOption::name).toTypedArray(),
        Selection(0, false),
    ),
    FormFilter {
    override fun addToForm(builder: FormBody.Builder) {
        val selection = state ?: Selection(0, false)
        val direction = if (selection.ascending) "asc" else "desc"
        builder.add("filters[sort]", "${SORT_OPTIONS[selection.index].value}_$direction")
    }
}

private val TAG_MATCH_OPTIONS = listOf(
    FilterOption("AND", "and"),
    FilterOption("OR", "or"),
)

private val SORT_OPTIONS = listOf(
    FilterOption("Updated chapters", "updated_chapters"),
    FilterOption("Created", "created_at"),
    FilterOption("Updated", "updated_at"),
    FilterOption("Title", "name"),
    FilterOption("Views", "views"),
    FilterOption("Rating", "rating"),
)
