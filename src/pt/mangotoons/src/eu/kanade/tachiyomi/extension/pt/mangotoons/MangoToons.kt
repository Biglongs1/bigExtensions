package eu.kanade.tachiyomi.extension.pt.mangotoons

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException

@Source
abstract class MangoToons : KeiSource() {

    private val apiUrl = "https://api.mangotoons.com/api"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3) { it.encodedPath.startsWith("/api/") }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = this
        .add("Accept", "application/json")

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getMangaList(
        page = page,
        query = query,
        format = filters.firstInstanceOrNull<FormatFilter>()?.selectedValue,
        status = filters.firstInstanceOrNull<StatusFilter>()?.selectedValue,
    )

    private suspend fun getMangaList(
        page: Int,
        query: String? = null,
        format: String? = null,
        status: String? = null,
    ): MangasPage {
        val url = "$apiUrl/obras".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .apply {
                query?.takeIf(String::isNotBlank)?.let { addQueryParameter("busca", it) }
                format?.takeIf(String::isNotBlank)?.let { addQueryParameter("formato_id", it) }
                status?.takeIf(String::isNotBlank)?.let { addQueryParameter("status_id", it) }
            }
            .build()

        val result = client.get(url).parseAs<ListDto>()

        return MangasPage(result.mangas, result.hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.firstOrNull() != "obra") return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.all(Char::isDigit) } ?: return null
        val manga = SManga.create().apply { this.url = "/obra/$id" }

        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val id = manga.url.substringAfterLast('/')
        val work = client.get("$apiUrl/obras/$id").parseAs<WorkWrapperDto>().obra

        return SMangaUpdate(manga = work.toSManga(), chapters = work.chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = chapter.url.split('/')
        val workId = segments.getOrNull(2) ?: throw IOException("Capítulo inválido")
        val chapterId = segments.getOrNull(4) ?: throw IOException("Capítulo inválido")

        val detail = client.get("$apiUrl/obras/$workId/capitulos/$chapterId").parseAs<ChapterDetailDto>()
        val pages = detail.toPageList()

        if (pages.isEmpty() && detail.isLocked) {
            throw IOException("Capítulo bloqueado por paywall na MangoToons.")
        }

        return pages
    }

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val formats = client.get("$apiUrl/filtros/formatos").parseAs<FormatListDto>().formatos
        val statuses = client.get("$apiUrl/filtros/status").parseAs<StatusListDto>().status

        return FilterData(
            formats = formats.map { FilterEntry(it.nome, it.id) },
            statuses = statuses.map { FilterEntry(it.nome, it.id) },
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>() ?: return FilterList()

        return FilterList(
            listOfNotNull(
                filterData.formats.takeIf(List<FilterEntry>::isNotEmpty)?.let(::FormatFilter),
                filterData.statuses.takeIf(List<FilterEntry>::isNotEmpty)?.let(::StatusFilter),
            ),
        )
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url
}
