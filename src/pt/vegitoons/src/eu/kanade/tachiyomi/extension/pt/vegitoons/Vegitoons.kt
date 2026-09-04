package eu.kanade.tachiyomi.extension.pt.vegitoons

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
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class Vegitoons : KeiSource() {
    private val apiUrl = "https://api.vegitoons.black"

    override fun Headers.Builder.configureHeaders(): Headers.Builder = add("scan-id", "1").add("Accept", "application/json")

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2) { it.host == apiUrl.toHttpUrl().host && !it.encodedPath.startsWith("/cdn/") }

    override suspend fun getPopularManga(page: Int): MangasPage = getList("ranking", page, FilterList(), "tipo" to "visualizacoes_geral", "gen_id" to "1")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getList("atualizacoes", page, FilterList(), "gen_id" to "1")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getList(
        "buscar",
        page,
        filters,
        "busca" to query,
        "todos_generos" to "1",
        "orderBy" to "visualizacoes",
        "orderDirection" to "DESC",
    )

    private suspend fun getList(path: String, page: Int, filters: FilterList, vararg parameters: Pair<String, String>): MangasPage {
        val url = "$apiUrl/obras/$path".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "26")
            .apply {
                parameters.forEach { (key, value) -> addQueryParameter(key, value) }
                filters.forEach { if (it is UrlFilter) it.addToUrl(this) }
            }
            .build()
        val result = client.get(url).parseAs<MangaListDto>()
        return MangasPage(result.obras.map { it.toSManga() }, page < result.totalPaginas)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.firstOrNull() != "obra") return null
        val id = url.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null
        return client.get("$apiUrl/obras/$id").parseAs<MangaDto>().toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val id = (baseUrl + manga.url).toHttpUrl().pathSegments[1]
        val result = client.get("$apiUrl/obras/$id").parseAs<MangaDto>()
        return SMangaUpdate(
            result.toSManga(),
            result.capitulos.map { it.toSChapter() }.distinctBy { it.url }.sortedByDescending { it.chapter_number },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = (baseUrl + chapter.url).toHttpUrl().pathSegments[1]
        return client.get("$apiUrl/capitulos/$id").parseAs<ChapterPagesDto>().pages
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/obras/filtros").parseAs()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        buildList {
            add(SortFilter())
            add(ChapterCountFilter("Mínimo de capítulos", "min_capitulos"))
            add(ChapterCountFilter("Máximo de capítulos", "max_capitulos"))
            data?.parseAs<FilterDataDto>()?.let {
                add(SelectFilter("Gênero", "gen_id", it.generos))
                add(SelectFilter("Formato", "formt_id", it.formatos))
                add(SelectFilter("Status", "stt_id", it.status))
                add(TagsFilter(it.tags))
            }
        },
    )
}
