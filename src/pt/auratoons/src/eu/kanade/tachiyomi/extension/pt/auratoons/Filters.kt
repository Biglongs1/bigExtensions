package eu.kanade.tachiyomi.extension.pt.auratoons

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.utils.firstInstanceOrNull

internal class AuraFilters(private val data: FilterDataDto?) {
    fun getFilterList() = FilterList(
        buildList {
            add(SortFilter())
            add(AdultFilter())
            add(Filter.Separator())
            add(StatusFilter())
            add(TypeFilter())
            add(CategoryModeFilter())
            data?.genres?.takeIf(List<FilterOptionDto>::isNotEmpty)?.let { add(GenreFilter(it)) }
            data?.themes?.takeIf(List<FilterOptionDto>::isNotEmpty)?.let { add(ThemeFilter(it)) }
            add(Filter.Separator())
            add(Filter.Header("Ano de lançamento"))
            add(YearFromFilter())
            add(YearToFilter())
            add(Filter.Header("Quantidade de capítulos"))
            add(ChaptersMinFilter())
            add(ChaptersMaxFilter())
        },
    )
}

internal class SearchOptions(
    val filters: CatalogFiltersDto,
    val sortBy: String,
    val sortDir: String,
)

internal fun FilterList.toSearchOptions(): SearchOptions {
    val sort = firstInstanceOrNull<SortFilter>()
    return SearchOptions(
        filters = CatalogFiltersDto(
            status = firstInstanceOrNull<StatusFilter>()?.selectedValues().orEmpty(),
            types = firstInstanceOrNull<TypeFilter>()?.selectedValues().orEmpty(),
            genres = firstInstanceOrNull<GenreFilter>()?.selectedValues().orEmpty(),
            themes = firstInstanceOrNull<ThemeFilter>()?.selectedValues().orEmpty(),
            categoryMode = firstInstanceOrNull<CategoryModeFilter>()?.selectedValue ?: "or",
            yearFrom = firstInstanceOrNull<YearFromFilter>()?.state.orEmpty().trim(),
            yearTo = firstInstanceOrNull<YearToFilter>()?.state.orEmpty().trim(),
            chaptersMin = firstInstanceOrNull<ChaptersMinFilter>()?.state.orEmpty().trim(),
            chaptersMax = firstInstanceOrNull<ChaptersMaxFilter>()?.state.orEmpty().trim(),
            adult = firstInstanceOrNull<AdultFilter>()?.selectedValue ?: "Ocultar",
        ),
        sortBy = sort?.sortBy ?: "updatedAt",
        sortDir = sort?.sortDir ?: "desc",
    )
}

private class Option(val label: String, val value: String)

private class SortFilter :
    Filter.Sort(
        "Ordenar por",
        SORT_OPTIONS.map(Option::label).toTypedArray(),
        Selection(0, false),
    ) {
    private val selection get() = state ?: Selection(0, false)
    val sortBy get() = SORT_OPTIONS[selection.index].value
    val sortDir get() = if (selection.ascending) "asc" else "desc"
}

private open class SelectFilter(
    name: String,
    private val options: List<Option>,
) : Filter.Select<String>(name, options.map(Option::label).toTypedArray()) {
    val selectedValue get() = options[state].value
}

private class AdultFilter :
    SelectFilter(
        "Conteúdo adulto",
        listOf(
            Option("Ocultar", "Ocultar"),
            Option("Incluir", "Incluir"),
            Option("Somente 18+", "Somente 18+"),
        ),
    )

private class CategoryModeFilter :
    SelectFilter(
        "Combinar gêneros e temas",
        listOf(
            Option("Qualquer categoria (OU)", "or"),
            Option("Todas as categorias (E)", "and"),
        ),
    )

private open class MultiOption(name: String, val value: String) : Filter.CheckBox(name)

private open class MultiFilter(
    name: String,
    options: List<FilterOptionDto>,
) : Filter.Group<MultiOption>(
    name,
    options.map { MultiOption(it.label, it.value) },
) {
    fun selectedValues() = state.filter(MultiOption::state).map(MultiOption::value)
}

private class StatusFilter :
    MultiFilter(
        "Status",
        listOf(
            FilterOptionDto("Em andamento", "ongoing"),
            FilterOptionDto("Completo", "completed"),
            FilterOptionDto("Cancelado", "cancelled"),
            FilterOptionDto("Hiato", "hiatus"),
        ),
    )

private class TypeFilter :
    MultiFilter(
        "Tipo",
        listOf(
            FilterOptionDto("Mangá", "manga"),
            FilterOptionDto("Manhwa", "manhwa"),
            FilterOptionDto("Manhua", "manhua"),
            FilterOptionDto("Webtoon", "webtoon"),
            FilterOptionDto("Comic", "comic"),
            FilterOptionDto("HQ", "hq"),
            FilterOptionDto("Pornhwa", "pornhwa"),
        ),
    )

private class GenreFilter(options: List<FilterOptionDto>) : MultiFilter("Gêneros", options)

private class ThemeFilter(options: List<FilterOptionDto>) : MultiFilter("Temas", options)

private class YearFromFilter : Filter.Text("De")

private class YearToFilter : Filter.Text("Até")

private class ChaptersMinFilter : Filter.Text("Mínimo")

private class ChaptersMaxFilter : Filter.Text("Máximo")

private val SORT_OPTIONS = listOf(
    Option("Atualização", "updatedAt"),
    Option("Adicionado", "createdAt"),
    Option("Título", "title"),
    Option("Visualizações", "views"),
    Option("Avaliação", "rating"),
    Option("Ano", "year"),
    Option("Capítulos", "chapters"),
)
