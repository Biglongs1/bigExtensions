package eu.kanade.tachiyomi.extension.pt.inkscan

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl
import java.io.IOException

interface UrlFilter {
    fun addToUrl(builder: HttpUrl.Builder)
}

open class SelectFilter(
    name: String,
    private val parameter: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()),
    UrlFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        val selected = options[state].second
        if (selected.isNotEmpty()) builder.setQueryParameter(parameter, selected)
    }
}

class SortFilter :
    SelectFilter(
        "Ordenar por",
        "order",
        listOf(
            "Mais populares" to "total_views.desc,id.asc",
            "Mais recentes" to "created_at.desc,id.asc",
            "Atualizados recentemente" to "updated_at.desc,id.asc",
            "A–Z" to "titulo.asc,id.asc",
            "Z–A" to "titulo.desc,id.asc",
            "Mais capítulos" to "total_capitulos.desc,id.asc",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        "status",
        listOf(
            "Todos" to "",
            "Em andamento" to "eq.ongoing",
            "Concluído" to "eq.completed",
            "Em hiato" to "eq.hiatus",
            "Cancelado" to "eq.cancelled",
        ),
    )

class ArchiveFilter :
    Filter.Select<String>("Acervo", arrayOf("Principal", "Adulto")),
    UrlFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        if (state == 0) {
            builder.addQueryParameter("or", "(is_acervo_b.is.null,is_acervo_b.eq.false)")
        } else {
            builder.addQueryParameter("is_acervo_b", "eq.true")
        }
    }
}

class OptionFilter(name: String) : Filter.CheckBox(name)

class FormatsFilter :
    Filter.Group<OptionFilter>("Formatos", listOf("Manga", "Manhwa", "Manhua", "Novel").map(::OptionFilter)),
    UrlFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        val selected = state.filter { it.state }.joinToString(",") { it.name }
        if (selected.isNotEmpty()) builder.addQueryParameter("formato", "in.($selected)")
    }
}

class GenresFilter(options: List<String>) :
    Filter.Group<OptionFilter>("Gêneros", options.map(::OptionFilter)),
    UrlFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        state.filter { it.state }.forEach {
            val genre = it.name.replace("\\", "\\\\").replace("\"", "\\\"")
            builder.addQueryParameter("or", "(tags.cs.{\"$genre\"},generos.cs.{\"$genre\"})")
        }
    }
}

class TextFilter(name: String, private val parameter: String) :
    Filter.Text(name),
    UrlFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        if (state.isNotBlank()) builder.addQueryParameter(parameter, "ilike.*${state.trim()}*")
    }
}

class ChapterCountFilter(name: String, private val operator: String) :
    Filter.Text(name),
    UrlFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        if (state.isBlank()) return
        val count = state.trim().toIntOrNull()?.takeIf { it >= 0 }
            ?: throw IOException("Informe uma quantidade de capítulos válida em '$name'.")
        builder.addQueryParameter("total_capitulos", "$operator.$count")
    }
}
