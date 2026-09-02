package eu.kanade.tachiyomi.extension.en.mangaball

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import java.io.IOException

@Source
abstract class MangaBall : KeiSource() {

    @Volatile
    private var csrfToken: String? = null
    private val csrfMutex = Mutex()

    override suspend fun getPopularManga(page: Int): MangasPage = browse(page, "views_desc")

    override suspend fun getLatestUpdates(page: Int): MangasPage = browse(page, "updated_chapters_desc")

    private suspend fun browse(page: Int, sort: String): MangasPage {
        val body = FormBody.Builder()
            .add("search_input", "")
            .add("filters[sort]", sort)
            .add("filters[page]", page.toString())
            .add("filters[start]", ((page - 1) * PAGE_SIZE).toString())
            .add("filters[translatedLanguage][]", "en")
            .build()

        return apiPost<MangaListResponseDto>(SEARCH_ENDPOINT, body).toMangasPage(baseUrl)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val builder = FormBody.Builder()
            .add("search_input", query)
            .add("filters[page]", page.toString())
            .add("filters[start]", ((page - 1) * PAGE_SIZE).toString())
            .add("filters[translatedLanguage][]", "en")

        for (filter in filters) {
            if (filter is FormFilter) filter.addToForm(builder)
        }

        return apiPost<MangaListResponseDto>(SEARCH_ENDPOINT, builder.build()).toMangasPage(baseUrl)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val key = manga.url.toMangaKey()
        val details = if (fetchDetails) {
            async { client.get(getMangaUrl(manga)).asJsoup().toSManga(manga.url) }
        } else {
            null
        }
        val chapterList = if (fetchChapters) {
            async { fetchChapterList(key.id) }
        } else {
            null
        }

        SMangaUpdate(
            manga = details?.await() ?: manga,
            chapters = chapterList?.await() ?: chapters,
        )
    }

    private fun Document.toSManga(mangaUrl: String) = SManga.create().apply {
        url = mangaUrl
        title = selectFirst("#comicDetail h6")?.text()?.takeIf(String::isNotEmpty)
            ?: throw IOException("Manga title not found")
        thumbnail_url = selectFirst(".featured-comic-carousel img.featured-cover")
            ?.absUrl("src")
            ?.takeIf(String::isNotEmpty)
            ?.toAbsoluteHttpsUrl(baseUrl)
        author = select("[data-person-id]")
            .map { it.text() }
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString()
            .takeIf(String::isNotEmpty)
        genre = select("[data-tag-id]")
            .map { it.text() }
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString()
            .takeIf(String::isNotEmpty)
        status = selectFirst(".badge-status")?.text().toMangaStatus(baseUrl)

        val synopsis = select("#descriptionContent .description-text > p")
            .map { it.text() }
            .filter(String::isNotEmpty)
            .joinToString("\n\n")
        val alternateNames = selectFirst(".alternate-name-container")?.text()?.takeIf(String::isNotEmpty)
        description = buildString {
            append(synopsis)
            alternateNames?.let {
                if (isNotEmpty()) append("\n\n")
                append("Alternative titles:\n")
                append(it)
            }
        }.takeIf(String::isNotEmpty)
    }

    private suspend fun fetchChapterList(titleId: String): List<SChapter> {
        val body = FormBody.Builder()
            .add("title_id", titleId)
            .build()

        return apiPost<ChapterListResponseDto>(CHAPTER_ENDPOINT, body).toSChapterList()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val imagesJson = document.select("script")
            .firstNotNullOfOrNull { script ->
                CHAPTER_IMAGES_REGEX.find(script.data())?.groupValues?.get(1)
            }
            ?: return emptyList()

        return imagesJson.parseAs<List<String>>().mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl.toAbsoluteHttpsUrl(baseUrl))
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val body = FormBody.Builder()
            .add("search_type", "getTagFilter")
            .build()

        return apiPost<TaxonomyResponseDto>(TAG_ENDPOINT, body).data.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = MangaBallFilters(
        data?.parseAs<TaxonomyDto>(),
    ).getFilterList()

    override fun getMangaUrl(manga: SManga): String {
        val key = manga.url.toMangaKey()
        return "$baseUrl/title-detail/${key.slug}-${key.id}/"
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter-detail/${chapter.url}/"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null

        val titleUrl = when (url.pathSegments.firstOrNull()) {
            "title-detail" -> url

            "chapter-detail" -> client.get(url)
                .asJsoup()
                .select("script[type=application/ld+json]")
                .firstNotNullOfOrNull { TITLE_URL_REGEX.find(it.data())?.value }
                ?.toHttpUrlOrNull()
                ?: return null

            else -> return null
        }
        val key = titleUrl.toMangaKeyOrNull() ?: return null
        val manga = SManga.create().apply { this.url = key.serialized }

        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    private fun HttpUrl.toMangaKeyOrNull(): MangaKey? {
        if (pathSegments.firstOrNull() != "title-detail") return null
        val segment = pathSegments.getOrNull(1) ?: return null
        val id = TITLE_ID_REGEX.find(segment)?.value ?: return null
        val slug = segment.removeSuffix("-$id").takeIf(String::isNotEmpty) ?: return null
        return MangaKey(slug, id)
    }

    private suspend inline fun <reified T> apiPost(path: String, body: FormBody): T {
        var response = client.post(
            "$baseUrl$path",
            apiHeaders(getCsrfToken()),
            body,
            ensureSuccess = false,
        )

        if (response.code == 403) {
            response.close()
            response = client.post(
                "$baseUrl$path",
                apiHeaders(getCsrfToken(forceRefresh = true)),
                body,
                ensureSuccess = false,
            )
        }

        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw IOException("MangaBall API returned HTTP $code")
        }

        return response.parseAs()
    }

    private suspend fun getCsrfToken(forceRefresh: Boolean = false): String {
        if (!forceRefresh) csrfToken?.let { return it }

        return csrfMutex.withLock {
            if (!forceRefresh) csrfToken?.let { return@withLock it }

            client.get("$baseUrl/search-advanced/")
                .asJsoup()
                .selectFirst("meta[name=csrf-token]")
                ?.attr("content")
                ?.takeIf(String::isNotEmpty)
                ?.also { csrfToken = it }
                ?: throw IOException("CSRF token not found")
        }
    }

    private fun apiHeaders(token: String) = headersBuilder()
        .set("Accept", "application/json")
        .set("X-CSRF-TOKEN", token)
        .build()

    companion object {
        private const val PAGE_SIZE = 24
        private const val SEARCH_ENDPOINT = "/api/v1/title/search-advanced/"
        private const val TAG_ENDPOINT = "/api/v1/tag/search/"
        private const val CHAPTER_ENDPOINT = "/api/v1/chapter/chapter-listing-by-title-id/"

        private val TITLE_ID_REGEX = Regex("[0-9a-fA-F]{24}$")
        private val TITLE_URL_REGEX = Regex("""https?://[^"'\\\s]+/title-detail/[^"'\\\s]+""")
        private val CHAPTER_IMAGES_REGEX = Regex("""const\s+chapterImages\s*=\s*JSON\.parse\(`([\s\S]*?)`\)""")
    }
}
