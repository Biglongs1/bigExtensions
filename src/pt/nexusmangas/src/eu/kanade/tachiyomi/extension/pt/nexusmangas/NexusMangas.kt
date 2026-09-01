package eu.kanade.tachiyomi.extension.pt.nexusmangas

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
abstract class NexusMangas : KeiSource() {

    private val apiUrl = "https://supabase.nexusmangas.com/rest/v1"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3) { it.host == apiUrl.toHttpUrl().host }

    // The anon key the site ships in its own bundle; PostgREST rejects the request without it.
    override fun Headers.Builder.configureHeaders(): Headers.Builder = this
        .add("Accept", "application/json")
        .add("apikey", ANON_KEY)
        .add("Authorization", "Bearer $ANON_KEY")

    override suspend fun getPopularManga(page: Int): MangasPage = query(page, order = "avg_rating.desc")

    override suspend fun getLatestUpdates(page: Int): MangasPage = query(page, order = "updated_at.desc")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = query(
        page = page,
        order = filters.firstInstanceOrNull<SortFilter>()?.selectedValue ?: "updated_at.desc",
        search = query,
        type = filters.firstInstanceOrNull<TypeFilter>()?.selectedValue,
        status = filters.firstInstanceOrNull<StatusFilter>()?.selectedValue,
        demographic = filters.firstInstanceOrNull<DemographicFilter>()?.selectedValue,
        genre = filters.firstInstanceOrNull<GenreFilter>()?.selectedValue,
    )

    private suspend fun query(
        page: Int,
        order: String,
        search: String? = null,
        type: String? = null,
        status: String? = null,
        demographic: String? = null,
        genre: String? = null,
    ): MangasPage {
        // An inner join is only correct when actually filtering by genre, otherwise it would
        // silently drop every work that has no genre attached.
        val genreJoin = if (genre.isNullOrBlank()) "" else ",work_genres!inner(genres!inner(name))"

        val url = "$apiUrl/works".toHttpUrl().newBuilder()
            .addQueryParameter("select", "$LIST_COLUMNS$genreJoin")
            .addQueryParameter("order", order)
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
            .apply {
                search?.sanitized()?.let {
                    addQueryParameter("or", "(title.ilike.*$it*,alternative_title.ilike.*$it*)")
                }
                type?.takeIf(String::isNotBlank)?.let { addQueryParameter("type", "eq.$it") }
                status?.takeIf(String::isNotBlank)?.let { addQueryParameter("status", "eq.$it") }
                demographic?.takeIf(String::isNotBlank)?.let { addQueryParameter("demographic", "eq.$it") }
                genre?.takeIf(String::isNotBlank)?.let {
                    addQueryParameter("work_genres.genres.name", "eq.$it")
                }
            }
            .build()

        val works = client.get(url).parseAs<List<WorkDto>>()

        return MangasPage(works.map(WorkDto::toSManga), works.size == PAGE_SIZE)
    }

    // PostgREST reads commas and parentheses as filter syntax.
    private fun String.sanitized(): String? = replace(FILTER_SYNTAX_REGEX, " ").trim().takeIf(String::isNotBlank)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.firstOrNull() != "obra") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotBlank) ?: return null
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
        val url = "$apiUrl/works".toHttpUrl().newBuilder()
            .addQueryParameter("select", DETAIL_COLUMNS)
            .addQueryParameter("slug", "eq.$slug")
            .addQueryParameter("limit", "1")
            .build()

        val work = client.get(url).parseAs<List<WorkDto>>().firstOrNull()
            ?: throw IOException("Obra não encontrada")

        return SMangaUpdate(manga = work.toSManga(), chapters = work.chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = chapter.url.split('/')
        val slug = segments.getOrNull(2) ?: throw IOException("Capítulo inválido")
        val number = segments.getOrNull(3) ?: throw IOException("Capítulo inválido")

        val url = "$apiUrl/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("select", "pages,works!inner(slug)")
            .addQueryParameter("works.slug", "eq.$slug")
            .addQueryParameter("number", "eq.$number")
            .addQueryParameter("limit", "1")
            .build()

        return client.get(url).parseAs<List<ChapterPagesDto>>().firstOrNull()?.toPageList()
            ?: throw IOException("Não achei as páginas do capítulo")
    }

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = "$apiUrl/genres".toHttpUrl().newBuilder()
            .addQueryParameter("select", "name")
            .addQueryParameter("order", "name.asc")
            .addQueryParameter("limit", "200")
            .build()

        val genres = client.get(url).parseAs<List<GenreDto>>().map(GenreDto::name)

        return FilterData(genres).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<FilterData>()?.genres

        return FilterList(
            listOfNotNull(
                SortFilter(),
                TypeFilter(),
                StatusFilter(),
                DemographicFilter(),
                genres?.takeIf(List<String>::isNotEmpty)?.let(::GenreFilter),
            ),
        )
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    companion object {
        private const val PAGE_SIZE = 30
        private const val LIST_COLUMNS = "id,slug,title,cover_url,status,type"
        private const val DETAIL_COLUMNS =
            "id,slug,title,description,cover_url,alternative_title,status,type,author,artist," +
                "demographic,scan:scans(name),work_genres(genres(name))," +
                "chapters(number,title,published_at)"
        private const val ANON_KEY =
            "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9." +
                "eyJpc3MiOiJzdXBhYmFzZSIsImlhdCI6MTc4NzgwMjAwMCwiZXhwIjo0OTQzNDc1NjAwLCJyb2xlIjoiYW5vbiJ9." +
                "Cnl8Jw2DeKe84OAkmJYfO33xlcZsw0TC2Nw_il0tpRs"
        private val FILTER_SYNTAX_REGEX = Regex("""[,()*]""")
    }
}
