package eu.kanade.tachiyomi.extension.pt.auratoons

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.utils.firstInstanceOrNull

internal fun buildFilterList(data: FilterDataDto?) = FilterList(
    SortFilter(),
    StatusFilter(),
    TypeFilter(),
    AdultFilter(),
    Filter.Separator(),
    Filter.Header("Ano de lançamento"),
    YearFromFilter(),
    YearToFilter(),
    Filter.Header("Quantidade de capítulos"),
    ChaptersMinFilter(),
    ChaptersMaxFilter(),
    Filter.Separator(),
    CategoryModeFilter(),
    GenreFilter(data?.genres.orEmpty()),
    ThemeFilter(data?.themes.orEmpty()),
)

internal fun FilterList.toCatalogFilters() = CatalogFiltersDto(
    status = firstInstanceOrNull<StatusFilter>()?.checked.orEmpty(),
    types = firstInstanceOrNull<TypeFilter>()?.checked.orEmpty(),
    genres = firstInstanceOrNull<GenreFilter>()?.checked.orEmpty(),
    themes = firstInstanceOrNull<ThemeFilter>()?.checked.orEmpty(),
    categoryMode = firstInstanceOrNull<CategoryModeFilter>()?.value ?: CATEGORY_MODE_OPTIONS[0].value,
    yearFrom = firstInstanceOrNull<YearFromFilter>()?.state?.trim().orEmpty(),
    yearTo = firstInstanceOrNull<YearToFilter>()?.state?.trim().orEmpty(),
    chaptersMin = firstInstanceOrNull<ChaptersMinFilter>()?.state?.trim().orEmpty(),
    chaptersMax = firstInstanceOrNull<ChaptersMaxFilter>()?.state?.trim().orEmpty(),
    adult = firstInstanceOrNull<AdultFilter>()?.value ?: ADULT_OPTIONS[0].value,
)

internal val FilterList.sortBy: String
    get() = SORT_OPTIONS[firstInstanceOrNull<SortFilter>()?.state?.index ?: DEFAULT_SORT_INDEX].value

internal val FilterList.sortDir: String
    get() = if (firstInstanceOrNull<SortFilter>()?.state?.ascending == true) "asc" else "desc"

private class OptionCheckBox(label: String, val value: String) : Filter.CheckBox(label)

private open class OptionGroup(name: String, options: List<FilterOptionDto>) : Filter.Group<OptionCheckBox>(name, options.map { OptionCheckBox(it.label, it.value) }) {
    val checked get() = state.filter(OptionCheckBox::state).map(OptionCheckBox::value)
}

private open class OptionSelect(name: String, private val options: List<FilterOptionDto>) : Filter.Select<String>(name, options.map(FilterOptionDto::label).toTypedArray()) {
    val value get() = options[state].value
}

private class SortFilter :
    Filter.Sort(
        "Ordenar por",
        SORT_OPTIONS.map(FilterOptionDto::label).toTypedArray(),
        Selection(DEFAULT_SORT_INDEX, false),
    )

private class StatusFilter : OptionGroup("Status", STATUS_OPTIONS)

private class TypeFilter : OptionGroup("Tipo", TYPE_OPTIONS)

private class AdultFilter : OptionSelect("Conteúdo adulto", ADULT_OPTIONS)

private class CategoryModeFilter : OptionSelect("Combinar gêneros e temas", CATEGORY_MODE_OPTIONS)

private class GenreFilter(options: List<FilterOptionDto>) : OptionGroup("Gêneros", options)

private class ThemeFilter(options: List<FilterOptionDto>) : OptionGroup("Temas", options)

private class YearFromFilter : Filter.Text("De")

private class YearToFilter : Filter.Text("Até")

private class ChaptersMinFilter : Filter.Text("Mínimo")

private class ChaptersMaxFilter : Filter.Text("Máximo")

private const val DEFAULT_SORT_INDEX = 3

private val SORT_OPTIONS = listOf(
    FilterOptionDto("Atualização", "updatedAt"),
    FilterOptionDto("Adicionado", "createdAt"),
    FilterOptionDto("Título", "title"),
    FilterOptionDto("Visualizações", "views"),
    FilterOptionDto("Avaliação", "rating"),
    FilterOptionDto("Ano", "year"),
    FilterOptionDto("Capítulos", "chapters"),
)

private val STATUS_OPTIONS = listOf(
    FilterOptionDto("Em andamento", "ongoing"),
    FilterOptionDto("Completo", "completed"),
    FilterOptionDto("Cancelado", "cancelled"),
    FilterOptionDto("Hiato", "hiatus"),
)

private val TYPE_OPTIONS = listOf(
    FilterOptionDto("Manga", "manga"),
    FilterOptionDto("Manhwa", "manhwa"),
    FilterOptionDto("Manhua", "manhua"),
    FilterOptionDto("Webtoon", "webtoon"),
    FilterOptionDto("Comic", "comic"),
    FilterOptionDto("HQ", "hq"),
    FilterOptionDto("Pornhwa", "pornhwa"),
)

private val ADULT_OPTIONS = listOf(
    FilterOptionDto("Ocultar", "Ocultar"),
    FilterOptionDto("Incluir", "Incluir"),
    FilterOptionDto("Somente 18+", "Somente 18+"),
)

private val CATEGORY_MODE_OPTIONS = listOf(
    FilterOptionDto("Qualquer categoria (OU)", "or"),
    FilterOptionDto("Todas as categorias (E)", "and"),
)
