package eu.kanade.tachiyomi.extension.pt.sakuramangas

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.FormBody

internal interface BodyFilter {
    fun addToBody(builder: FormBody.Builder)
}

internal class SortFilter :
    Filter.Select<String>("Ordenar por", arrayOf("Mais lidos", "Favoritos", "Nota alta", "Menos lidos", "Menos favoritos", "Nota baixa")),
    BodyFilter {
    override fun addToBody(builder: FormBody.Builder) {
        builder.add("order", arrayOf("3", "1", "2", "6", "4", "5")[state])
    }
}

internal class SelectFilter(name: String, private val parameter: String, private val options: Array<String>) :
    Filter.Select<String>(name, arrayOf("Todos") + options),
    BodyFilter {
    override fun addToBody(builder: FormBody.Builder) {
        builder.add(parameter, if (state == 0) "" else options[state - 1])
    }
}

internal class AuthorFilter :
    Filter.Text("Autor"),
    BodyFilter {
    override fun addToBody(builder: FormBody.Builder) {
        builder.add("author", state.trim())
    }
}

internal class TagFilter(name: String) : Filter.TriState(name)

internal class TagsFilter(name: String, options: List<String>) :
    Filter.Group<TagFilter>(name, options.map(::TagFilter)),
    BodyFilter {
    override fun addToBody(builder: FormBody.Builder) {
        state.forEach {
            when (it.state) {
                Filter.TriState.STATE_INCLUDE -> builder.add("tags[]", it.name)
                Filter.TriState.STATE_EXCLUDE -> builder.add("excludeTags[]", it.name)
            }
        }
    }
}
