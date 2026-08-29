package eu.kanade.tachiyomi.extension.pt.sssscanlator

import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

@Source
abstract class YomuComics :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val loginMutex = Mutex()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3) { it.host == baseUrl.toHttpUrl().host }

    // Every route answers "Invalid browser fingerprint" without the Sec-Fetch headers.
    override fun Headers.Builder.configureHeaders(): Headers.Builder = this
        .set("Accept", "application/json, text/plain, */*")
        .set("Sec-Fetch-Dest", "empty")
        .set("Sec-Fetch-Mode", "cors")
        .set("Sec-Fetch-Site", "same-origin")

    private val rscHeaders by lazy { headersBuilder().set("RSC", "1").build() }

    // ============================== Listing ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page, sort = "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(page, sort = "recent")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getMangaList(page, query, filters)

    private suspend fun getMangaList(
        page: Int,
        query: String = "",
        filters: FilterList = FilterList(),
        sort: String? = null,
    ): MangasPage {
        val url = "$baseUrl/api/library".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .apply {
                sort?.let { addQueryParameter("sort", it) }
                if (query.isNotBlank()) {
                    addQueryParameter("search", query)
                }
                filters.filterIsInstance<UrlFilter>()
                    .filterNot { sort != null && it is SortFilter }
                    .forEach { it.addToUrl(this) }
            }
            .build()

        val result = authGet(url).parseAs<LibraryDto>()

        return MangasPage(result.entries, result.hasNextPage)
    }

    // ============================== Details ===============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.firstOrNull() !in MANGA_PATH_SEGMENTS) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        val manga = SManga.create().apply { this.url = "/obra/$slug" }

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
        val slug = manga.url.substringAfterLast('/')
        val payload = authGet("$baseUrl/obra/$slug".toHttpUrl(), rscHeaders).use { it.body.string() }

        val details = payload.extractNextJsRsc<DetailsDto>()
            ?: throw IOException("Não achei os dados da obra")

        return SMangaUpdate(
            manga = details.toSManga(payload.extractNextJsRsc<HeaderDto>(), payload.genres()),
            chapters = details.chapterList,
        )
    }

    /** Genre badges carry a cuid key, the type and status badges beside them do not. */
    private fun String.genres(): List<String> = GENRE_BADGE_REGEX.findAll(this)
        .map { it.groupValues[1] }
        .distinct()
        .toList()

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        ensureLogin()

        val url = "$baseUrl/api/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("id", chapter.url)
            .build()

        val response = client.get(url, ensureSuccess = false)
        if (response.code == 403) {
            throw IOException(response.parseAs<LockedDto>().message)
        }

        return response.ensureSuccessful().parseAs<PagesDto>().toPageList()
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/api/genres")
        .parseAs<List<String>>()
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<String>>()
            ?: return FilterList(SortFilter(), TypeFilter(), StatusFilter())

        return FilterList(SortFilter(), TypeFilter(), StatusFilter(), GenreFilter(genres))
    }

    // ================================ Auth ================================

    /**
     * The site is subscriber-only: every catalog and series route redirects to the landing page
     * without a session, so the credentials are required before any of them is requested.
     */
    private suspend fun authGet(url: HttpUrl, requestHeaders: Headers = headers): Response {
        ensureLogin()

        val response = client.get(url, requestHeaders, ensureSuccess = false)
        if (!response.isLoggedOut(url)) return response.ensureSuccessful()

        response.close()
        login()

        val retry = client.get(url, requestHeaders, ensureSuccess = false)
        if (retry.isLoggedOut(url)) {
            retry.close()
            throw IOException(SESSION_EXPIRED_MESSAGE)
        }

        return retry.ensureSuccessful()
    }

    // A logged out request is redirected to the landing page instead of being rejected.
    private fun Response.isLoggedOut(url: HttpUrl): Boolean = code == 401 || code == 403 || request.url.encodedPath != url.encodedPath

    private fun Response.ensureSuccessful(): Response {
        if (isSuccessful) return this

        val status = code
        close()
        throw IOException("Yomu respondeu $status")
    }

    private suspend fun ensureLogin() {
        if (hasSession()) return
        login()
    }

    private suspend fun login() = loginMutex.withLock {
        if (hasSession()) return@withLock

        val identifier = preferences.getString(PREF_IDENTIFIER, "").orEmpty().trim()
        val password = preferences.getString(PREF_PASSWORD, "").orEmpty()
        if (identifier.isEmpty() || password.isEmpty()) throw IOException(LOGIN_REQUIRED_MESSAGE)

        val csrfToken = client
            .get("$baseUrl/api/auth/csrf", headers, cacheControl = CacheControl.FORCE_NETWORK)
            .parseAs<CsrfDto>()
            .csrfToken

        val body = FormBody.Builder()
            .add("identifier", identifier)
            .add("password", password)
            .add("csrfToken", csrfToken)
            .add("callbackUrl", baseUrl)
            .add("json", "true")
            .build()

        val requestHeaders = headersBuilder().set("X-Auth-Return-Redirect", "1").build()
        client.post("$baseUrl/api/auth/callback/credentials", requestHeaders, body, ensureSuccess = false).close()

        if (!hasSession()) throw IOException(LOGIN_FAILED_MESSAGE)
    }

    private fun hasSession(): Boolean = client.cookieJar
        .loadForRequest(baseUrl.toHttpUrl())
        .any { it.name == SESSION_COOKIE && it.value.isNotEmpty() }

    // ============================ Preferences =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val warning = "⚠️ Os dados inseridos nesta seção serão usados somente para realizar o login na fonte"
        val message = "Insira %s para prosseguir com o acesso aos recursos disponíveis na fonte"

        EditTextPreference(screen.context).apply {
            key = PREF_IDENTIFIER
            title = "📧 Email ou usuário"
            summary = "Email ou nome de usuário de acesso"
            dialogMessage = buildString {
                appendLine(message.format("seu email ou nome de usuário"))
                append("\n$warning")
            }
            setDefaultValue("")
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_PASSWORD
            title = "🔑 Senha"
            summary = "Senha de acesso"
            dialogMessage = buildString {
                appendLine(message.format("sua senha"))
                append("\n$warning")
            }
            setDefaultValue("")
        }.let(screen::addPreference)
    }

    // =============================== Utils ================================

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = chapter.memo["slug"]?.stringOrNull
        val number = chapter.memo["number"]?.stringOrNull
        if (slug == null || number == null) return baseUrl

        return "$baseUrl/ler/$slug/$number"
    }

    companion object {
        private const val PAGE_SIZE = 30
        private const val PREF_IDENTIFIER = "yomu_identifier"
        private const val PREF_PASSWORD = "yomu_password"
        private const val SESSION_COOKIE = "__Secure-authjs.session-token"
        private const val LOGIN_REQUIRED_MESSAGE =
            "A Yomu agora exige conta. Faça login no WebView ou informe email e senha nas configurações da extensão."
        private const val LOGIN_FAILED_MESSAGE =
            "Login recusado pela Yomu. Revise email e senha nas configurações da extensão."
        private const val SESSION_EXPIRED_MESSAGE =
            "Sessão expirada. Faça login no WebView ou revise email e senha nas configurações da extensão."
        private val MANGA_PATH_SEGMENTS = listOf("obra", "ler")
        private val GENRE_BADGE_REGEX =
            Regex(""""span","[a-z0-9]+",\{"data-slot":"badge"[^}]*"children":"([^"]+)"""")
    }
}
