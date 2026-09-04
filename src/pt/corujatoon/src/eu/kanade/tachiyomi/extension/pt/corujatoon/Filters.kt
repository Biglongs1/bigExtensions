package eu.kanade.tachiyomi.extension.pt.corujatoon

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

open class SelectFilter(
    name: String,
    private val parameter: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    fun addToUrl(builder: HttpUrl.Builder) {
        val selected = options[state].second
        if (selected.isNotEmpty()) builder.setQueryParameter(parameter, selected)
    }
}

class SortFilter :
    SelectFilter(
        "Ordenar por",
        "sort",
        listOf(
            "Mais populares" to "popular",
            "Mais recentes" to "recent",
            "Melhor avaliação" to "rating",
            "A–Z" to "title",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        "status",
        listOf(
            "Todos" to "",
            "Em andamento" to "ONGOING",
            "Concluído" to "COMPLETED",
            "Em hiato" to "HIATUS",
            "Cancelado" to "CANCELLED",
        ),
    )

class TypeFilter :
    SelectFilter(
        "Tipo",
        "type",
        listOf(
            "Todos" to "",
            "Manhwa" to "MANHWA",
            "Mangá" to "MANGA",
            "Manhua" to "MANHUA",
            "Webtoon" to "WEBTOON",
            "Shoujo" to "SHOUJO",
            "Novel" to "NOVEL",
            "Comic" to "COMIC",
        ),
    )

class GenreFilter(options: List<GenreOptionDto>) :
    SelectFilter(
        "Gênero",
        "genre",
        listOf("Todos" to "") + options.map { it.name to it.slug },
    )
