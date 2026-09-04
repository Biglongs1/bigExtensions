package eu.kanade.tachiyomi.extension.pt.imperiodabritannia

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.getLocalStorage
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class ImperioDaBritannia : KeiSource() {
    private val apiUrl = "https://api.imperiodabritannia.net/api"

    override fun Headers.Builder.configureHeaders(): Headers.Builder = add("Accept", "application/json")
        .add("X-Noencryptionbritta", "1")
        .add("X-Brit-Cache", "true")

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2) { it.host == apiUrl.toHttpUrl().host }

    override suspend fun getPopularManga(page: Int): MangasPage {
        // The catalog's views_desc sort currently fails on the server; the homepage ranking works.
        val result = client.get("$apiUrl/obras/top10/views?periodo=total").parseAs<RankingDto>()
        return MangasPage(result.obras.map { it.toSManga() }, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getList("capitulos/recentes", page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getList("obras", page, query, filters)

    private suspend fun getList(path: String, page: Int, query: String = "", filters: FilterList = FilterList()): MangasPage {
        val url = "$apiUrl/$path".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "24")
            .apply {
                if (query.isNotBlank()) addQueryParameter("busca", query)
                if (path == "obras") addQueryParameter("ordem", "criada_em_desc")
                filters.forEach { if (it is UrlFilter) it.addToUrl(this) }
            }
            .build()
        val result = client.get(url).parseAs<MangaListDto>()
        return MangasPage(result.obras.map { it.toSManga() }, result.pagination.hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val value = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        val endpoint = when (url.pathSegments[0]) {
            "obra" -> "$apiUrl/obras/$value"
            "manga" -> "$apiUrl/obras/by-slug/$value"
            else -> return null
        }
        return client.get(endpoint).parseAs<MangaDetailsDto>().obra.toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val id = (baseUrl + manga.url).toHttpUrl().pathSegments[1]
        val work = client.get("$apiUrl/obras/$id").parseAs<MangaDetailsDto>().obra
        return SMangaUpdate(work.toSManga(), work.chapterList())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = (baseUrl + chapter.url).toHttpUrl().pathSegments
        val authHeaders = headers.newBuilder().apply {
            getLocalStorage(baseUrl, "token")?.takeIf(String::isNotBlank)?.let {
                set("Authorization", "Bearer $it")
            }
        }.build()
        val result = client.get("$apiUrl/obras/${segments[1]}/capitulos/${segments[3]}", authHeaders)
            .parseAs<ChapterDetailsDto>()
        return result.capitulo.toPages()
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val formats = async { client.get("$apiUrl/filtros/formatos").parseAs<FormatsDto>().formatos }
        val tags = async { client.get("$apiUrl/filtros/tags").parseAs<TagsDto>().tags }
        FilterDataDto(formats.await(), tags.await()).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        buildList {
            add(SortFilter())
            data?.parseAs<FilterDataDto>()?.let {
                add(OptionsFilter("Formatos", "formato_ids", it.formats))
                add(OptionsFilter("Gêneros", "tag_ids", it.tags))
            }
        },
    )
}
