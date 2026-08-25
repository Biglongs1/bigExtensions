package eu.kanade.tachiyomi.extension.pt.loverstoon

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selectedValue get() = options[state].second
}

class SortFilter :
    SelectFilter(
        "Ordenar por",
        listOf(
            "Atualizações recentes" to "",
            "Mais vistos" to "views",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        listOf(
            "Todos" to "",
            "Em andamento" to "ongoing",
            "Finalizado" to "completed",
            "Hiato" to "hiatus",
            "Dropado" to "dropped",
            "Cancelado" to "cancelled",
            "Inativo" to "inactive",
        ),
    )

class GenreFilter :
    SelectFilter(
        "Gênero",
        listOf("Todos" to "") + GENRES.map { it to it },
    )

class ScanFilter(scans: List<ScanDto>) :
    SelectFilter(
        "Scan",
        listOf("Todas" to "") + scans.map { it.name to it.id },
    )

private val GENRES = listOf(
    "Ação",
    "Adulto",
    "Apocalíptico",
    "Artes Marciais",
    "Aventura",
    "BL",
    "Comédia",
    "Drama",
    "Escolar",
    "Fantasia",
    "Ficção Científica",
    "Gore",
    "Harem",
    "Harém",
    "Histórico",
    "Horror",
    "Isekai",
    "Josei",
    "Magia",
    "Mecha",
    "Mistério",
    "Psicológico",
    "Reencarnação",
    "Regressão",
    "Romance",
    "Sci-fi",
    "Seinen",
    "Shoujo",
    "Shounen",
    "Slice of Life",
    "Smut",
    "Sobrenatural",
    "Suspense",
    "Tragédia",
    "Vida Escolar",
    "Vingança",
    "Webtoon",
    "Yaoi",
    "Yuri",
)
