package eu.kanade.tachiyomi.extension.pt.loverstoon

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

@Source
abstract class LoversToon : KeiSource() {

    private val apiUrl get() = "$baseUrl/api"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3) { it.encodedPath.startsWith("/api/") }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = this
        .add("Accept", "application/json")

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page, sort = "views")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getMangaList(
        page = page,
        sort = filters.firstInstanceOrNull<SortFilter>()?.selectedValue,
        query = query,
        genre = filters.firstInstanceOrNull<GenreFilter>()?.selectedValue,
        status = filters.firstInstanceOrNull<StatusFilter>()?.selectedValue,
        scan = filters.firstInstanceOrNull<ScanFilter>()?.selectedValue,
    )

    private suspend fun getMangaList(
        page: Int,
        sort: String? = null,
        query: String? = null,
        genre: String? = null,
        status: String? = null,
        scan: String? = null,
    ): MangasPage {
        val url = "$apiUrl/comics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_LIMIT.toString())
            .apply {
                sort?.takeIf(String::isNotBlank)?.let { addQueryParameter("sort", it) }
                query?.takeIf(String::isNotBlank)?.let { addQueryParameter("search", it) }
                genre?.takeIf(String::isNotBlank)?.let { addQueryParameter("genre", it) }
                status?.takeIf(String::isNotBlank)?.let { addQueryParameter("status", it) }
                scan?.takeIf(String::isNotBlank)?.let { addQueryParameter("scan", it) }
            }
            .build()

        val result = client.get(url).parseAs<MangaListDto>()

        return MangasPage(
            mangas = result.data.filterNot(MangaDto::isNovel).map { it.toSManga(baseUrl) },
            hasNextPage = page < result.totalPages,
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.firstOrNull() !in MANGA_PATH_SEGMENTS) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotBlank) ?: return null
        val manga = SManga.create().apply { this.url = slug }

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
        val details = client.get("$apiUrl/comics/${manga.url}").parseAs<MangaDto>()

        return SMangaUpdate(
            manga = details.toSManga(baseUrl),
            chapters = details.lastChapters.map { it.toSChapter(details.scanlator) },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get("$apiUrl/chapters/${chapter.url}")
        .parseAs<ChapterPagesDto>()
        .toPageList(baseUrl)

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val scans = client.get("$apiUrl/scans").parseAs<List<ScanDto>>()
        return FilterData(scans).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val scans = data?.parseAs<FilterData>()?.scans

        return FilterList(
            listOfNotNull(
                SortFilter(),
                GenreFilter(),
                StatusFilter(),
                scans?.let(::ScanFilter),
            ),
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comic/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/read/${chapter.url}"

    companion object {
        private const val PAGE_LIMIT = 24
        private val MANGA_PATH_SEGMENTS = listOf("comic", "obra")
    }
}
