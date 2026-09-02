package eu.kanade.tachiyomi.extension.en.atsumaru

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Atsumaru : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getHomeManga(
        endpoint = "popular",
        page = page,
        timeframe = "daily",
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = getHomeManga(
        endpoint = "recentlyUpdated",
        page = page,
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/collections/manga/documents/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.ifBlank { "*" })
            .addQueryParameter("query_by", QUERY_BY)
            .addQueryParameter("query_by_weights", QUERY_BY_WEIGHTS)
            .addQueryParameter("num_typos", NUM_TYPOS)
            .addQueryParameter("prefix", PREFIX)
            .apply {
                if (query.isNotBlank()) {
                    addQueryParameter("infix", INFIX)
                }
            }
            .addQueryParameter("filter_by", filters.toTypesenseFilter())
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PAGE_SIZE.toString())
            .addQueryParameter(
                "sort_by",
                filters.firstInstanceOrNull<SortFilter>()?.sortBy ?: DEFAULT_SORT_BY,
            )
            .build()

        val response = client.get(url).parseAs<TypesenseSearchResponse>()
        return MangasPage(response.toSMangaList(), response.hasNextPage(page, PAGE_SIZE))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val mangaId = when (url.pathSegments.firstOrNull()) {
            "manga", "read" -> url.pathSegments.getOrNull(1)
            else -> null
        }?.takeIf(String::isNotEmpty) ?: return null

        return getMangaPage(mangaId)
            .takeIf(MangaPageDto::isComic)
            ?.toSManga()
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaPageDeferred = if (fetchDetails || fetchChapters) {
            async { getMangaPage(manga.url) }
        } else {
            null
        }
        val chaptersDeferred = if (fetchChapters) {
            async { getAllChapters(manga.url) }
        } else {
            null
        }

        val mangaPage = mangaPageDeferred?.await()
        val updatedManga = if (fetchDetails) {
            requireNotNull(mangaPage).toSManga()
        } else {
            manga
        }
        val updatedChapters = if (fetchChapters) {
            requireNotNull(chaptersDeferred).await().toSChapterList(
                mangaId = manga.url,
                scanlators = requireNotNull(mangaPage).scanlatorNames(),
            )
        } else {
            chapters
        }

        SMangaUpdate(updatedManga, updatedChapters)
    }

    private suspend fun getMangaPage(mangaId: String): MangaPageDto {
        val url = "$baseUrl/api/manga/page".toHttpUrl().newBuilder()
            .addQueryParameter("id", mangaId)
            .build()

        return client.get(url).parseAs<MangaPageResponse>().mangaPage
    }

    private suspend fun getAllChapters(mangaId: String): AllChaptersResponse {
        val url = "$baseUrl/api/manga/allChapters".toHttpUrl().newBuilder()
            .addQueryParameter("mangaId", mangaId)
            .build()

        return client.get(url).parseAs()
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/read/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pathSegments = getChapterUrl(chapter).toHttpUrl().pathSegments
        val mangaId = pathSegments.getOrNull(1)
            ?: throw IllegalArgumentException("Invalid chapter URL")
        val chapterId = pathSegments.getOrNull(2)
            ?: throw IllegalArgumentException("Invalid chapter URL")
        val url = "$baseUrl/api/read/chapter".toHttpUrl().newBuilder()
            .addQueryParameter("mangaId", mangaId)
            .addQueryParameter("chapterId", chapterId)
            .build()

        return client.get(url).parseAs<ReaderResponse>().toPageList()
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client
        .get("$baseUrl/api/explore/availableFilters")
        .parseAs<AvailableFiltersDto>()
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val availableFilters = data?.parseAs<AvailableFiltersDto>()

        return FilterList(
            buildList {
                add(SortFilter())
                availableFilters?.typeOptions()?.let { add(TypeFilter(it)) }
                availableFilters?.statusOptions()?.let { add(StatusFilter(it)) }
                add(ContentRatingFilter())
                add(YearFilter())
                add(MinimumChapterFilter())
                add(OfficialTranslationFilter())
                availableFilters?.genreOptions()?.let {
                    add(FilterListSeparator)
                    add(GenreFilter(it))
                }
                availableFilters?.tagOptions()?.let { add(TagFilter(it)) }
            },
        )
    }

    private suspend fun getHomeManga(
        endpoint: String,
        page: Int,
        timeframe: String? = null,
    ): MangasPage {
        val url = "$baseUrl/api/home2/$endpoint".toHttpUrl().newBuilder()
            .addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .apply {
                timeframe?.let { addQueryParameter("timeframe", it) }
            }
            .addQueryParameter("mediums", COMIC_MEDIUM)
            .build()
        val response = client.get(url).parseAs<HomeResponse>()

        return MangasPage(response.toSMangaList(), response.hasNextPage(PAGE_SIZE))
    }

    private fun FilterList.toTypesenseFilter(): String = buildList {
        add(BASE_FILTER)
        filterIsInstance<TypesenseFilter>().forEach { addAll(it.toConditions()) }
    }.joinToString(" && ")

    companion object {
        private const val PAGE_SIZE = 20
        private const val COMIC_MEDIUM = "Comic"
        private const val QUERY_BY = "title,englishTitle,otherNames,authors,acronyms"
        private const val QUERY_BY_WEIGHTS = "4,3,2,2,1"
        private const val NUM_TYPOS = "4,3,2,1,0"
        private const val PREFIX = "true,true,true,true,false"
        private const val INFIX = "off,off,fallback,off,off"
        private const val BASE_FILTER = "medium:=[`Comic`] && hidden:!=true && isAdult:=false"
    }
}
