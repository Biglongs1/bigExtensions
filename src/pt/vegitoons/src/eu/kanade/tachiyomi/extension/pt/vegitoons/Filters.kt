package eu.kanade.tachiyomi.extension.pt.vegitoons

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl
import java.io.IOException

interface UrlFilter {
    fun addToUrl(builder: HttpUrl.Builder)
}

class SortFilter :
    Filter.Select<String>("Ordenar por", OPTIONS.map { it.first }.toTypedArray()),
    UrlFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        val option = OPTIONS[state]
        builder.setQueryParameter("orderBy", option.second)
        builder.setQueryParameter("orderDirection", option.third)
    }

    companion object {
        private val OPTIONS = listOf(
            Triple("Mais visualizados", "visualizacoes", "DESC"),
            Triple("Menos visualizados", "visualizacoes", "ASC"),
            Triple("A–Z", "nome", "ASC"),
            Triple("Z–A", "nome", "DESC"),
            Triple("Mais recentes", "criacao", "DESC"),
            Triple("Mais antigos", "criacao", "ASC"),
            Triple("Atualizados recentemente", "ultima_atualizacao", "DESC"),
        )
    }
}

class SelectFilter(name: String, private val parameter: String, options: List<FilterOptionDto>) :
    Filter.Select<String>(name, arrayOf("Todos") + options.map { it.name }),
    UrlFilter {
    private val ids = listOf(0) + options.map { it.id }

    override fun addToUrl(builder: HttpUrl.Builder) {
        if (state == 0) return
        if (parameter == "gen_id") builder.removeAllQueryParameters("todos_generos")
        builder.setQueryParameter(parameter, ids[state].toString())
    }
}

class TagFilter(option: FilterOptionDto) : Filter.CheckBox(option.name) {
    val id = option.id
}

class TagsFilter(options: List<FilterOptionDto>) :
    Filter.Group<TagFilter>("Tags", options.map(::TagFilter)),
    UrlFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        val tags = state.filter { it.state }.joinToString(",") { it.id.toString() }
        if (tags.isNotEmpty()) builder.addQueryParameter("tag_ids", tags)
    }
}

class ChapterCountFilter(name: String, private val parameter: String) :
    Filter.Text(name),
    UrlFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        if (state.isBlank()) return
        val count = state.trim().toIntOrNull()?.takeIf { it >= 0 }
            ?: throw IOException("Informe uma quantidade de capítulos válida em '$name'.")
        builder.addQueryParameter(parameter, count.toString())
    }
}
