package eu.kanade.tachiyomi.extension.pt.inkscan

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getLocalStorage
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

@Source
abstract class InkScan : KeiSource() {
    private val apiUrl = "https://api.inkscann.live"
    private val sessionMutex = Mutex()
    private var session: SessionDto? = null

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2) { it.host == apiUrl.toHttpUrl().host }

    override fun getHomeUrl(): String = "$baseUrl/auth"

    override suspend fun getPopularManga(page: Int): MangasPage = getList(page, "total_views.desc,id.asc")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getList(page, "updated_at.desc,id.asc")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getList(page, "total_views.desc,id.asc", query, filters)

    private suspend fun getList(page: Int, order: String, query: String = "", filters: FilterList = FilterList()): MangasPage {
        val url = "$apiUrl/rest/v1/obras".toHttpUrl().newBuilder()
            .addQueryParameter("select", "id,titulo,capa_url")
            .addQueryParameter("order", order)
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
            .apply {
                if (query.isNotBlank()) addQueryParameter("titulo", "ilike.*$query*")
                filters.forEach { if (it is UrlFilter) it.addToUrl(this) }
                if (filters.firstInstanceOrNull<ArchiveFilter>() == null) ArchiveFilter().addToUrl(this)
            }
            .build()
        val works = authorized { client.get(url, it, ensureSuccess = false) }.parseAs<List<MangaDto>>()
        return MangasPage(works.map { it.toSManga() }, works.size == PAGE_SIZE)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.firstOrNull() != "manga") return null
        val id = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        return getWork(id).toSManga()
    }

    private suspend fun getWork(id: String): MangaDto {
        val url = "$apiUrl/rest/v1/obras".toHttpUrl().newBuilder()
            .addQueryParameter("select", "id,titulo,capa_url,descricao,status,generos,tags,autor,artista,titulos_alternativos,pasta_s3,is_acervo_b")
            .addQueryParameter("id", "eq.$id")
            .build()
        return authorized { client.get(url, it, ensureSuccess = false) }.parseAs<List<MangaDto>>().first()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val id = (baseUrl + manga.url).toHttpUrl().pathSegments[1]
        val details = async { if (fetchDetails) getWork(id).toSManga() else manga }
        val chapterList = async { if (fetchChapters) getChapters(id) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun getChapters(mangaId: String): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var offset = 0
        do {
            val url = "$apiUrl/rest/v1/capitulos".toHttpUrl().newBuilder()
                .addQueryParameter("select", "id,numero,titulo,created_at")
                .addQueryParameter("obra_id", "eq.$mangaId")
                .addQueryParameter("order", "numero.desc")
                .addQueryParameter("limit", "1000")
                .addQueryParameter("offset", offset.toString())
                .build()
            val batch = authorized { client.get(url, it, ensureSuccess = false) }.parseAs<List<ChapterDto>>()
            chapters += batch.map { it.toSChapter(mangaId) }
            offset += batch.size
        } while (batch.size == 1000)
        return chapters
    }

    override fun getChapterUrl(chapter: SChapter): String = (baseUrl + chapter.url).toHttpUrl().newBuilder().fragment(null).build().toString()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = (baseUrl + chapter.url).toHttpUrl()
        val chapterId = url.fragment ?: throw IOException("Atualize a lista de capítulos da obra.")
        val work = getWork(url.pathSegments[1])
        val body = ChapterRequestDto(chapterId).toJsonRequestBody()
        val result = authorized { client.post("$apiUrl/functions/v1/get-chapter", it, body, ensureSuccess = false) }
            .parseAs<ChapterPagesDto>()
        return result.toPages(work, url.pathSegments[3])
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val genres = mutableSetOf<String>()
        var offset = 0
        do {
            val url = "$apiUrl/rest/v1/obras".toHttpUrl().newBuilder()
                .addQueryParameter("select", "tags,generos")
                .addQueryParameter("order", "id.asc")
                .addQueryParameter("limit", "1000")
                .addQueryParameter("offset", offset.toString())
                .build()
            val batch = authorized { client.get(url, it, ensureSuccess = false) }.parseAs<List<GenresDto>>()
            batch.forEach { genres += it.tags.orEmpty() + it.generos.orEmpty() }
            offset += batch.size
        } while (batch.size == 1000)
        return genres.map(String::trim).filter(String::isNotEmpty).distinct().sorted().toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        buildList {
            add(SortFilter())
            add(ArchiveFilter())
            add(StatusFilter())
            add(FormatsFilter())
            add(ChapterCountFilter("Mínimo de capítulos", "gte"))
            add(ChapterCountFilter("Máximo de capítulos", "lte"))
            add(TextFilter("Autor", "autor"))
            add(TextFilter("Artista", "artista"))
            data?.parseAs<List<String>>()?.let { add(GenresFilter(it)) }
        },
    )

    private suspend fun authorized(request: suspend (Headers) -> Response): Response {
        var response = request(authHeaders())
        if (response.code == 401) {
            response.close()
            response = request(authHeaders(forceRefresh = true))
        }
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw IOException("A Ink recusou o acesso ($code). Entre novamente pela WebView e confira o acesso ao capítulo.")
        }
        return response
    }

    private suspend fun authHeaders(forceRefresh: Boolean = false): Headers = sessionMutex.withLock {
        var current = session
        if (current == null || !current.isValid() || forceRefresh) {
            current = getLocalStorage(baseUrl, STORAGE_KEY)?.parseAs<SessionDto>()
                ?: throw IOException(LOGIN_MESSAGE)
            if (!current.isValid() || forceRefresh) current = refreshSession(current)
            session = current
        }
        headers.newBuilder()
            .add("apikey", ANON_KEY)
            .add("Authorization", "Bearer ${current.accessToken}")
            .build()
    }

    private suspend fun refreshSession(current: SessionDto): SessionDto {
        val authHeaders = headers.newBuilder().add("apikey", ANON_KEY).build()
        val body = RefreshRequestDto(current.refreshToken).toJsonRequestBody()
        val response = client.post("$apiUrl/auth/v1/token?grant_type=refresh_token", authHeaders, body, ensureSuccess = false)
        if (!response.isSuccessful) {
            response.close()
            throw IOException(LOGIN_MESSAGE)
        }
        // Preserve the whole Supabase session, including its user object, for the site's own client.
        val payload = response.parseAs<JsonElement>().toString().toJsonString()
        return runWebView {
            onPageFinished {
                evaluateJs(
                    """
                    (() => {
                        const session = JSON.parse($payload);
                        session.expires_at = session.expires_at || Math.floor(Date.now() / 1000) + session.expires_in;
                        const value = JSON.stringify(session);
                        localStorage.setItem(${STORAGE_KEY.toJsonString()}, value);
                        return value;
                    })()
                    """.trimIndent(),
                ) { resolve(it.parseAs<String>().parseAs<SessionDto>()) }
            }
            loadData(baseUrl, "")
        }
    }

    companion object {
        private const val PAGE_SIZE = 24
        private const val STORAGE_KEY = "sb-api-auth-token"
        private const val LOGIN_MESSAGE = "Abra a Ink na WebView, faça login e conclua o CAPTCHA. Depois volte e atualize a fonte."

        // Public anonymous key included in the website's Supabase client.
        private const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNqeWJmdnlvem5tdHhtamh5Y29qIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njk1NTI3MTIsImV4cCI6MjA4NTEyODcxMn0.0nWTir-WVr83QrPoIj8GbSt2Tuu3QZONA_TMzyZ8Ljc"
    }
}
