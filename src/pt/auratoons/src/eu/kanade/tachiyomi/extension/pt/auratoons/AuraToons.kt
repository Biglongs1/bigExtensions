package eu.kanade.tachiyomi.extension.pt.auratoons

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
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

@Source
abstract class AuraToons : KeiSource() {

    @Volatile
    private var catalogActionId: String? = null
    private val catalogActionMutex = Mutex()

    override suspend fun getPopularManga(page: Int): MangasPage = fetchCatalog(
        page = page,
        sortBy = "views",
        sortDir = "desc",
    ).toMangasPage(page, baseUrl)

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchCatalog(
        page = page,
        sortBy = "updatedAt",
        sortDir = "desc",
    ).toMangasPage(page, baseUrl)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val options = filters.toSearchOptions()
        return fetchCatalog(
            page = page,
            query = query,
            filters = options.filters,
            sortBy = options.sortBy,
            sortDir = options.sortDir,
        ).toMangasPage(page, baseUrl)
    }

    private suspend fun fetchCatalog(
        page: Int,
        query: String = "",
        filters: CatalogFiltersDto = CatalogFiltersDto(),
        sortBy: String,
        sortDir: String,
    ): CatalogResponseDto {
        val body = listOf(
            CatalogRequestDto(
                filters = filters,
                toolbarQuery = query,
                sortBy = sortBy,
                sortDir = sortDir,
                page = page,
            ),
        ).toJsonString().toRequestBody(ACTION_MEDIA_TYPE)

        suspend fun execute(forceRefresh: Boolean): CatalogResponseDto? {
            val actionId = getCatalogActionId(forceRefresh)
            return client.post(
                "$baseUrl/biblioteca",
                catalogHeaders(actionId),
                body,
                ensureSuccess = false,
            ).use { response ->
                if (response.isSuccessful) response.extractNextJs<CatalogResponseDto>() else null
            }
        }

        return execute(forceRefresh = false)
            ?: execute(forceRefresh = true)
            ?: throw IOException("Não foi possível carregar o catálogo da Aura Toons")
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val detail = client.get(getMangaUrl(manga))
            .extractNextJs<MangaDetailDto>()
            ?: throw IOException("Detalhes da obra não encontrados")

        return SMangaUpdate(
            manga = detail.manga.toSManga(baseUrl),
            chapters = detail.toSChapterList(),
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringAfterLast('/')
        val url = "$baseUrl/api/nxtoons/chapter-pages".toHttpUrl().newBuilder()
            .addQueryParameter("chapterId", chapterId)
            .build()

        return client.get(url, ensureSuccess = false).use { response ->
            if (!response.isSuccessful) return@use emptyList()
            response.parseAs<ChapterPagesDto>().toPageList(baseUrl)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        val manga = SManga.create().apply { this.url = slug }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val separator = chapter.url.lastIndexOf('/')
        require(separator > 0) { "URL de capítulo inválida" }
        return "$baseUrl/manga/${chapter.url.substring(0, separator)}/capitulo/${chapter.url.substring(separator + 1)}"
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val optionGroups = getCatalogBundle().extractOptionGroups()
            .sortedByDescending(List<FilterOptionDto>::size)
        return FilterDataDto(
            genres = optionGroups.getOrElse(0) { emptyList() },
            themes = optionGroups.getOrElse(1) { emptyList() },
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = AuraFilters(
        data?.parseAs<FilterDataDto>(),
    ).getFilterList()

    private suspend fun getCatalogActionId(forceRefresh: Boolean): String {
        if (!forceRefresh) catalogActionId?.let { return it }

        return catalogActionMutex.withLock {
            if (!forceRefresh) catalogActionId?.let { return@withLock it }
            getCatalogBundle(forceRefresh)
                .let(CATALOG_ACTION_REGEX::find)
                ?.groupValues
                ?.get(1)
                ?.also { catalogActionId = it }
                ?: throw IOException("Ação do catálogo não encontrada")
        }
    }

    private suspend fun getCatalogBundle(forceRefresh: Boolean = false): String {
        val cacheControl = if (forceRefresh) CacheControl.FORCE_NETWORK else CacheControl.Builder().build()
        val document = client.get("$baseUrl/biblioteca", cacheControl = cacheControl).asJsoup()
        val scriptUrl = document.selectFirst("script[src*=/app/biblioteca/page-]")
            ?.absUrl("src")
            ?.takeIf(String::isNotEmpty)
            ?: throw IOException("Bundle da biblioteca não encontrado")
        return client.get(scriptUrl, cacheControl = cacheControl).use { it.body.string() }
    }

    private fun catalogHeaders(actionId: String): Headers = headersBuilder()
        .set("Accept", "text/x-component")
        .set("Content-Type", ACTION_MEDIA_TYPE.toString())
        .set("Referer", "$baseUrl/biblioteca")
        .set("Next-Action", actionId)
        .set("Next-Router-State-Tree", NEXT_ROUTER_STATE)
        .build()

    private fun String.extractOptionGroups(): List<List<FilterOptionDto>> = OPTION_ARRAY_REGEX
        .findAll(this)
        .map { array ->
            OPTION_REGEX.findAll(array.value).map { option ->
                FilterOptionDto(
                    label = option.groupValues[1].decodeJsString(),
                    value = option.groupValues[2].decodeJsString(),
                )
            }.toList()
        }
        .filter { it.size > 10 }
        .toList()

    private fun String.decodeJsString(): String {
        val normalized = replace(JS_HEX_REGEX) { "\\u00${it.groupValues[1]}" }
        return "\"$normalized\"".parseAs()
    }

    companion object {
        private val ACTION_MEDIA_TYPE = "text/plain;charset=UTF-8".toMediaType()
        private const val NEXT_ROUTER_STATE = "%5B%22%22%2C%7B%22children%22%3A%5B%22biblioteca%22%2C%7B%22children%22%3A%5B%22__PAGE__%22%2C%7B%7D%2Cnull%2Cnull%5D%7D%2Cnull%2Cnull%5D%7D%2Cnull%2Cnull%2Ctrue%5D"
        private val CATALOG_ACTION_REGEX = Regex(
            """createServerReference\)\("([a-f0-9]{40,})"[^;]{0,240}"fetchBibliotecaCatalogAction""",
        )
        private val OPTION_ARRAY_REGEX = Regex(
            """\[(?:\{label:"(?:\\.|[^"\\])*",value:"(?:\\.|[^"\\])*"},?)+]""",
        )
        private val OPTION_REGEX = Regex("""\{label:"((?:\\.|[^"\\])*)",value:"((?:\\.|[^"\\])*)"}""")
        private val JS_HEX_REGEX = Regex("""\\x([0-9a-fA-F]{2})""")
    }
}
