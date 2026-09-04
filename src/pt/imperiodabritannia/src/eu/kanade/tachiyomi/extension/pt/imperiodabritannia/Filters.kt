package eu.kanade.tachiyomi.extension.pt.imperiodabritannia

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlFilter {
    fun addToUrl(builder: HttpUrl.Builder)
}

class SortFilter :
    Filter.Select<String>("Ordenar por", OPTIONS.map { it.first }.toTypedArray()),
    UrlFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        builder.setQueryParameter("ordem", OPTIONS[state].second)
    }

    companion object {
        private val OPTIONS = listOf(
            "Mais recentes" to "criada_em_desc",
            "A–Z" to "nome_asc",
            "Z–A" to "nome_desc",
            "Mais capítulos" to "capitulos_desc",
        )
    }
}

class OptionFilter(option: FilterOptionDto) : Filter.CheckBox(option.nome) {
    val id = option.id
}

class OptionsFilter(name: String, private val parameter: String, options: List<FilterOptionDto>) :
    Filter.Group<OptionFilter>(name, options.map(::OptionFilter)),
    UrlFilter {
    override fun addToUrl(builder: HttpUrl.Builder) {
        val selected = state.filter { it.state }.joinToString(",") { it.id.toString() }
        if (selected.isNotEmpty()) builder.addQueryParameter(parameter, selected)
    }
}
